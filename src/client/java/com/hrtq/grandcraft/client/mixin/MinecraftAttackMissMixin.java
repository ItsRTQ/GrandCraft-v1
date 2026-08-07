package com.hrtq.grandcraft.client.mixin;

import com.hrtq.grandcraft.client.ClientAttackCommit;
import com.hrtq.grandcraft.network.AttackMissPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Tells the server when a swing hit nothing, so a miss costs endlag like a hit does.
 *
 * <p>Reported at the end of {@code startAttack} rather than from the swing itself,
 * because that is the one place the three outcomes are still distinguishable:
 * {@code ENTITY} is already handled by the attack callback, {@code BLOCK} is mining
 * and must not be punished, and {@code MISS} is the whiff. Vanilla's swing packet
 * cannot tell them apart.
 *
 * <h2>Why TAIL, and not RETURN</h2>
 * Five guards higher up leave without throwing a swing — {@code missTime} still
 * running, a null hit result, hands busy, a disabled item, and
 * {@code cannotAttackWithItem}. Any of them can be reached while {@code hitResult}
 * is {@code MISS}, so the type alone does not prove a swing happened.
 *
 * <p>The return value looks like it would settle that, but it does not:
 * {@code startAttack} returns true only when a block was destroyed outright, or
 * from the spectator and piercing-weapon paths. <strong>A miss returns
 * false</strong> — it falls through the hit-result switch to the common tail,
 * swings, and returns the still-false local. Guarding on the return value
 * therefore discards precisely the case this class exists to report.
 *
 * <p>Each of those five guards leaves through its own {@code RETURN} opcode, and so
 * do the spectator and piercing paths. {@code TAIL} injects before the <em>last</em>
 * one, which is the common tail reached only after {@code LocalPlayer.swing} has
 * run. The swing is therefore structurally guaranteed, and ENTITY and BLOCK are
 * ruled out by the type check that remains.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftAttackMissMixin {
	@Shadow
	public HitResult hitResult;

	@Inject(method = "startAttack", at = @At("TAIL"))
	private void grandcraft$reportMiss(CallbackInfoReturnable<Boolean> info) {
		if (this.hitResult == null || this.hitResult.getType() != HitResult.Type.MISS) {
			return;
		}

		ClientPlayNetworking.send(new AttackMissPayload());

		// A whiff books a phase exactly like a hit does, so it commits the client the same
		// way — otherwise spamming at open air stays a phantom factory while spamming at a
		// mob is fixed. See ClientAttackCommit.
		ClientAttackCommit.commit(Util.getMillis());
	}
}

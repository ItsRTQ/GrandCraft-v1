package com.hrtq.grandcraft.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Stops the client swinging the arm for an attack the server has not agreed to yet.
 *
 * <p><strong>This is the file to delete if attacks stop animating entirely.</strong> It is
 * the last piece of vanilla's optimistic attack prediction, and it is removed for the same
 * reason {@code MultiPlayerGameModeAttackMixin} removed the damage half: this mod's combat
 * is server-authoritative, so the client has no business drawing a swing it might not get.
 *
 * <h2>The ghost this kills</h2>
 *
 * <p>{@code Minecraft.startAttack} swings the arm on the way out, unconditionally, before
 * the server has said anything. When the server then <em>refuses</em> — an empty stamina
 * pool, a raised guard — it sends nothing back at all, so the client never learns the
 * swing did not happen and the arm has already moved. That is a phantom attack, and out of
 * stamina it is one per click (user, 2026-08-07).
 *
 * <p>The phase packet is the only honest source of a swing animation: it arrives when the
 * server has actually booked the wind-up, it drives {@code AttackAnimation}'s authored
 * clips for a weapon and {@code HumanoidCombatPose} for bare hands, and it is sent to every
 * viewer rather than just this client. Nothing is lost by waiting a round trip for it —
 * what is lost is a swing that never existed.
 *
 * <h2>Mining keeps its swing, and that is the whole condition</h2>
 *
 * <p>The redirected call site is the common tail of {@code startAttack}, reached for all
 * three outcomes: an entity, a whiff, and a <strong>block</strong>. Only the first two are
 * attacks. Breaking a block is not server-gated by anything in this mod, its arm swing is
 * the feedback that mining is happening, and {@code Minecraft.continueAttack} would swing
 * on the very next tick anyway — suppressing it here would make the first tick of every
 * mining action look dropped. So a {@code BLOCK} hit result keeps vanilla's swing and
 * everything else loses it.
 *
 * <p>The piercing-weapon path swings from its own call site earlier in the method and is
 * redirected by the same annotation. That is an attack too, so it takes the same rule.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftAttackSwingMixin {
	@Shadow
	public HitResult hitResult;

	@Redirect(
			method = "startAttack",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/player/LocalPlayer;"
							+ "swing(Lnet/minecraft/world/InteractionHand;)V"))
	private void grandcraft$noSwingUntilTheServerAgrees(LocalPlayer player, InteractionHand hand) {
		if (this.hitResult != null && this.hitResult.getType() == HitResult.Type.BLOCK) {
			player.swing(hand);
		}

		// Otherwise nothing: the phase packet draws the swing, if there is one.
	}
}

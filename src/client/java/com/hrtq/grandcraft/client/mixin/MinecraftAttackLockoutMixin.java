package com.hrtq.grandcraft.client.mixin;

import com.hrtq.grandcraft.client.ClientCombatPhases;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Swallows an attack input thrown while the player is already mid-swing, so a click
 * during a wind-up or an endlag does <strong>nothing at all</strong>.
 *
 * <p>The server has always refused those swings — {@code canStartAttack} does it, and it
 * is the thing that actually enforces the rule. What it could not do is stop the client
 * from playing vanilla's arm swing on the way to being refused, which read as the player
 * attacking twice and only being credited once. A phantom.
 *
 * <p>Cancelling at HEAD takes the whole input: no swing animation, no
 * {@code ServerboundAttackPacket}, no {@code AttackMissPayload}, and no {@code missTime}.
 * The click is not delayed or queued — it is dropped, which is what makes a committed
 * swing a commitment rather than a suggestion.
 *
 * <h2>Read from the phase, not from a new packet</h2>
 *
 * {@code ClientCombatPhases} already knows: {@code CombatController.syncPhase} sends the
 * phase to every viewer <em>and to the actor itself</em>, precisely so a player can see
 * their own commitment. So the lockout is the phase, exactly, with no second clock to
 * drift against it — unlike {@code ClientAttackLockout}, which carries only the current
 * phase's remaining ticks and would expire three times during one swing.
 *
 * <p>The window between the click and the phase packet arriving is one round trip, in
 * which a second click can still slip through. It is refused by the server as it always
 * was, and it is far shorter than a human double-click — the case this exists for is the
 * player clicking repeatedly through a 550ms sword swing, not two clicks in one tick.
 *
 * <h2>Every click, including one aimed at a block</h2>
 *
 * The first version let a click through when the crosshair was on a <strong>block</strong>,
 * reasoning that mining is not attacking and an endlag has no business stopping someone
 * breaking stone. <strong>That carve-out was the whole lockout's undoing and was removed
 * the same day.</strong> Blocks are what most of the world is made of — the ground, a
 * wall, anything the player happens to be facing — so in ordinary play the crosshair is on
 * one far more often than not, and the exception was swallowing the rule. The lockout only
 * ever applied when the player happened to be aiming at a mob or at open sky, which read
 * as no lockout at all.
 *
 * <p>What it costs is small and bounded: a player cannot <em>begin</em> breaking a block
 * during the few ticks of an endlag. Mining already in progress is unaffected, because
 * holding the button runs {@code Minecraft.continueAttack} and never comes back through
 * here.</p>
 *
 * <p><strong>A guard is not covered and must not be</strong>, even though the server also
 * refuses a swing from one. Dropping a guard and attacking in one motion is a deliberate
 * mechanic, and {@code MinecraftAttackGuardMixin} exists to make that exact input work —
 * swallowing it here would undo it.</p>
 *
 * <h2>Third of three at this HEAD</h2>
 *
 * {@code MinecraftAttackGuardMixin} and {@code MinecraftRadialAttackMixin} also inject at
 * the head of {@code startAttack}. Mixin checks for cancellation after <em>each</em>
 * handler, so whichever cancels first stops the rest — and the order is the order the
 * mixins are listed in {@code grandcraft.client.mixins.json}, where the guard comes first.
 *
 * <p>That order is the safe one and worth keeping: the guard's handler sends a release and
 * does not cancel, so it should run before anything that might swallow the click. It is
 * harmless if it ever does not — a player mid-swing cannot be guarding, the two states are
 * exclusive — but the two orderings are not equally obvious and the config decides it.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftAttackLockoutMixin {
	@Shadow
	public LocalPlayer player;

	@Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
	private void grandcraft$dropAttackWhileSwinging(CallbackInfoReturnable<Boolean> info) {
		if (this.player == null) {
			return;
		}

		if (!ClientCombatPhases.stateOf(this.player.getId(), Util.getMillis()).isAttack()) {
			return;
		}

		// False is "no block was destroyed", which is the truth: nothing happened.
		info.setReturnValue(false);
	}
}

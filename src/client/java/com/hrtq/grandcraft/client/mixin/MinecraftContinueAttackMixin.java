package com.hrtq.grandcraft.client.mixin;

import com.hrtq.grandcraft.client.ClientAttackCommit;
import com.hrtq.grandcraft.client.ClientCombatPhases;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stops the <em>held</em> attack button while the player is mid-swing — the second half
 * of the lockout, and the half that was missing.
 *
 * <p>{@code MinecraftAttackLockoutMixin} swallows the click. It cannot swallow the hold:
 * {@code Minecraft.continueAttack} runs every tick the button is down and never goes
 * through {@code startAttack} at all. Its own javadoc said as much and treated it as
 * acceptable, which it was not — <strong>{@code continueAttack} swings the arm</strong>
 * (verified against the 26.2 jar: {@code continueDestroyBlock}, then
 * {@code addBreakingBlockEffect}, then {@code LocalPlayer.swing}). So a player holding or
 * spamming the button while facing a block — the ground, a wall, anything — kept flicking
 * the arm right through a wind-up they were locked out of, and kept breaking the block
 * while they did it.
 *
 * <p>Cancelling at HEAD takes both: no mining progress, no crack particles, no swing.
 * A commitment stays a commitment whether the button is clicked or leant on.
 *
 * <h2>What cancelling skips, and why that is fine</h2>
 *
 * <p>The branch this bypasses ends in {@code MultiPlayerGameMode.stopDestroyBlock}, so a
 * block already being mined is not actively abandoned — its progress simply stops
 * advancing for the few ticks of the swing, and the next tick out of the lockout either
 * resumes it or stops it exactly as vanilla would. <strong>No progress can be made during
 * an attack</strong>, which is the rule being enforced; forgetting the progress as well
 * would be a second, unasked-for change.
 *
 * <p>Deliberately <em>not</em> gated on where the crosshair is pointing. That carve-out
 * was tried on the click path and had to be reverted the same day — most of the world is
 * blocks, so an exception for them swallowed the rule.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftContinueAttackMixin {
	@Shadow
	public LocalPlayer player;

	@Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
	private void grandcraft$noMiningWhileSwinging(boolean leftClickDown, CallbackInfo info) {
		if (this.player == null) {
			return;
		}

		long now = Util.getMillis();

		if (!ClientCombatPhases.stateOf(this.player.getId(), now).isAttack()
				&& !ClientAttackCommit.pending(now)) {
			return;
		}

		info.cancel();
	}
}

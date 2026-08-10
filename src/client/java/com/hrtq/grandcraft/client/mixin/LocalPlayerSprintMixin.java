package com.hrtq.grandcraft.client.mixin;

import com.hrtq.grandcraft.client.ClientDowned;
import com.hrtq.grandcraft.client.ClientStamina;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Refuses to start a sprint while the pool is spent.
 *
 * <p>The server drains stamina for every tick spent sprinting and calls
 * {@code setSprinting(false)} when the pool empties, which reaches the client
 * through entity data. That alone is not enough: the client re-evaluates sprinting
 * every tick from held input, so it would immediately ask to sprint again and the
 * two sides would argue about it — visible as a stutter rather than a stop.
 *
 * <p>Only blocks <em>starting</em>. A sprint already running is ended by the server,
 * so the two hooks do not overlap.
 *
 * <p>Vanilla's own food gate still applies underneath this. Replacing it belongs
 * with the rest of the vanilla-removal work, not here.
 */
@Mixin(LocalPlayer.class)
public class LocalPlayerSprintMixin {
	@Inject(method = "canStartSprinting", at = @At("HEAD"), cancellable = true)
	private void grandcraft$sprintNeedsStamina(CallbackInfoReturnable<Boolean> info) {
		// Sprinting at 7% of walking pace would be a stutter rather than a run, and the
		// attribute has already taken the speed away — this stops the client asking for
		// it every tick and arguing with the server about the answer.
		if (ClientDowned.isDowned() || (ClientStamina.hasData() && ClientStamina.exhausted())) {
			info.setReturnValue(false);
		}
	}
}

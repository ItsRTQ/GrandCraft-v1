package com.hrtq.grandcraft.client.mixin;

import com.hrtq.grandcraft.client.hud.RadialMenu;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gives the quick-slot wheel the mouse while it is open, and takes it from the camera.
 *
 * <p>This is what makes the wheel a HUD overlay rather than a screen. A screen would
 * have handed over the cursor for free, but it also stops the player walking, jumping
 * and dodging — and a hotbar replacement in a combat mod has to be usable mid-fight.
 * So the wheel draws over a live game, and the only thing it takes away is looking.
 *
 * <p>{@code turnPlayer} is the narrowest place to take it: it is the single method
 * that turns accumulated mouse movement into a change of view, so cancelling it
 * suppresses aiming and nothing else. Everything the surrounding
 * {@code handleAccumulatedMovement} does — its frame-limiter nudge, its timestamp —
 * still happens.
 *
 * <p>The deltas do not need clearing here. {@code handleAccumulatedMovement} zeroes
 * them itself immediately <em>after</em> this call, whether or not it happened — so
 * cancelling cannot bank a frame's movement and swing the camera through all of it
 * once the wheel shuts, and each frame's movement reaches the wheel exactly once.
 * {@code handleAccumulatedMovement} is also the only caller, which is what makes that
 * safe to rely on. <strong>Verify it again after any Minecraft update.</strong>
 */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerRadialMixin {
	@Shadow
	private double accumulatedDX;

	@Shadow
	private double accumulatedDY;

	@Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
	private void grandcraft$aimRadialMenu(double deltaTime, CallbackInfo info) {
		if (!RadialMenu.isOpen()) {
			return;
		}

		RadialMenu.aim(this.accumulatedDX, this.accumulatedDY);

		info.cancel();
	}
}

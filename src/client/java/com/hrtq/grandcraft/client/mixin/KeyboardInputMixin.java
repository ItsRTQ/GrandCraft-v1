package com.hrtq.grandcraft.client.mixin;

import com.hrtq.grandcraft.client.ClientDowned;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Takes the crouch key away from a player who is already on the floor.
 *
 * <p>Reported in game 2026-08-09: a downed player could still toggle into a sneak.
 * Harmless in isolation and wrong the moment there is a prone pose to fight with — two
 * postures for one body, and vanilla's would win the parts ours does not overwrite.
 *
 * <p><strong>Suppressed at the input rather than at the pose.</strong> Crouching is not
 * one thing that can be vetoed: it changes the hitbox, the eye height, the step
 * behaviour, whether the player walks off ledges, and what the server is told. Clearing
 * the key press is the one edit that makes all of those agree, and it is the same
 * decision {@code ClientJumpMixin} makes for the jump — a client-predicted action can
 * only be refused on the client.
 *
 * <p>Targets {@code KeyboardInput} rather than {@code ClientInput}: the subclass
 * overrides {@code tick} and does not call super, so an injection on the parent would
 * never fire for a real player at the keyboard. Nothing else in the game produces a
 * {@code KeyboardInput}, so this is exactly the local player and no gamemode or
 * identity check is needed.
 *
 * <p>Sprint is left alone here even though it is suppressed too — that one has a proper
 * seam in {@code LocalPlayerSprintMixin}, which refuses the sprint rather than lying
 * about the key.
 */
@Mixin(KeyboardInput.class)
public class KeyboardInputMixin {
	@Inject(method = "tick", at = @At("TAIL"))
	private void grandcraft$noCrouchWhileDowned(CallbackInfo info) {
		if (!ClientDowned.isDowned()) {
			return;
		}

		ClientInput self = (ClientInput) (Object) this;
		Input keys = self.keyPresses;

		// Rebuilt rather than mutated: Input is a record, so shift cannot be cleared in
		// place. Skipped entirely when the key is already up, which is almost every tick
		// a player spends down.
		if (!keys.shift()) {
			return;
		}

		self.keyPresses = new Input(keys.forward(), keys.backward(), keys.left(),
				keys.right(), keys.jump(), false, keys.sprint());
	}
}

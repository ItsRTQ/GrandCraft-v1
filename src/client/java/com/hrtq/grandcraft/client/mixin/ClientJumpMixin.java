package com.hrtq.grandcraft.client.mixin;

import com.hrtq.grandcraft.client.ClientDowned;
import com.hrtq.grandcraft.client.ClientStamina;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stops the local player predicting a jump they cannot pay for, or one they have no
 * business making at all because they are lying on the floor.
 *
 * <p>This is the half of the jump cost that actually refuses. The server sees a
 * player's jump only after the client has already moved and reported it, so its own
 * hook can charge but not prevent — see {@code LivingEntityJumpMixin}. Without this
 * the client would jump, the server would decline to charge, and jumps would be free
 * whenever the pool was too low.
 *
 * <p>Targets {@code LivingEntity} rather than {@code LocalPlayer} because
 * {@code LocalPlayer} does not override {@code jumpFromGround}; there is nothing on
 * the subclass to inject into.
 *
 * <p><strong>The guards are load-bearing.</strong> In single player the integrated
 * server shares this class, so without the client-side and identity checks this
 * would also fire for the server's copy of every entity and charge twice.
 */
@Mixin(LivingEntity.class)
public class ClientJumpMixin {
	@Inject(method = "jumpFromGround", at = @At("HEAD"), cancellable = true)
	private void grandcraft$refuseUnaffordableJump(CallbackInfo info) {
		LivingEntity self = (LivingEntity) (Object) this;

		if (!self.level().isClientSide() || self != Minecraft.getInstance().player) {
			return;
		}

		// A downed player is prone, and prone things do not hop. There is nothing to
		// gate server-side — the jump is already over by the time it hears about it —
		// so this is the only place the refusal can happen at all.
		if (ClientDowned.isDowned()) {
			info.cancel();
			return;
		}

		if (ClientStamina.canAfford(ClientStamina.jumpCost(), Util.getMillis())) {
			return;
		}

		info.cancel();
	}
}

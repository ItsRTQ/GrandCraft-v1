package com.hrtq.grandcraft.mixin;

import com.hrtq.grandcraft.combat.CombatProfile;
import com.hrtq.grandcraft.combat.CombatProfiles;
import com.hrtq.grandcraft.combat.GrandCraftCombat;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes jumping cost stamina, so repositioning in neutral is a spend rather than a
 * free option.
 *
 * <h2>Why this cancels for mobs but not for players</h2>
 * The two reach this method by completely different routes:
 *
 * <ul>
 *   <li>A mob jumps from {@code LivingEntity.aiStep}, on the server, before anything
 *       has moved. Cancelling there is a real gate.</li>
 *   <li>A player's jump is client-predicted. The server only calls this from
 *       {@code ServerGamePacketListenerImpl.handleMovePlayer}, <em>after</em> the
 *       client has jumped and reported the movement — vanilla uses it to award the
 *       jump stat and food exhaustion, not to decide anything. Cancelling there
 *       would suppress the bookkeeping and leave the jump itself untouched.</li>
 * </ul>
 *
 * So this charges both and refuses only the first. Stopping a player from jumping is
 * the client gate's job, which is why {@code jumpCost} is on the stamina packet.
 *
 * <p>An unaffordable player jump therefore goes through unpaid rather than being
 * undone. That needs a client which declined its own gate — the same trade-off
 * already accepted for whiff endlag.
 */
@Mixin(LivingEntity.class)
public class LivingEntityJumpMixin {
	@Inject(method = "jumpFromGround", at = @At("HEAD"), cancellable = true)
	private void grandcraft$jumpCostsStamina(CallbackInfo info) {
		LivingEntity self = (LivingEntity) (Object) this;

		// Combat is server-authoritative. The client's own copy of this call is gated
		// separately, against the pool the server last reported.
		if (self.level().isClientSide()) {
			return;
		}

		CombatProfile profile = CombatProfiles.forEntity(self);

		if (profile == null || !profile.usesStamina()) {
			return;
		}

		if (GrandCraftCombat.controllerOf(self).spendJump(profile)) {
			return;
		}

		if (self instanceof Player) {
			return;
		}

		info.cancel();
	}
}

package com.hrtq.grandcraft.mixin;

import com.hrtq.grandcraft.combat.CombatController;
import com.hrtq.grandcraft.combat.PlayerAttack;
import com.hrtq.grandcraft.player.GrandCraftAttachments;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Drives the combat clock. Fabric has no per-entity tick event, so this is the
 * one place a controller can be advanced.
 *
 * <p>Kept deliberately narrow: it reads a nullable attachment and returns, so
 * entities that have never fought cost one null check per tick and never
 * allocate a controller.
 */
@Mixin(LivingEntity.class)
public class LivingEntityMixin {
	@Inject(method = "tick", at = @At("TAIL"))
	private void grandcraft$tickCombat(CallbackInfo info) {
		LivingEntity self = (LivingEntity) (Object) this;

		// Combat is server-authoritative.
		if (self.level().isClientSide()) {
			return;
		}

		CombatController controller = self.getAttached(GrandCraftAttachments.COMBAT_CONTROLLER);

		if (controller != null) {
			controller.tick(self);

			// After the tick, never inside it: the controller has to have already
			// entered ATTACK_ACTIVE this tick for there to be a frame to land on.
			// Returns immediately for anything that is not a player mid-swing.
			PlayerAttack.tick(self, controller);
		}
	}
}

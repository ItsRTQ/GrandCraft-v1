package com.hrtq.grandcraft.mixin;

import com.hrtq.grandcraft.combat.CombatController;
import com.hrtq.grandcraft.combat.Downed;
import com.hrtq.grandcraft.combat.PlayerAttack;
import com.hrtq.grandcraft.player.GrandCraftAttachments;
import com.hrtq.grandcraft.skill.Acrobat;
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

			// Also after, and for a related reason: the controller has already counted
			// this tick's ground contact and already let go of a grip that landed, so
			// what this reads is the current state rather than the previous one.
			// Returns immediately for anything that is not a player.
			Acrobat.tick(self, controller);

			// Last, and it has to be: this is the one of the three that can end with the
			// actor dead. Anything running after it would be reading a controller whose
			// entity is on its way out.
			Downed.tick(self, controller);
		}
	}
}

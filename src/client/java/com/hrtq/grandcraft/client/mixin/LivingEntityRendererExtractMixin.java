package com.hrtq.grandcraft.client.mixin;

import com.hrtq.grandcraft.client.ClientCombatPhases;
import com.hrtq.grandcraft.client.ClientGameSettings;
import com.hrtq.grandcraft.client.render.CombatPoseState;
import com.hrtq.grandcraft.combat.CombatState;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Util;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Carries an entity's combat phase from the client's phase store onto its render
 * state, once per entity per frame.
 *
 * <p>Injected on {@code LivingEntityRenderer} rather than on any concrete renderer
 * because every living-entity renderer reaches this method through its super chain
 * — so one hook serves the whole family and the player. The full descriptor is
 * mandatory: the class carries bridge overloads of the same name.
 *
 * <p>Read against the wall clock rather than the render state's {@code ageInTicks}
 * so the pose interpolates between ticks; there is no partial tick on the state to
 * reconstruct it from.
 *
 * <p>Strictly a copy, with no measuring of its own. Anything derived from the entity
 * has to be worked out on the client tick instead, because this only runs for
 * entities that are actually drawn — and the local player is not drawn in first
 * person.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererExtractMixin {
	@Inject(
			method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;"
					+ "Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
			at = @At("TAIL"))
	private void grandcraft$combatPhase(LivingEntity entity, LivingEntityRenderState state,
			float partialTick, CallbackInfo info) {
		if (!(state instanceof CombatPoseState poseState)) {
			return;
		}

		if (!ClientGameSettings.current().combatAnimations()) {
			poseState.grandcraft$setPhase(CombatState.NEUTRAL, 0.0F, null);
			return;
		}

		long now = Util.getMillis();
		int id = entity.getId();

		poseState.grandcraft$setPhase(
				ClientCombatPhases.stateOf(id, now),
				ClientCombatPhases.progressOf(id, now),
				ClientCombatPhases.travelYaw(id));
	}
}

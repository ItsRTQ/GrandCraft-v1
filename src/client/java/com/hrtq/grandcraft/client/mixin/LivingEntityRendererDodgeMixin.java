package com.hrtq.grandcraft.client.mixin;

import com.hrtq.grandcraft.client.ClientGameSettings;
import com.hrtq.grandcraft.client.render.CombatPoseState;
import com.hrtq.grandcraft.client.render.DodgeStep;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Leans a dodging actor's whole body into the step.
 *
 * <p>{@code setupRotations} is where vanilla applies every whole-body rotation it
 * has — the body yaw, the death topple, the riptide pitch — so it is the right
 * place for one more, and injecting at TAIL means the lean composes on top of the
 * body yaw exactly as riptide's pitch does.
 *
 * <p>Targets {@code LivingEntityRenderer} rather than the player's renderer: the
 * player's own {@code AvatarRenderer} override calls super in all three of its
 * branches, so this fires for the player as well as for any mob that ever gains the
 * verb. One hook, no entity types named.
 *
 * <p>The lean has to sit here rather than in the model because the humanoid rig has
 * no bone that moves the whole body — posing one would bend the actor at the waist
 * instead of tipping it off its feet.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererDodgeMixin {
	@Inject(
			method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;"
					+ "Lcom/mojang/blaze3d/vertex/PoseStack;FF)V",
			at = @At("TAIL"))
	private void grandcraft$dodgeStep(LivingEntityRenderState state, PoseStack poseStack,
			float bodyRot, float scale, CallbackInfo info) {
		if (!ClientGameSettings.current().combatAnimations()) {
			return;
		}

		if (!(state instanceof CombatPoseState poseState)) {
			return;
		}

		DodgeStep.apply(poseStack,
				poseState.grandcraft$phase(),
				poseState.grandcraft$phaseProgress(),
				poseState.grandcraft$travelYaw(),
				bodyRot,
				state.boundingBoxHeight,
				scale);
	}
}

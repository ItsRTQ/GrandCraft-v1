package com.hrtq.grandcraft.client.mixin;

import com.hrtq.grandcraft.client.ClientGameSettings;
import com.hrtq.grandcraft.client.render.CombatPoseState;
import com.hrtq.grandcraft.client.render.DodgeStep;
import com.hrtq.grandcraft.client.render.DownedStep;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Turns a dodging actor's whole body into the step, and lays a downed one on the
 * ground.
 *
 * <p>Two verbs on one hook because they need the same thing — a rotation no bone can
 * express — and this method is where vanilla keeps every whole-body rotation it has.
 * They are mutually exclusive by construction: an actor cannot be rolling and prone.
 *
 * <p>{@code setupRotations} is where vanilla applies every whole-body rotation it
 * has — the body yaw, the death topple, the riptide pitch — so it is the right
 * place for one more, and injecting at TAIL means the turn composes on top of the
 * body yaw exactly as riptide's pitch does.
 *
 * <p>Targets {@code LivingEntityRenderer} rather than the player's renderer: the
 * player's own {@code AvatarRenderer} override calls super in all three of its
 * branches, so this fires for the player as well as for any mob that ever gains the
 * verb. One hook, no entity types named.
 *
 * <p>The turn has to sit here rather than in the model because the humanoid rig has
 * no bone that moves the whole body — posing one would bend the actor at the waist
 * and leave its legs facing the way they were. The rest of the dodge, everything
 * that <em>is</em> expressible as parts, is drawn from the same clip in
 * {@code PlayerModelMixin}.
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
				bodyRot);

		// After the dodge's turn rather than before it, so that if the two ever did
		// overlap the body would be laid down last and the roll would read as a wobble
		// rather than the pose being drawn upright.
		DownedStep.apply(poseStack, poseState.grandcraft$phase());
	}
}

package com.hrtq.grandcraft.client.mixin;

import com.hrtq.grandcraft.client.ClientGameSettings;
import com.hrtq.grandcraft.client.render.CombatPoseState;
import com.hrtq.grandcraft.client.render.DodgeAnimation;
import com.hrtq.grandcraft.client.render.HumanoidCombatPose;
import com.hrtq.grandcraft.combat.CombatState;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the player's combat phases on the player's own rig.
 *
 * <p>The counterpart to {@code AbstractZombieModelMixin}, and the reason
 * {@link HumanoidCombatPose} takes bare model parts rather than a model: this is the
 * same animation against a different rig, exactly as intended. Until this existed
 * the player was the only actor whose phases reached the client and were then drawn
 * by nobody — it guarded, dodged and staggered invisibly.
 *
 * <p>TAIL because {@code Model.setupAnim} opens with {@code resetPose()}, and because
 * {@code PlayerModel.setupAnim} ends by calling {@code HumanoidModel.setupAnim} — so
 * TAIL is the first point after vanilla has finished posing. The descriptor is
 * spelled out because the class carries two bridge overloads of the same name.
 *
 * <p>Unlike the zombie's, this does <em>not</em> zero {@code attackTime}. The
 * player's swing is still vanilla's to draw, and the phases announced for a player
 * are only ever guard, dodge and stagger — none of which replaces a swing. A stagger
 * that interrupts one simply blends over whatever vanilla had reached, which is the
 * correct result.
 *
 * <p>The sleeve and trouser overlays follow for free: they are children of the arm
 * parts in the mesh, which is why vanilla's own {@code setupAnim} never copies to
 * them.
 */
@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {
	@Inject(
			method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V",
			at = @At("TAIL"))
	private void grandcraft$combatPose(AvatarRenderState state, CallbackInfo info) {
		if (!ClientGameSettings.current().combatAnimations()) {
			return;
		}

		CombatState phase = ((CombatPoseState) state).grandcraft$phase();

		if (phase == CombatState.NEUTRAL) {
			return;
		}

		// The mixin does not extend HumanoidModel, so the parts are reached through the
		// runtime type. Shadowing them is not an option: they are declared on
		// HumanoidModel, and @Shadow only resolves members of the target class.
		HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;

		float progress = ((CombatPoseState) state).grandcraft$phaseProgress();

		// The dodge is the one phase drawn from an authored clip rather than from a
		// procedural posture, so it takes the whole rig — legs included — instead of
		// the three parts the postures move. HumanoidCombatPose deliberately blends to
		// zero for the dodge phases, so the two never both apply.
		if (phase.isDodge()) {
			DodgeAnimation.apply(model, phase, progress,
					((CombatPoseState) state).grandcraft$travelYaw(), state.bodyRot);
			return;
		}

		HumanoidCombatPose.apply(model.head, model.rightArm, model.leftArm,
				phase, progress, HumanoidCombatPose.guardArmOf(state));
	}
}

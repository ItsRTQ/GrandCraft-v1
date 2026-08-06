package com.hrtq.grandcraft.client.mixin;

import com.hrtq.grandcraft.client.ClientGameSettings;
import com.hrtq.grandcraft.client.render.AttackAnimation;
import com.hrtq.grandcraft.client.render.CombatPoseState;
import com.hrtq.grandcraft.client.render.DodgeAnimation;
import com.hrtq.grandcraft.client.render.HumanoidCombatPose;
import com.hrtq.grandcraft.combat.CombatState;
import com.hrtq.grandcraft.combat.WeaponCategory;
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
 * <p>Unlike the zombie's, this does <em>not</em> zero {@code attackTime} — and that
 * survived the player gaining real attack phases, which looked like the one thing that
 * would overturn it.
 *
 * <p><strong>Zeroing it was tried on 2026-08-05 and reverted: it looked markedly
 * worse.</strong> The reasoning for trying was sound as far as it went —
 * {@code HumanoidModel.setupAttackAnimation} really does fold the head's pitch into the
 * attacking arm ({@code api-facts.md} has the formula), and {@code HumanoidClipPose}
 * really does compose onto whatever is already there, so the two do multiply. Taking
 * vanilla's swing away did not improve it, which says the authored clip is not
 * self-sufficient on this rig: it was drawn to sit on top of something, and on its own
 * the arm is wrong in a different and uglier way.
 *
 * <p>So the interaction between the two is still the open question, and the answer is not
 * "remove one of them". <strong>Do not re-propose zeroing {@code attackTime} here</strong>
 * without a runtime test to show for it.
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

		// The attack is the second phase drawn from an authored clip, and like the dodge
		// it takes the whole rig rather than the three parts the postures move.
		//
		// Only for a weapon the animator drew a swing for. A bow or an empty hand has no
		// clip and falls through to the procedural posture below, which is what every mob
		// uses — a clip authored for a greatsword played on a bow reads as stabbing with
		// it, which is worse than a plain wind-up.
		WeaponCategory weapon = ((CombatPoseState) state).grandcraft$weapon();

		if (phase.isAttack() && AttackAnimation.hasClip(weapon)) {
			AttackAnimation.apply(model, phase, progress, weapon);
			return;
		}

		HumanoidCombatPose.apply(model.head, model.rightArm, model.leftArm,
				phase, progress, HumanoidCombatPose.guardArmOf(state));
	}
}

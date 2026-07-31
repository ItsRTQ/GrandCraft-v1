package com.hrtq.grandcraft.client.mixin;

import com.hrtq.grandcraft.client.render.CombatPoseState;
import com.hrtq.grandcraft.client.render.HumanoidCombatPose;
import com.hrtq.grandcraft.combat.CombatState;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.monster.zombie.AbstractZombieModel;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gives the zombie a visible attack.
 *
 * <p>Injected at TAIL for two separate reasons, either of which alone would be
 * enough: {@code Model.setupAnim} opens by calling {@code resetPose()}, so
 * anything applied earlier is wiped; and {@code AbstractZombieModel} finishes by
 * calling {@code AnimationUtils.animateZombieArms}, so anything applied before
 * that is overwritten by the arms-out idle. The descriptor is spelled out because
 * the class carries two bridge overloads of the same name.
 *
 * <p>All the actual posing is in {@link HumanoidCombatPose}, shared rather than
 * written here, because the player's rig is the same one — adding the player is
 * meant to be this class again against a different model, not a second animation.
 */
@Mixin(AbstractZombieModel.class)
public abstract class AbstractZombieModelMixin {
	/**
	 * Silences vanilla's swing for the duration of a phase.
	 *
	 * <p>The server only announces phases for actors whose attack timing is ours end
	 * to end, so once one is running vanilla's swing is not a second opinion to blend
	 * with — it is a leftover. Zeroing {@code attackTime} stops both
	 * {@code HumanoidModel.setupAttackAnimation} and the attack branch of
	 * {@code AnimationUtils.animateZombieArms}, leaving the phase pose in sole charge
	 * of the whole startup-active-recovery cycle.
	 *
	 * <p>Done here rather than during extraction because
	 * {@code LivingEntityRenderer.extractRenderState} runs <em>before</em>
	 * {@code extractHumanoidRenderState} assigns {@code attackTime} — zeroing it
	 * there is silently overwritten a few instructions later. HEAD of the model's
	 * own setup is the first point that is provably after the write and before both
	 * readers. The state is rebuilt every frame, so writing to it here costs nothing.
	 */
	@Inject(
			method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/ZombieRenderState;)V",
			at = @At("HEAD"))
	private void grandcraft$suppressVanillaSwing(ZombieRenderState state, CallbackInfo info) {
		if (((CombatPoseState) state).grandcraft$phase() != CombatState.NEUTRAL) {
			state.attackTime = 0.0F;
		}
	}

	@Inject(
			method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/ZombieRenderState;)V",
			at = @At("TAIL"))
	private void grandcraft$combatPose(ZombieRenderState state, CallbackInfo info) {
		CombatState phase = ((CombatPoseState) state).grandcraft$phase();

		if (phase == CombatState.NEUTRAL) {
			return;
		}

		// The mixin does not extend HumanoidModel, so the parts are reached through
		// the runtime type. Shadowing them is not an option: they are declared on
		// HumanoidModel, and @Shadow only resolves members of the target class.
		HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;

		HumanoidCombatPose.apply(model.head, model.rightArm, model.leftArm,
				phase, ((CombatPoseState) state).grandcraft$phaseProgress());
	}
}

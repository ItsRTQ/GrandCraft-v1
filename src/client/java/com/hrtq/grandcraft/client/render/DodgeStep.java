package com.hrtq.grandcraft.client.render;

import com.hrtq.grandcraft.combat.CombatState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

/**
 * The whole-body half of a dodge: every turn the clip asks for, which no model part
 * can express.
 *
 * <p>{@link DodgeAnimation} is the file to edit when the step looks wrong; this one
 * only decides where the turn is applied, and the answer has not changed — it lives on
 * the {@code PoseStack} because the humanoid rig has no bone that turns the whole
 * actor.
 *
 * <p>That covers the chest's turn as well as the hips', and not out of laziness: on
 * vanilla's rig the arms are siblings of the torso rather than children, so a turn
 * left on the torso twists them about their own length instead of carrying them round
 * the body. {@code HumanoidClipPose} has the full account.
 *
 * <p>Targets {@code LivingEntityRenderer} rather than the player's renderer, through
 * {@code LivingEntityRendererDodgeMixin}, so it fires for any actor that ever gains
 * the verb rather than for the player alone.
 *
 * <h2>This used to be a procedural lean, and is not any more</h2>
 *
 * The first version tipped the body by a hard-coded {@code LEAN_DEGREES} on a
 * snap-in, ease-out envelope, because there was no authored animation to take the
 * shape from. There is now, and two body rotations fighting each other is worse than
 * either alone — so the tip moved into the clip, where it is carried by the chest and
 * therefore leaves the legs planted, and what remains here is the hips' turn.
 *
 * <h2>The pivot is not the origin</h2>
 *
 * At this point in the transform the origin sits at the actor's feet, which is where
 * a hip turn should hinge from — but the stack has already been scaled by the time
 * {@code setupRotations} runs, so anything measured in blocks would have to be divided
 * by {@code scale}. A turn about the vertical axis needs no pivot at all, which is
 * why nothing here does that division any more.
 */
public final class DodgeStep {
	private static final float RADIANS_TO_DEGREES = (float) (180.0 / Math.PI);

	private DodgeStep() {
	}

	/**
	 * Turns the body, if this actor is mid-dodge.
	 *
	 * @param phase     the actor's combat phase
	 * @param progress  how far through that phase, 0 to 1
	 * @param travelYaw the dodge's heading in world degrees, or {@code null} when none
	 *                  has been sampled
	 * @param bodyRot   the body yaw vanilla has already applied, which is what the
	 *                  heading is measured against
	 */
	public static void apply(PoseStack poseStack, CombatState phase, float progress,
			Float travelYaw, float bodyRot) {
		float turn = DodgeAnimation.wholeBodyYRot(phase, progress, travelYaw, bodyRot);

		if (turn == 0.0F) {
			return;
		}

		// Negated on the way onto the stack. A ModelPart's yRot grows to the actor's
		// right, and YP here grows the other way — vanilla establishes that by applying
		// the body yaw as (180 - bodyRot) rather than as bodyRot.
		poseStack.mulPose(Axis.YP.rotationDegrees(-turn * RADIANS_TO_DEGREES));
	}
}

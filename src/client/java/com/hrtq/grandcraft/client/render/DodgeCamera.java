package com.hrtq.grandcraft.client.render;

import com.hrtq.grandcraft.client.ClientCombatPhases;
import com.hrtq.grandcraft.combat.CombatState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

/**
 * The first-person half of a dodge.
 *
 * <p><strong>This is the file to edit when the dodge does not feel like one from
 * inside your own head.</strong> Almost everyone plays first person, where the
 * third-person lean is by definition invisible — without this a dodge is a
 * teleport: the invulnerability works, the stamina goes, and nothing on screen says
 * anything happened.
 *
 * <p>Directional, and driven by the same curve as {@link DodgeStep} so the camera
 * and the body are always doing the same thing at the same moment. Stepping the way
 * you are looking dips the view; stepping sideways rolls it. Without splitting them
 * that way every dodge feels identical regardless of which one you chose, which
 * wastes the only feedback a first-person player gets about a decision they just
 * made under pressure.
 *
 * <p>Deliberately small. A camera that actually followed the body through its full
 * motion is the honest reading, but it is disorienting mid-fight and a real nausea
 * risk, and neither is worth it for feedback that only has to say "you committed,
 * and you are briefly safe".
 */
public final class DodgeCamera {
	/** Degrees the view drops when stepping along the line of sight. */
	private static final float DIP_DEGREES = 11.0F;

	/** Degrees the view rolls when stepping across it. */
	private static final float TILT_DEGREES = 13.0F;

	private DodgeCamera() {
	}

	/**
	 * Adds the step to the camera transform, if the given actor is mid-dodge.
	 *
	 * @param poseStack the stack vanilla folds into the projection matrix
	 * @param viewYaw   where the camera is pointing, so the step can be split into
	 *                  its along-view and across-view parts
	 */
	public static void apply(PoseStack poseStack, int entityId, float viewYaw, long nowMillis) {
		CombatState phase = ClientCombatPhases.stateOf(entityId, nowMillis);

		if (!phase.isDodge()) {
			return;
		}

		float amount = DodgeStep.leanFraction(phase, ClientCombatPhases.progressOf(entityId, nowMillis));

		if (amount == 0.0F) {
			return;
		}

		// Split the travel direction about where the player is looking rather than
		// about where their body faces: in first person the view is the only frame of
		// reference they have.
		Float travelYaw = ClientCombatPhases.travelYaw(entityId);
		double relative = Math.toRadians(travelYaw == null ? 0.0F : travelYaw - viewYaw);

		float along = (float) Math.cos(relative);
		float across = (float) Math.sin(relative);

		poseStack.mulPose(Axis.XP.rotationDegrees(amount * DIP_DEGREES * along));
		poseStack.mulPose(Axis.ZP.rotationDegrees(amount * TILT_DEGREES * across));
	}
}

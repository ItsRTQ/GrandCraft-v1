package com.hrtq.grandcraft.client.render;

import com.hrtq.grandcraft.client.ClientCombatPhases;
import com.hrtq.grandcraft.combat.CombatState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.util.Ease;

/**
 * The first-person half of a dodge.
 *
 * <p><strong>This is the file to edit when the dodge does not feel like one from
 * inside your own head.</strong> Almost everyone plays first person, where the
 * third-person lean is by definition invisible — without this a dodge is a
 * teleport: the invulnerability works, the stamina goes, and nothing on screen says
 * anything happened.
 *
 * <p>Directional. Stepping the way you are looking dips the view; stepping sideways
 * rolls it. Without splitting them
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

	/**
	 * Fraction of the whole dodge the camera move occupies. The rest of the dodge is
	 * spent level and still moving — which is what makes it a step rather than a pose.
	 */
	private static final float SPAN = 0.45F;

	/** Fraction of that window spent snapping in, as opposed to releasing. */
	private static final float ATTACK = 0.22F;

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

		float amount = amount(phase, ClientCombatPhases.progressOf(entityId, nowMillis));

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

	/**
	 * How far into the camera move the dodge is, from 0 to 1.
	 *
	 * <p>Measured across the dodge as a whole rather than per phase, so the view does
	 * not kink at the handover from invulnerable to recovering. The two halves are
	 * assumed equal, for the same reason {@link DodgeAnimation} assumes it.
	 *
	 * <p>The move is deliberately <em>over well before the dodge is</em>. A step is a
	 * push off, not a pose held for the duration: the view snaps into it in a couple of
	 * ticks and is level again while the player is still travelling and still
	 * committed. Stretching it across the whole window turned a step into a drift.
	 *
	 * <p>This curve used to live in {@code DodgeStep} and drove the third-person lean
	 * as well, so that the camera and the body agreed. The body now takes its shape
	 * from the animator's clip instead, and this stayed behind — first person has no
	 * authored pose to follow, and this is tuned rather than merely inherited. Nausea
	 * is the constraint here and silhouette is the constraint there; they no longer
	 * have a reason to be the same number.
	 */
	private static float amount(CombatState phase, float progress) {
		float t = Math.clamp(progress, 0.0F, 1.0F) * 0.5F;

		if (phase != CombatState.DODGE_ACTIVE) {
			t += 0.5F;
		}

		if (t >= SPAN) {
			return 0.0F;
		}

		float u = t / SPAN;

		// Snap in, ease out. The asymmetry is the whole character of the move — an even
		// curve in and out is a sway.
		return u < ATTACK
				? Ease.outQuart(u / ATTACK)
				: 1.0F - Ease.inOutCubic((u - ATTACK) / (1.0F - ATTACK));
	}
}

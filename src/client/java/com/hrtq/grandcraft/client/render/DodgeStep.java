package com.hrtq.grandcraft.client.render;

import com.hrtq.grandcraft.combat.CombatState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.util.Ease;

/**
 * The third-person half of a dodge: a fast step, the way a boxer shifts off a line.
 *
 * <p><strong>This is the file to edit when the step looks wrong from outside.</strong>
 * It is a rotation of the whole body rather than a pose of its limbs, which is why
 * it lives here and not in {@link HumanoidCombatPose} — and why it needs no model
 * mixin and would work unchanged for any entity that ever gains the dodge verb.
 *
 * <p>Deliberately a lean and not a roll. A full tumble is a lot of animation to
 * spend on thirteen ticks, and it fights the fast leg movement vanilla is already
 * playing from the launch itself — the legs are what read as the step, and the body
 * only has to agree with them. The displacement does most of the work here; this
 * exists so the actor looks like it went rather than like it slid.
 *
 * <h2>Two things that are easy to get wrong here</h2>
 * <ul>
 *   <li><strong>The pivot is not the origin.</strong> At this point in the transform
 *       the origin sits at the actor's feet, which is exactly where a lean should
 *       hinge from — but it is divided by {@code scale}, because the stack has
 *       already been scaled by the time {@code setupRotations} runs. That is the
 *       same correction vanilla applies to its own upside-down translate.</li>
 *   <li><strong>The lean axis is yawed to the direction of travel.</strong> Without
 *       it every dodge leans forward, so a backstep tips face-first while sliding
 *       backwards. Yawing about the vertical axis first is safe to do about the
 *       origin, since the pivot is directly above it.</li>
 * </ul>
 */
public final class DodgeStep {
	/** How far the body tips into the direction it is travelling, at the extreme. */
	private static final float LEAN_DEGREES = -26.0F;

	/**
	 * Height the lean hinges from, as a fraction of the hitbox. Low, because a step
	 * drives from the feet — hinging at the waist reads as a stumble instead.
	 */
	private static final float PIVOT_HEIGHT = 0.15F;

	/**
	 * Fraction of the whole dodge the lean occupies. The rest of the dodge is spent
	 * upright and still moving — which is what makes it a step rather than a pose.
	 */
	private static final float LEAN_SPAN = 0.45F;

	/** Fraction of that window spent snapping in, as opposed to releasing. */
	private static final float LEAN_ATTACK = 0.22F;

	private DodgeStep() {
	}

	/**
	 * Leans the body, if this actor is mid-dodge.
	 *
	 * @param phase      the actor's combat phase
	 * @param progress   how far through that phase, 0 to 1
	 * @param travelYaw  the dodge's heading in world degrees, or {@code null} to lean
	 *                   straight forward
	 * @param bodyRot    the body yaw vanilla has already applied
	 * @param boxHeight  the actor's hitbox height, for the pivot
	 * @param scale      the render scale already on the stack
	 */
	public static void apply(PoseStack poseStack, CombatState phase, float progress,
			Float travelYaw, float bodyRot, float boxHeight, float scale) {
		if (!phase.isDodge() || scale <= 0.0F) {
			return;
		}

		float lean = LEAN_DEGREES * amount(phase, progress);

		if (lean == 0.0F) {
			return;
		}

		float pivotY = boxHeight * PIVOT_HEIGHT / scale;

		// Relative to the way the body is already facing, since vanilla has applied
		// that yaw before handing us the stack.
		float heading = travelYaw == null ? 0.0F : travelYaw - bodyRot;

		poseStack.mulPose(Axis.YP.rotationDegrees(heading));
		poseStack.rotateAround(Axis.XP.rotationDegrees(lean), 0.0F, pivotY, 0.0F);
		poseStack.mulPose(Axis.YP.rotationDegrees(-heading));
	}

	/**
	 * How far into the lean the actor is, from 0 to 1.
	 *
	 * <p>Measured across the dodge as a whole rather than per phase, so the body does
	 * not kink at the handover from invulnerable to recovering.
	 *
	 * <p>The lean is deliberately <em>over well before the dodge is</em>. A step is a
	 * push off, not a pose held for the duration: the body snaps into it in a couple
	 * of ticks and is upright again while the actor is still travelling and still
	 * committed. Stretching it across the whole window turned a step into a lean, and
	 * a lean that outlasts the movement reads as drifting.
	 */
	private static float amount(CombatState phase, float progress) {
		float t = phase == CombatState.DODGE_ACTIVE
				? Math.clamp(progress, 0.0F, 1.0F) * 0.5F
				: 0.5F + Math.clamp(progress, 0.0F, 1.0F) * 0.5F;

		if (t >= LEAN_SPAN) {
			return 0.0F;
		}

		float u = t / LEAN_SPAN;

		// Snap in, ease out. The asymmetry is the whole character of the move — an
		// even curve in and out is a sway.
		return u < LEAN_ATTACK
				? Ease.outQuart(u / LEAN_ATTACK)
				: 1.0F - Ease.inOutCubic((u - LEAN_ATTACK) / (1.0F - LEAN_ATTACK));
	}

	/** The lean at this instant, for anything that has to agree with it. */
	public static float leanFraction(CombatState phase, float progress) {
		return phase.isDodge() ? amount(phase, progress) : 0.0F;
	}
}

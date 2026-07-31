package com.hrtq.grandcraft.client.render;

import com.hrtq.grandcraft.client.ClientCombatPhases;
import com.hrtq.grandcraft.combat.CombatState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.util.Ease;

/**
 * Shakes the first-person camera while the local player is staggered.
 *
 * <p>Exists because the flinch pose does not: the player's own rig is not drawn in
 * first person, so without this a stagger is something that happens entirely to
 * somebody the player cannot see. That matters most for a guard break, which is a
 * punishment with no damage attached — nothing flashes, nothing knocks you back, and
 * the only other signal is the stamina bar. A player looking at the fight rather than
 * at their bar would have no idea they had just been opened up.
 *
 * <p>A dip and a roll rather than a random shake. Random jitter reads as damage or as
 * a bug; a single decisive lurch reads as losing your footing, which is what a
 * stagger is. It settles rather than oscillates for the same reason — the recovery is
 * the part the player has to feel the length of.
 *
 * <p>Deliberately not directional, unlike {@link DodgeCamera}. A stagger is not
 * something the actor chose to do in a direction, and the hit that caused it may not
 * have come from anywhere in particular — a guard break has no attacker position at
 * all by the time this runs.
 */
public final class StaggerCamera {
	/** How far the view pitches down at the worst of it. */
	private static final float DIP_DEGREES = 7.0F;

	/** How far it rolls, always to the same side, so the lurch has a shape. */
	private static final float ROLL_DEGREES = 4.5F;

	/** Fraction of the stagger spent snapping in before it begins to settle. */
	private static final float SNAP_IN = 0.2F;

	private StaggerCamera() {
	}

	public static void apply(PoseStack poseStack, int entityId, long nowMillis) {
		if (ClientCombatPhases.stateOf(entityId, nowMillis) != CombatState.STAGGERED) {
			return;
		}

		float amount = amount(ClientCombatPhases.progressOf(entityId, nowMillis));

		if (amount == 0.0F) {
			return;
		}

		poseStack.mulPose(Axis.XP.rotationDegrees(amount * DIP_DEGREES));
		poseStack.mulPose(Axis.ZP.rotationDegrees(amount * ROLL_DEGREES));
	}

	/**
	 * The same envelope {@code HumanoidCombatPose} gives the flinch, so the camera and
	 * the third-person body are provably the same motion rather than two things that
	 * happen to look similar.
	 */
	private static float amount(float progress) {
		float t = Math.clamp(progress, 0.0F, 1.0F);

		return t < SNAP_IN
				? Ease.outCubic(t / SNAP_IN)
				: 1.0F - Ease.inCubic((t - SNAP_IN) / (1.0F - SNAP_IN));
	}
}

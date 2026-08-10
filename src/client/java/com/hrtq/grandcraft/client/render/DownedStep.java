package com.hrtq.grandcraft.client.render;

import com.hrtq.grandcraft.combat.CombatState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

/**
 * The whole-body half of the downed pose: laying the actor on the ground.
 *
 * <p>{@link DownedPose} draws the limbs; this puts the body they hang off flat. The
 * split is the same one {@link DodgeStep} makes, and it exists for the same reason —
 * the humanoid rig has no bone that moves the whole actor, so a rotation of the body
 * has to live on the {@code PoseStack}.
 *
 * <h2>This is the first clip whose {@code root} bone carries keyframes</h2>
 *
 * <p>Every other delivery leaves {@code root} empty, and the converter drops it. This
 * one keys it — a −90° pitch and a shift — because "lying down" is not expressible any
 * other way. {@link #PITCH_DEGREES}, {@link #FORWARD} and {@link #LIFT} are that bone,
 * read straight off {@code character_down}, and they are the whole of this file.
 *
 * <h2>How the four candidate readings were narrowed to one</h2>
 *
 * <p>Offline, before any of this was written, by rendering the clip under each and
 * looking at where the body ended up — the method the sword established. The root can
 * be read with or without the half-turn conjugation the <em>bones</em> need, and its
 * position can be applied before or after its rotation, which is four combinations:
 *
 * <pre>
 * conjugated, position first   body floats 15-21 units in the air
 * conjugated, position after   body lies flat but 19-51 units out in front
 * raw,        position first   body floats 15-21 units in the air
 * raw,        position after   body lies flat, spanning the origin      &lt;- this one
 * </pre>
 *
 * <p>So the root is read <strong>raw</strong> while the bones are conjugated, and its
 * shift is applied <strong>after</strong> its rotation. Those are not in tension: the
 * conjugation is a change of frame for the model's own bones, and the root is not one
 * of them — it is the whole-model transform, applied in a frame that already carries
 * vanilla's own half turn from {@code setupRotations}.
 *
 * <p>Position-after-rotation means {@code translate} then {@code mulPose} on a
 * {@code PoseStack}, which composes right to left — <em>not</em> the order it reads in.
 * Vanilla's swimming branch does the opposite pair in the same method and picks its own
 * offset to suit; it is the corroboration for the units, not for the order.
 *
 * <h2>What is still a guess, and what to change if it is wrong</h2>
 *
 * <p>The offline render settles the clip's own geometry. What it cannot settle is the
 * frame {@code setupRotations} hands over — whether its Z runs the way the model's
 * does. If the body lies flat but face-up instead of face-down, or with its head where
 * its feet should be, <strong>the fix is {@link #PITCH_DEGREES}</strong>: try +90.
 * If it lies correctly but sunk into the floor or floating, that is {@link #LIFT}. If
 * it is correct but offset along its own length, that is {@link #FORWARD}. Nothing
 * else in the mod reads these.
 */
public final class DownedStep {
	/**
	 * The clip's {@code root} pitch, in degrees, laying the actor flat.
	 *
	 * <p>−90 is also vanilla's own figure for the same job — riptide spin uses
	 * {@code Axis.XP.rotationDegrees(-90 - xRot)} in this very method, which is what
	 * establishes negative XP as pitching forward. The clip agreeing with it is a
	 * good sign rather than a coincidence: both are "put the player on their front".
	 */
	private static final float PITCH_DEGREES = -90.0F;

	/**
	 * The clip's {@code root} Z shift, in blocks — 19 model units at 16 to the block.
	 *
	 * <p>What stops the body being drawn a metre from the player it belongs to. The
	 * rotation swings the whole actor about its feet, which leaves it lying entirely to
	 * one side of where it was standing; this brings it back so the player's position is
	 * somewhere under their own chest.
	 */
	private static final float FORWARD = 19.0F / 16.0F;

	/**
	 * The clip's {@code root} Y shift, in blocks — 3 model units.
	 *
	 * <p>Half a body's thickness, so the actor rests <em>on</em> the ground rather than
	 * with its midline in it.
	 */
	private static final float LIFT = 3.0F / 16.0F;

	private DownedStep() {
	}

	/**
	 * Lays a downed actor down, and leaves every other phase alone.
	 *
	 * <p>No easing and no blend, matching {@link DownedPose}: going down is a cut. The
	 * blow that caused it is the transition, and a body settling gracefully over a
	 * quarter of a second reads as lying down on purpose.
	 */
	public static void apply(PoseStack poseStack, CombatState phase) {
		if (phase != CombatState.DOWNED) {
			return;
		}

		// Reads as "shift, then rotate" and composes as "rotate, then shift" — see the
		// class notes. Swapping these two lines is not a refactor.
		poseStack.translate(0.0F, LIFT, FORWARD);
		poseStack.mulPose(Axis.XP.rotationDegrees(PITCH_DEGREES));
	}
}

package com.hrtq.grandcraft.client.render;

import com.hrtq.grandcraft.client.animation.BedrockClip;
import com.hrtq.grandcraft.client.animation.GrandCraftAnimations;
import com.hrtq.grandcraft.client.animation.HumanoidClipPose;
import com.hrtq.grandcraft.combat.CombatState;
import net.minecraft.client.model.HumanoidModel;

/**
 * Picks the dodge clip for a step and says how far into it the actor is.
 *
 * <p><strong>This is the file to edit when the wrong dodge plays.</strong> The clips
 * themselves are the animator's; how they are chosen and timed is here.
 *
 * <p>The animator exported four, one per direction, under the names they carry in
 * Blockbench. Those names are used verbatim rather than renamed on the way in, so a
 * re-export drops straight over the resource with nothing to rename — including the
 * misspelling of "dodge" they all share.
 *
 * <p><strong>The names change between deliveries, and only these four constants care.</strong>
 * The 2026-08-03 export renamed every clip ({@code dogde_back1} → {@code dogde_back},
 * {@code animation_side_left} → {@code dogde_side_left}, {@code right} →
 * {@code dogde_right}, {@code animation_front} → {@code dogde_front}) without changing a
 * single keyframe time or length. A clip that fails to resolve plays nothing at all, so
 * check these first when a re-export makes the dodge go still.
 *
 * <p><strong>Three of the four are in use.</strong> The forward direction plays the
 * backstep clip instead of the authored forward one — see {@link #FRONT}.
 */
public final class DodgeAnimation {
	private static final String BACK = "dogde_back";

	/**
	 * <strong>The forward dodge deliberately plays the backstep clip.</strong> Not a
	 * placeholder left behind by accident — the user's call after seeing both.
	 *
	 * <p>The authored forward clip is {@code dogde_front}, and it is still in the
	 * resource; restoring it is this one line. <strong>The 2026-08-03 re-export did not
	 * change what made it read as a dive</strong> — it moved that clip's arms by a pixel
	 * or two and left the -67.4° chest pitch and the eleven-pixel chest translation
	 * exactly as they were, so the substitution below still stands. What is wrong with it is not a runtime
	 * fault: it composes {@code torso} +15° with {@code Upperbody} -67.4° into -52° of
	 * net pitch, and on the animator's rig that reads as a dive because the chest also
	 * translates eleven pixels forward. Vanilla's torso cannot translate — it is a loose
	 * cuboid on top of the legs rather than a segment welded to the hips — so the
	 * translation is dropped and what is left folds the box down from the hip pivot.
	 *
	 * <p>The backstep is a third of that pitch (+30°) and eases in and out rather than
	 * snapping to its extreme on the first frame, which is why it survives the same
	 * treatment. Reusing it costs a lean in the wrong direction, which on a blocky rig
	 * reads as a brace; the fold did not read as anything.
	 */
	private static final String FRONT = BACK;

	private static final String LEFT = "dogde_side_left";
	private static final String RIGHT = "dogde_right";

	/** Half-width of the arc that counts as dodging straight ahead, or straight back. */
	private static final float FORWARD_ARC = 45.0F;
	private static final float BACKWARD_ARC = 135.0F;

	/**
	 * Fraction of the dodge over which the pose is released back to vanilla.
	 *
	 * <p>A clip that ends on its rest pose needs no such thing, and three of the four
	 * do. The right-hand one does not: its hips are left about two degrees off zero at
	 * the last keyframe, and without a release those two degrees would sit there until
	 * the phase ended and then vanish in a single frame. Two degrees is not a visible
	 * pop, but a clip that ended forty degrees out would be, and a runtime that only
	 * works for clips that happen to be tidy is a trap for the next export.
	 *
	 * <p>Short enough not to eat the pose. Everything authored has long since finished
	 * by the time this starts.
	 */
	private static final float RELEASE = 0.15F;

	private DodgeAnimation() {
	}

	/**
	 * Poses the rig for one frame of a dodge, or leaves it alone if this actor is not
	 * dodging.
	 *
	 * @param progress  how far through the current phase, 0 to 1
	 * @param travelYaw the dodge's heading in world degrees, or null if none was sampled
	 * @param bodyRot   the body yaw, to turn that heading into a direction the actor
	 *                  would recognise
	 */
	public static void apply(HumanoidModel<?> model, CombatState phase, float progress,
			Float travelYaw, float bodyRot) {
		if (!phase.isDodge()) {
			return;
		}

		BedrockClip clip = clipFor(travelYaw, bodyRot);
		float t = elapsed(phase, progress);

		HumanoidClipPose.apply(model, clip, t * clip.length(), blend(t));
	}

	/**
	 * The turn for this frame, in radians, positive to the actor's right; zero when it
	 * is not dodging.
	 *
	 * <p>Read separately from {@link #apply} because every yaw in these clips belongs
	 * on the {@code PoseStack} rather than on any model part — see
	 * {@link HumanoidClipPose#wholeBodyYRot}, which is where the reason is written down.
	 */
	public static float wholeBodyYRot(CombatState phase, float progress, Float travelYaw,
			float bodyRot) {
		if (!phase.isDodge()) {
			return 0.0F;
		}

		BedrockClip clip = clipFor(travelYaw, bodyRot);
		float t = elapsed(phase, progress);

		// Released on the same curve as the pose, or the body and its limbs would
		// disagree about when the dodge is over.
		return HumanoidClipPose.wholeBodyYRot(clip, t * clip.length()) * blend(t);
	}

	/**
	 * The clip for the direction this dodge is travelling, relative to the way the
	 * actor is facing — so a sideways dodge plays sideways whichever way the world is
	 * pointing.
	 *
	 * <p>An unknown heading is a backstep, which is the same default
	 * {@code ClientDodge} applies when a dodge is asked for with no movement input.
	 */
	private static BedrockClip clipFor(Float travelYaw, float bodyRot) {
		return GrandCraftAnimations.clip(GrandCraftAnimations.PLAYER_DODGE,
				name(travelYaw, bodyRot));
	}

	private static String name(Float travelYaw, float bodyRot) {
		if (travelYaw == null) {
			return BACK;
		}

		// Vanilla yaw grows clockwise, so a heading ahead of the body's own is a
		// heading off to the actor's right.
		float relative = wrap(travelYaw - bodyRot);
		float magnitude = Math.abs(relative);

		if (magnitude <= FORWARD_ARC) {
			return FRONT;
		}

		if (magnitude >= BACKWARD_ARC) {
			return BACK;
		}

		return relative > 0.0F ? RIGHT : LEFT;
	}

	/**
	 * How much of the authored pose is applied, from 1 down to 0 across the last
	 * {@link #RELEASE} of the dodge. See that constant for why it exists at all.
	 */
	private static float blend(float t) {
		float remaining = 1.0F - t;

		return remaining >= RELEASE ? 1.0F : Math.max(remaining, 0.0F) / RELEASE;
	}

	/**
	 * How far through the whole dodge this frame is, from 0 to 1.
	 *
	 * <p>The clip is stretched across the <em>whole</em> dodge rather than played at
	 * the tempo it was authored at. Phase lengths here are config fields and a clip's
	 * length is fixed, so playing it straight would leave a long dodge posed and still
	 * for its tail and would cut a short one off mid-pose. Stretching means the pose
	 * always lands back on rest exactly as recovery ends, whatever the settings say —
	 * which is the objection that kept keyframes out of the procedural poses, answered.
	 *
	 * <p>The two halves are assumed equal, because the render state carries the
	 * progress through the <em>current</em> phase and not the length of the other one.
	 * {@code DodgeStep} made the same assumption for the lean it replaces, and at these
	 * durations a skew between the halves reads as the pose being slightly fast or slow
	 * rather than as a break.
	 */
	private static float elapsed(CombatState phase, float progress) {
		float t = Math.clamp(progress, 0.0F, 1.0F) * 0.5F;

		return phase == CombatState.DODGE_ACTIVE ? t : t + 0.5F;
	}

	private static float wrap(float degrees) {
		float wrapped = degrees % 360.0F;

		if (wrapped >= 180.0F) {
			wrapped -= 360.0F;
		}

		if (wrapped < -180.0F) {
			wrapped += 360.0F;
		}

		return wrapped;
	}
}

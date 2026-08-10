package com.hrtq.grandcraft.client.render;

import com.hrtq.grandcraft.client.animation.BedrockClip;
import com.hrtq.grandcraft.client.animation.GrandCraftAnimations;
import com.hrtq.grandcraft.client.animation.HumanoidClipPose;
import com.hrtq.grandcraft.combat.CombatState;
import net.minecraft.client.model.HumanoidModel;

/**
 * The limbs of a downed actor: the animator's {@code character_down}, looped.
 *
 * <p><strong>This is the file to edit when the prone pose looks wrong from the waist
 * up.</strong> The body's own rotation onto the ground is {@link DownedStep}'s — the
 * rig has no bone that lays an actor flat — and the two together are the whole pose.
 * Sibling of {@link AttackAnimation} and {@link DodgeAnimation} by design.
 *
 * <h2>Looped on its own clock, not on the phase's</h2>
 *
 * <p>Every other clip in the mod is stretched across the phase that owns it: a wind-up
 * plays once over however long the wind-up lasts, and the phase's progress <em>is</em>
 * the clip's time. That is exactly wrong here. Being down runs for up to a minute and
 * the clip is a one-second struggle, so playing it against the phase would draw a
 * single, imperceptibly slow writhe over the whole bleed-out.
 *
 * <p>So this reads the world clock instead and wraps. The consequence worth stating:
 * <em>nothing about this pose says how long is left</em>. The clock is on the HUD, and
 * an ally deciding whether to come for you reads the fact that you are down, not how
 * far through it you are.
 *
 * <h2>Absolute, and the whole rig</h2>
 *
 * <p>Absolute for {@code AttackAnimation}'s reason and more so: a prone body composed
 * on top of vanilla's walk cycle would swim. And the whole rig rather than the three
 * parts a procedural posture moves — legs included — because every part of a body
 * lying on the ground is somewhere vanilla would never put it.
 * {@code HumanoidCombatPose} deliberately blends to zero for this phase, so the two
 * never both apply.
 *
 * <p>No fade. Going down is a hard cut, unlike a wind-up that eases in: the blow that
 * caused it is the transition, and a body easing gently to the floor over a quarter of
 * a second reads as lying down on purpose.
 */
public final class DownedPose {
	/** The animator's name for it, used verbatim so a re-export drops in unrenamed. */
	private static final String CLIP = "character_down";

	private static final float MILLIS_PER_SECOND = 1000.0F;

	private DownedPose() {
	}

	/** Poses the rig for one frame, or leaves it alone if this actor is not down. */
	public static void apply(HumanoidModel<?> model, CombatState phase, long nowMillis) {
		if (phase != CombatState.DOWNED) {
			return;
		}

		BedrockClip clip = clip();

		HumanoidClipPose.apply(model, clip, loopedSeconds(clip, nowMillis), 1.0F,
				true, HumanoidClipPose.ClipRig.VANILLA_MATCHED);
	}

	/**
	 * The clip, or the empty one if the pack does not have it.
	 *
	 * <p><strong>A clip that fails to resolve poses nothing at all, silently</strong> —
	 * the same trap {@code AttackAnimation} and {@code DodgeAnimation} both record being
	 * caught by. If a downed player stands bolt upright, the name above is the first
	 * thing to check.
	 */
	private static BedrockClip clip() {
		return GrandCraftAnimations.clip(GrandCraftAnimations.PLAYER_DOWNED, CLIP);
	}

	/**
	 * Where in the clip this frame is, wrapped into its length.
	 *
	 * <p>Off the world clock rather than off any per-actor state, which has a property
	 * worth keeping: every downed player on the server is at the same point in the
	 * struggle. That is not realism, it is legibility — two bodies on the ground moving
	 * in step read as one thing that has happened twice, and two out of phase read as
	 * two different things.
	 */
	private static float loopedSeconds(BedrockClip clip, long nowMillis) {
		float length = clip.length();

		if (length <= 0.0F) {
			return 0.0F;
		}

		return (nowMillis / MILLIS_PER_SECOND) % length;
	}
}

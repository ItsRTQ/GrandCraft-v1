package com.hrtq.grandcraft.combat;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.animation.state.AnimationTest;
import com.geckolib.animation.state.AnimationTimeline;
import java.util.EnumMap;
import java.util.Map;

/**
 * Maps combat phases to GeckoLib clips, and stretches each clip to fill its phase.
 *
 * <p>This is the bridge between the mod's two animation systems. The phase pipeline
 * already delivers state to every watching client — {@code CombatPhasePayload} into
 * {@code ClientCombatPhases} — and until now only the vanilla-model mixins listened.
 * A GeckoLib model has no {@code ModelPart}s for those mixins to rotate, so it reads
 * the same data here and picks a clip instead.
 *
 * <h2>Playback speed follows the phase, not the file</h2>
 * A clip's authored length and its phase's configured length are two independent
 * numbers, and left alone they disagree: a 12.5 tick swing mapped to a 5 tick wind-up
 * would get 40% of the way through before the phase ended, so the blow would land
 * with the arms still rising and the swing itself would never play.
 *
 * <p>So the controller's speed is set to {@code clipTicks / phaseTicks} every frame.
 * A clip always fills exactly its phase, whatever the phase is configured to. That
 * makes the timings in {@code /grandcraft config combat} genuinely drive the
 * animation: raise a mob's stagger and its flinch plays longer and slower; shorten a
 * wind-up and the telegraph snaps. It also means the visual and the damage can never
 * drift apart, which is the failure this is really guarding against.
 *
 * <h2>One clip per phase</h2>
 * Each phase is scaled independently, so a clip mapped to two phases would be
 * restarted and re-scaled at the boundary rather than flowing across it. Give each
 * phase its own clip — wind-up, slam, recovery, flinch — which is also how an
 * animator naturally thinks about it.
 *
 * <p>Phases with no clip fall through to {@link PlayState#STOP}, leaving whatever
 * other controller is running — usually locomotion — in charge. That is deliberate:
 * a mob can be given a wind-up before it has a flinch, and unmapped phases simply
 * stay as they were.
 *
 * <p>Keep the controller's blend <strong>short</strong> — a tick or two. A blend is
 * measured in ticks like everything else, so a long one eats the phase it is meant
 * to be showing, and on the two-tick damage window it would swallow the clip
 * entirely. Zero is legal and snaps hard between clips, which is visible as a pop
 * when a phase begins and again when it hands back to locomotion.
 *
 * <h2>Do not map {@link CombatState#GUARDING} here</h2>
 * A held guard is granted in short repeating leases rather than as one phase, so
 * {@code phaseTicksOf} reports the lease length, not how long the guard has been up.
 * Scaling to that would play a guard clip at lease speed, over and over. This is the
 * same sawtooth that already caught the vanilla-rig guard pose, which is why that one
 * is forbidden from reading phase progress. A guard needs a held pose, not a scaled
 * clip — map the raise and the recovery if they need animating, and hold the guard
 * itself from a separate looping controller.
 */
public final class CombatPhaseAnimations {
	private final Map<CombatState, RawAnimation> clips = new EnumMap<>(CombatState.class);

	/** Registers the clip to play while {@code state} is running. */
	public CombatPhaseAnimations on(CombatState state, RawAnimation animation) {
		this.clips.put(state, animation);
		return this;
	}

	/**
	 * Drives one animation controller from the entity's current phase.
	 *
	 * @param entityId the client-side id of the entity being animated
	 */
	public <T extends GeoAnimatable> PlayState play(AnimationTest<T> test, int entityId) {
		CombatPhaseView view = CombatPhaseView.get();
		RawAnimation clip = this.clips.get(view.stateOf(entityId));

		if (clip == null) {
			return PlayState.STOP;
		}

		PlayState playing = test.setAndContinue(clip);
		test.setControllerSpeed(speedFor(test, view.phaseTicksOf(entityId)));

		return playing;
	}

	/**
	 * How fast to play so the clip finishes exactly as the phase does.
	 *
	 * <p>The clip's length is read from the controller's timeline rather than declared
	 * alongside the mapping, so re-exporting an animation at a different length cannot
	 * silently leave a stale number behind. The timeline is null on the first frame of
	 * a new animation, since it is built after this handler returns — that one frame
	 * runs at normal speed, which is 16ms and not perceptible.
	 *
	 * <p>The controller's blend is subtracted, because the timeline counts it as a
	 * stage of its own. Without that the clip would be sped up to make room for the
	 * blend and would finish early, by a margin that grows as the phase gets shorter.
	 */
	private static float speedFor(AnimationTest<?> test, float phaseTicks) {
		if (phaseTicks <= 0.0F) {
			return 1.0F;
		}

		AnimationTimeline timeline = test.controller().getTimeline();

		if (timeline == null) {
			return 1.0F;
		}

		double clipTicks = timeline.totalTime() - test.controller().getTransitionTicks();

		return clipTicks <= 0.0 ? 1.0F : (float) (clipTicks / phaseTicks);
	}
}

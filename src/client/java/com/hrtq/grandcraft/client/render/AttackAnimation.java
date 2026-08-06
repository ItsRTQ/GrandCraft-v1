package com.hrtq.grandcraft.client.render;

import com.hrtq.grandcraft.client.animation.BedrockClip;
import com.hrtq.grandcraft.client.animation.GrandCraftAnimations;
import com.hrtq.grandcraft.client.animation.HumanoidClipPose;
import com.hrtq.grandcraft.combat.CombatState;
import com.hrtq.grandcraft.combat.WeaponCategory;
import net.minecraft.client.model.HumanoidModel;

/**
 * Picks the wind-up clip for what the player is holding and says how far into it they are.
 *
 * <p><strong>This is the file to edit when the wind-up plays at the wrong moment or holds
 * the wrong frame.</strong> The clips themselves are the animator's; how they are chosen
 * and timed is here. Sibling of {@link DodgeAnimation}, deliberately — the two solve the
 * same problem and should read the same way.
 *
 * <h2>The clips are wind-ups ONLY — user's ruling, 2026-08-05</h2>
 *
 * <p>This file's first version read each clip as a whole attack — raise, hold, fast strike
 * segment, return — and stretched it across all three phases with the strike's end on the
 * damage tick. <strong>The user has overruled that reading.</strong> The {@code wind_up_*}
 * clips belong to {@code ATTACK_STARTUP}; the strike proper will be a separate
 * {@code hit_<weapon>} delivery that does not exist yet. Until it does,
 * {@code ATTACK_ACTIVE} plays <em>the fast segment these clips already carry</em> —
 * the animator's own downswing — as the interim occupant of that slot ("the player hand
 * must go up and then down", user, 2026-08-05, after a hold-only active read as a
 * fake-out). When hit clips arrive they take the active phase over and the fallback
 * retires; the seam is {@link #seconds}.
 *
 * <p>What is <strong>never played</strong> is each clip's tail past {@link WindUp#strikeEnd}
 * — the return toward an end pose this rig renders badly. Recovery's fade does the
 * returning instead, and startup never samples before {@link WindUp#from}, where the sword
 * clip is authored standing at ease.
 *
 * <p>None of this moves damage. {@code PlayerAttack} deals the blow only while the server
 * says {@code ATTACK_ACTIVE} and {@code CombatController} cancels the attack when a stagger
 * lands in startup — this file draws the wind-up and decides nothing.
 *
 * <h2>Timing</h2>
 *
 * <p>Startup stretches the clip's authored approach — rest to {@code preparedAt} — over
 * however long the configured wind-up lasts, so the prepared pose is reached exactly when
 * the phase ends and a retuned startup needs no number here changed. Active and recovery
 * hold that frame, recovery fading it out; {@link #blend} is measured against the phase,
 * not the clip, so the arm is provably back on vanilla by the tick the player can act
 * again (the lesson the first version paid for — see {@link #blend}).
 *
 * <h2>Interruption</h2>
 *
 * <p>A stagger in startup cancels the attack server-side, the phase stops being an attack
 * phase, and {@code PlayerModelMixin}'s gate drops this pose the same frame — the stagger
 * posture takes over from vanilla's live pose. The hand-off is a cut, not a cross-fade:
 * every pose here is computed statelessly from the phase the render state carries, and a
 * fade out of an abandoned pose would need a memory of it that nothing keeps. Accepted for
 * the prototype; noted so nobody hunts for a blend that was never written.
 */
public final class AttackAnimation {
	/**
	 * One category's wind-up clip and the slice of it that is actually played.
	 *
	 * @param clip the name the animator gave it in Blockbench, used verbatim so a
	 *             re-export drops in with nothing to rename
	 * @param from seconds into the clip where playback <em>enters</em>. The sword and
	 *             dagger are authored from the model's limp rest pose — arms hanging at
	 *             the sides — and a player mid-fight is never in it: the swing visibly
	 *             collapsed to "standing at ease" before winding up, which is what
	 *             <em>"the animation is starting from the player resting position"</em>
	 *             (user, 2026-08-05) reported. Entering where the raise has already
	 *             left rest, with {@link #WIND_IN} carrying the gap from the player's
	 *             live stance, means the rest-like head of the clip is never shown.
	 * @param preparedAt seconds into the clip of the fully-prepared pose — the frame
	 *                   the wind-up tops out on and holds until the strike
	 * @param strikeEnd seconds into the clip where the fast segment — the animator's
	 *                  own downswing — has finished travelling. The active window
	 *                  plays {@code preparedAt → strikeEnd}, so the visible blow and
	 *                  the damage window are the same moment. Nothing past it is ever
	 *                  sampled: what remains in each clip is a return to an end pose
	 *                  this rig renders badly, and recovery's fade from the strike's
	 *                  end does the returning instead.
	 */
	private record WindUp(String clip, float from, float preparedAt, float strikeEnd) {
	}

	/**
	 * <strong>The names change between deliveries and only these four lines care.</strong>
	 * A clip that fails to resolve plays nothing at all — silently — so these are the first
	 * thing to check when a re-export makes the wind-up go still. Same trap
	 * {@code DodgeAnimation} records having been caught by once already.
	 *
	 * <h2>How each cutoff was chosen — and that it is a stand-in</h2>
	 *
	 * <p>The user's preferred shape is a delivery that <em>ends</em> on its prepared pose;
	 * these cutoffs are the sanctioned temporary alternative — an explicit end time per
	 * clip, centralised here, sampled up to and never past. Each is the last authored
	 * keyframe before the clip's fast segment, read off the right arm's keyframe times
	 * (the channel that defines all four motions), <em>not</em> off the largest rotation:
	 *
	 * <pre>
	 * dagger      chamber to 0.0833, held to 0.2917  → cut at 0.2917
	 * sword       raise to 0.5417, no hold           → cut at 0.5417
	 * greatsword  raise to 0.5, held to 0.5833       → cut at 0.5833
	 * staff       settle to 0.2083, held to 0.7083   → cut at 0.7083
	 * </pre>
	 *
	 * <p>Where a clip holds its prepared pose, the cutoff is the <em>end</em> of the hold:
	 * the pose is identical across it, and taking the longer span keeps the approach at
	 * the animator's proportions when startup stretches it.
	 *
	 * <p>These are seconds of the authored clip. Retuning a category's startup length does
	 * not need them changed; a re-export does.
	 */
	/**
	 * Entry points ({@code from}): where each clip's arm has visibly left rest. The
	 * sword's raise is ease-in and spends its first quarter second barely off the
	 * sides — entering at 0.25s picks it up around 32° of raise, which the wind-in
	 * fade covers from the item-holding stance. The greatsword and staff author their
	 * t=0 <em>in</em> a ready grip rather than at rest, so they enter at 0; the
	 * dagger's chamber arrives in two frames and has no rest-like head worth
	 * skipping.
	 */
	private static final WindUp DAGGER = new WindUp("wind_up_dagger", 0.0F, 0.2917F, 0.375F);
	private static final WindUp SWORD = new WindUp("wind_up_sword", 0.25F, 0.5417F, 0.625F);
	private static final WindUp GREATSWORD = new WindUp("wind_up_greatsword", 0.0F, 0.5833F, 0.6667F);
	private static final WindUp STAFF = new WindUp("wind_up_staff", 0.0F, 0.7083F, 1.0F);

	/**
	 * <strong>Experiment, 2026-08-05: mirror the arm channels — set false to restore
	 * the unmirrored read.</strong>
	 *
	 * <p>What runtime showed (four-screenshot sequence, {@code run/screenshots/}): the
	 * held pose put the hand low, backward and outward, sword flat behind the neck.
	 * What the user wants is the mirror image of exactly that — hand up, forward and
	 * across, the start of a {@code \} stroke. The suspected mechanism is the rig
	 * being built into the opposite x half of bedrock space (right arm at larger x,
	 * root spun 180° to display right) — {@link HumanoidClipPose}'s apply javadoc has
	 * the full argument and its honest limits. Judged at runtime only.
	 */
	private static final boolean MIRROR_ARMS = true;

	/**
	 * Fraction of startup by which the prepared pose is reached; the rest of the phase
	 * holds it.
	 *
	 * <p>These are downward strikes, so the telegraph that has to be on screen is the
	 * hand <em>up top</em>, ready to fall — and the authored raises spend most of their
	 * own time low: each approaches its peak with an ease-in, sweeping the hand through
	 * the bottom of the arc slowly and rising only at the end. Spread evenly over
	 * startup that read as <em>"the hand is starting off super low"</em> (user,
	 * 2026-08-05). Compressing the raise into the front of the phase turns the balance
	 * around: a quarter of the wind-up is motion, three quarters are the charged pose.
	 *
	 * <p>1.0 restores the even spread; the clips themselves are untouched either way.
	 */
	private static final float PREPARED_BY = 0.25F;

	/**
	 * Fraction of startup across which the pose fades <em>in</em> from vanilla's.
	 *
	 * <p>Absolute playback's one sharp edge: setting the clip's early pose outright
	 * <em>drops</em> the hand from vanilla's item-holding stance toward rest before
	 * raising it. Ramping the blend instead slides the arm from wherever vanilla holds
	 * it into the rising clip, so the wind-up's first visible motion is upward.
	 *
	 * <p><strong>Equal to {@link #PREPARED_BY} deliberately</strong>, and the pair with
	 * {@link WindUp#from} of one fix: the fade completes on the same frame the raise
	 * tops out, so the whole visible wind-up is one sweep from the player's live stance
	 * to the charged pose — at no point is the model standing at ease. A first cut ran
	 * the fade over 0.15 of startup, which on a short wind-up is three frames: not a
	 * blend, a snap, and the rest-like early clip pose showed through anyway.
	 *
	 * <p>(The dodge's declined wind-in ramp is a different question — those clips key
	 * their full pose at t=0 by design and the user liked the snap. These clips start
	 * at rest; the ramp here fixes a droop, not a snap into a pose.)
	 */
	private static final float WIND_IN = PREPARED_BY;

	/**
	 * <strong>Experiment, 2026-08-05: play the clip absolutely — set false to fall back
	 * to composing it over vanilla's pose.</strong>
	 *
	 * <p>The defect this targets: the arm clipping through the body, worse the further
	 * down the player looks. The stack underneath it is known —
	 * {@code setupAttackAnimation} throws vanilla's own procedural swing with a
	 * pitch-dependent drag ({@code api-facts.md} has the formula), and a clip
	 * <em>composed on top of it</em> renders as the product of two motions, never as
	 * drawn. Absolute mode makes the clip the whole rotation on body and both arms while
	 * it is in charge, and hands them back to vanilla's live pose across the recovery
	 * blend. What it deliberately does not overwrite — head, legs, positions — is
	 * reasoned through in {@link HumanoidClipPose}'s own javadoc.
	 *
	 * <p>Not the same as the rejected {@code attackTime} zeroing, which removed vanilla's
	 * swing but kept relative composition over the walk pose — the record of that is in
	 * {@code PlayerModelMixin}. Judged at runtime only, per the standing rule on render
	 * fixes.
	 */
	private static final boolean ABSOLUTE_CLIP = true;

	private AttackAnimation() {
	}

	/**
	 * Poses the rig for one frame of a wind-up, or leaves it alone if this actor is not
	 * attacking or is holding something with no authored wind-up.
	 *
	 * @param progress how far through the current phase, 0 to 1
	 * @param category what the actor is holding; null falls back to the procedural pose
	 */
	public static void apply(HumanoidModel<?> model, CombatState phase, float progress,
			WeaponCategory category) {
		WindUp windUp = windUpFor(category);

		if (!phase.isAttack() || windUp == null) {
			return;
		}

		BedrockClip clip = GrandCraftAnimations.clip(GrandCraftAnimations.PLAYER_ATTACK,
				windUp.clip());

		HumanoidClipPose.apply(model, clip, seconds(windUp, phase, progress),
				blend(phase, progress), ABSOLUTE_CLIP, MIRROR_ARMS);
	}

	/**
	 * Whether this category has an authored wind-up at all.
	 *
	 * <p><strong>RANGED and UNARMED do not</strong>, and nothing here stands in for them:
	 * they fall through to {@code HumanoidCombatPose}, the procedural posture that already
	 * draws every mob's attack phases. Borrowing a clip authored for a weapon that is not
	 * in the hand would be worse than a plain posture — the dagger clip on a bow reads as
	 * stabbing with it.
	 */
	public static boolean hasClip(WeaponCategory category) {
		return windUpFor(category) != null;
	}

	private static WindUp windUpFor(WeaponCategory category) {
		if (category == null) {
			return null;
		}

		return switch (category) {
			case LIGHT -> DAGGER;
			case MEDIUM -> SWORD;
			case HEAVY -> GREATSWORD;
			case ARCANE -> STAFF;
			case RANGED, UNARMED -> null;
		};
	}

	/**
	 * The clip time to draw this frame, in seconds — up and then down, phase by phase.
	 *
	 * <p>Startup runs entry → prepared across its first {@link #PREPARED_BY} and holds
	 * the charged pose for the remainder, so the telegraph on screen is mostly the hand
	 * up top rather than the low half of the raise. Active plays prepared → strike's
	 * end — the animator's own downswing, opening on the same tick the server starts
	 * looking for a target, so the weapon is seen falling as the blow lands. Recovery
	 * holds the strike's end while {@link #blend} fades it out.
	 *
	 * <p>The active mapping is the interim occupant of the {@code hit_<weapon>} slot:
	 * when dedicated hit clips arrive, they play here and the fast-segment fallback
	 * retires. The animation still decides nothing — damage timing is
	 * {@code PlayerAttack}'s and never moved.
	 */
	private static float seconds(WindUp windUp, CombatState phase, float progress) {
		float t = Math.clamp(progress, 0.0F, 1.0F);

		return switch (phase) {
			case ATTACK_STARTUP -> {
				float raised = Math.min(t / PREPARED_BY, 1.0F);

				yield windUp.from() + raised * (windUp.preparedAt() - windUp.from());
			}
			case ATTACK_ACTIVE ->
					windUp.preparedAt() + t * (windUp.strikeEnd() - windUp.preparedAt());
			default -> windUp.strikeEnd();
		};
	}

	/**
	 * How much of the authored pose is applied: faded in over the top of startup, all
	 * of it until the endlag, then handed back to vanilla across the whole of it.
	 *
	 * <p><strong>Measured against the phase, not against the clip.</strong> It was a clip
	 * fraction first, and that is the wrong thing to hang it on — <em>where the animator's
	 * last keyframe sits is not where the swing ends</em>, and a clip-fraction release
	 * once left part of an off-rest end pose still applied when the lockout ended, which
	 * is what was reported as the ending clipping through the player. Tied to recovery it
	 * cannot do that: recovery <em>is</em> the endlag, so the arm is provably on vanilla's
	 * own pose by the tick the player can act again, and the release scales with the
	 * endlag for free — a greatsword's long one gets a soft return, a dagger's short one
	 * a quick one.
	 *
	 * <p>The startup end fades in for the same reason in mirror — see {@link #WIND_IN}.
	 */
	private static float blend(CombatState phase, float progress) {
		float t = Math.clamp(progress, 0.0F, 1.0F);

		return switch (phase) {
			case ATTACK_STARTUP -> Math.min(t / WIND_IN, 1.0F);
			case ATTACK_RECOVERY -> 1.0F - t;
			default -> 1.0F;
		};
	}
}

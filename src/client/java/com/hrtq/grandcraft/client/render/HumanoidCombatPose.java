package com.hrtq.grandcraft.client.render;

import com.hrtq.grandcraft.combat.CombatController;
import com.hrtq.grandcraft.combat.CombatState;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.util.Ease;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;

/**
 * Draws a combat phase onto the shared humanoid rig.
 *
 * <p><strong>This is the file to edit when the telegraph does not read.</strong>
 * Everything else in the animation layer is plumbing; the whole look lives in the
 * three postures and the blend envelope below.
 *
 * <p>The attack is a two-armed slam: the arms open wide during the wind-up, as if
 * winding up for a hug, and clap together on the active frames. The wind-up is the
 * point — it is what an opponent has to read before deciding to dodge, block or
 * step out. A symmetric pose was chosen over a one-armed swing because it changes
 * the silhouette far more, so it stays legible at a distance, in a crowd, and from
 * any angle rather than only side-on.
 *
 * <h2>Two rules a future edit must not break</h2>
 * <ul>
 *   <li><strong>Nothing is applied at NEUTRAL, and recovery ends at zero blend.</strong>
 *       The pose is a weighted blend away from whatever vanilla already computed,
 *       not a replacement for it, so at blend zero the rig is bit-for-bit vanilla.
 *       That is what makes the hand-back at the end of recovery invisible; an
 *       absolute pose would snap.</li>
 *   <li><strong>Rotate the arms, not the body.</strong> In {@code HumanoidModel} the
 *       arms are siblings of the body rather than children, so a torso rotation
 *       leaves them hanging in the air and has to be paid for by moving each arm's
 *       position to match. This pose stays clear of that by keeping the torso still
 *       and expressing everything in the arms.</li>
 * </ul>
 *
 * <p>Written procedurally rather than as keyframes deliberately: these are short
 * mechanical phases whose lengths are configurable per actor, and a keyframe clip
 * has a fixed length. If a richer set of attacks ever wants authored animation,
 * only this file changes.
 */
public final class HumanoidCombatPose {
	/** How much of a stagger is spent snapping into the flinch before it settles. */
	private static final float FLINCH_IN = 0.25F;

	private HumanoidCombatPose() {
	}

	/**
	 * Which arm this actor would be blocking with, read off the render state.
	 *
	 * <p>Defers to {@link CombatController#guardHand} rather than re-deciding, so the
	 * arm that is drawn is by construction the arm the server charges wear to and
	 * applies the shield discount for. Getting that from the render state rather than
	 * over the wire also means it costs no packet and works for every player in view,
	 * not just the one holding the key.
	 *
	 * @return the guarding arm, or null when nothing in either hand can guard
	 */
	public static HumanoidArm guardArmOf(ArmedEntityRenderState state) {
		HumanoidArm main = state.mainArm;
		InteractionHand hand = CombatController.guardHand(
				state.getMainHandItemStack(),
				main == HumanoidArm.RIGHT ? state.leftHandItemStack : state.rightHandItemStack);

		if (hand == null) {
			return null;
		}

		return hand == InteractionHand.MAIN_HAND ? main : main.getOpposite();
	}

	/**
	 * One posture. Symmetric by construction — the two arms are never posed
	 * independently, which is what keeps this to four numbers.
	 *
	 * @param armXRot   arm raise; negative lifts forward and up, {@code -PI/2} is
	 *                  straight out in front
	 * @param armSpread how far apart the arms are swung; positive opens them,
	 *                  negative crosses them over each other
	 * @param armRoll   outward tilt, for width in the silhouette
	 * @param headXRot  head pitch, <em>added</em> to wherever the actor is looking
	 */
	private record Posture(float armXRot, float armSpread, float armRoll, float headXRot) {
	}

	/**
	 * End of the wind-up: arms thrown wide and slightly raised, head tipped back.
	 *
	 * <p>The widest silhouette in the set, and deliberately nothing like the idle —
	 * a zombie's resting arms are already forward, so opening them out is the
	 * clearest available signal that something is coming.
	 */
	private static final Posture OPEN = new Posture(-1.75F, 1.05F, 0.30F, -0.20F);

	/** The clap: arms driven together in front and slightly crossed, head snapped down. */
	private static final Posture SLAM = new Posture(-1.45F, -0.45F, -0.10F, 0.25F);

	/**
	 * Hit reaction. Arms thrown back and splayed, head snapped up — deliberately the
	 * inverse of the attack, because this is the cue that says an interrupt landed
	 * and it has to be told apart from a wind-up at a glance.
	 */
	private static final Posture FLINCH = new Posture(0.40F, 0.35F, 0.40F, -0.35F);

	/**
	 * How far forward the guarding arm comes.
	 *
	 * <p>Well short of the quarter turn that would hold it dead level. Straight out
	 * reads as pointing at something rather than guarding against it — a braced arm
	 * keeps a little bend out of the shoulder, and dropping the hand below the
	 * shoulder line is what suggests that on a rig with no elbow to bend.
	 */
	private static final float GUARD_ARM_X_ROT = (float) Math.toRadians(-65.0);

	/**
	 * How far the guarding arm swings in across the body, for a right arm; mirrored
	 * for a left one.
	 *
	 * <p>This is what puts the arm in front of the <em>torso</em> rather than out to
	 * the side of it. The shoulder sits a good way off the centre line, so an arm
	 * raised without this covers the air beside the actor and leaves the middle of the
	 * body open — which looks like presenting a weapon rather than hiding behind one.
	 *
	 * <p>Large, and it needs to be. The shoulder is far enough off centre that a
	 * modest swing still leaves the arm out in open air beside the body; only once it
	 * is well past halfway does it start to look tucked in against the chest.
	 *
	 * <p><strong>Negative is inward for an arm pitched forward like this</strong>, and
	 * that is the opposite of what {@link Posture#armSpread()} would suggest — it is
	 * negated for the right arm and opens the arms when positive. The first attempt
	 * here trusted that convention and swung the arm out to the side instead. The
	 * convention is not wrong so much as narrow: it describes arms near their resting
	 * pitch, where {@code yRot} barely moves them, and this one is a quarter turn away
	 * from that. Take the sign from what the rig does, not from the neighbouring
	 * constant.
	 */
	private static final float GUARD_ARM_Y_ROT = (float) Math.toRadians(-55.0);

	/**
	 * How far the guarding arm turns about its own length once it is out in front,
	 * for a right arm; mirrored for a left one. Thirty degrees, turned inward so the
	 * weapon lies across the line between the two fighters.
	 *
	 * <p>Applied as {@code zRot}, which is the outermost of the three rotations — a
	 * {@code ModelPart} composes them Z, then Y, then X — so it turns the arm as a
	 * whole after the pitch above has already aimed it forward.
	 *
	 * <p>Positive is outward, following {@link Posture#armRoll()}. Outward is right
	 * here because the arm is already swung hard in across the chest by
	 * {@link #GUARD_ARM_Y_ROT}: rolling it further inward on top of that folded the
	 * weapon back towards the actor instead of laying it across the front.
	 */
	private static final float GUARD_ARM_ROLL = (float) Math.toRadians(30.0);

	/**
	 * Poses the rig for one frame.
	 *
	 * <p>Takes the parts rather than the model so it is not tied to any one model
	 * class — the player's rig is the same three parts.
	 *
	 * @param progress how far through the phase, 0 to 1, interpolated between ticks
	 * @param guardArm the arm holding what the actor is blocking with, or null when it
	 *                 is not blocking with anything; only read for the guard phases
	 */
	public static void apply(ModelPart head, ModelPart rightArm, ModelPart leftArm,
			CombatState phase, float progress, HumanoidArm guardArm) {
		float blend = blend(phase, progress);

		if (blend <= 0.0F) {
			return;
		}

		// The guard is the one pose that is not symmetric, so it does not go through
		// Posture at all — see applyGuard.
		if (phase.isGuard()) {
			applyGuard(rightArm, leftArm, guardArm, blend);
			return;
		}

		Posture posture = posture(phase, progress);

		if (posture == null) {
			return;
		}

		rightArm.xRot = mix(rightArm.xRot, posture.armXRot(), blend);
		leftArm.xRot = mix(leftArm.xRot, posture.armXRot(), blend);

		// Signs follow vanilla's own convention in AnimationUtils.animateZombieArms,
		// where the resting arms sit at yRot -0.1 on the right and +0.1 on the left:
		// negative on the right is outward, and its own attack closes them by driving
		// that value positive.
		rightArm.yRot = mix(rightArm.yRot, -posture.armSpread(), blend);
		leftArm.yRot = mix(leftArm.yRot, posture.armSpread(), blend);

		rightArm.zRot = mix(rightArm.zRot, posture.armRoll(), blend);
		leftArm.zRot = mix(leftArm.zRot, -posture.armRoll(), blend);

		// Added rather than blended: the head is already aimed at whatever the actor
		// is looking at, and that aim must survive the pose.
		head.xRot += posture.headXRot() * blend;
	}

	/**
	 * How strongly the posture overrides vanilla, from 0 (untouched) to 1 (fully
	 * posed).
	 *
	 * <p>This is where the readability lives. The wind-up rises quickly and then
	 * holds, so the arms spend most of the phase at full spread where they can be
	 * seen, rather than easing through it and only being open at the last instant.
	 * Recovery is the mirror: a slow release that lands on exactly zero.
	 */
	private static float blend(CombatState phase, float progress) {
		float t = Math.clamp(progress, 0.0F, 1.0F);

		return switch (phase) {
			// Reaches the held shape early. A linear ramp would spend the whole
			// wind-up in transit and the pose would never be legible before the hit.
			case ATTACK_STARTUP -> Ease.outCubic(t);
			case ATTACK_ACTIVE -> 1.0F;
			case ATTACK_RECOVERY -> 1.0F - Ease.inOutCubic(t);
			case STAGGERED -> t < FLINCH_IN
					? Ease.outCubic(t / FLINCH_IN)
					: 1.0F - Ease.inCubic((t - FLINCH_IN) / (1.0F - FLINCH_IN));
			// A dodge is a rotation of the whole body rather than a pose of its arms,
			// so it is drawn elsewhere and this leaves the rig alone. A tuck — arms in,
			// legs drawn up — belongs here once the roll rotation exists to tuck into.
			case NEUTRAL, DODGE_ACTIVE, DODGE_RECOVERY -> 0.0F;
			// Same envelope as the attack: up quickly so the stance is legible for
			// almost the whole raise, and released slowly onto exactly zero.
			case GUARD_RAISE -> Ease.outCubic(t);
			// Flat, and it must stay flat. A held guard is announced in renewing
			// leases, so its progress runs 0 to 1 over and over for as long as it is
			// up — reading it here would make the stance pulse once a second.
			case GUARDING -> 1.0F;
			case GUARD_RECOVERY -> 1.0F - Ease.inOutCubic(t);
		};
	}

	/** The posture being blended towards, or null when the phase has none. */
	private static Posture posture(CombatState phase, float progress) {
		float t = Math.clamp(progress, 0.0F, 1.0F);

		return switch (phase) {
			case ATTACK_STARTUP -> OPEN;
			// The clap itself. Front-loaded so the arms cover most of the distance in
			// the first of the two active ticks, which is what puts the impact of the
			// animation on the tick that actually deals the damage.
			case ATTACK_ACTIVE -> lerp(OPEN, SLAM, Ease.outQuart(t));
			// Held while the blend falls away, which is what "arms drift back" is.
			case ATTACK_RECOVERY -> SLAM;
			case STAGGERED -> FLINCH;
			// See blend(): the dodge phases are drawn as a body rotation, not here.
			case NEUTRAL, DODGE_ACTIVE, DODGE_RECOVERY -> null;
			// The guard has no Posture: it is asymmetric and handled before this is
			// ever reached. See applyGuard.
			case GUARD_RAISE, GUARDING, GUARD_RECOVERY -> null;
		};
	}

	/**
	 * Raises the one arm that is doing the blocking, and leaves everything else alone.
	 *
	 * <p>The only asymmetric pose in the file, and deliberately outside {@link Posture}
	 * rather than a widening of it. Every other posture here is a whole-body shape
	 * where symmetry is the point — it is what makes an attack read from any angle. A
	 * guard is the opposite kind of thing: it is <em>an object held in a hand</em>, and
	 * which hand is the entire content of the pose. Generalising Posture to carry two
	 * of every value would have made the four attack numbers eight to serve this one
	 * case.
	 *
	 * <p>Nothing but that arm moves. An earlier symmetric version brought both arms up
	 * and read unmistakably as a hug — the body language of blocking is one limb
	 * interposed, not two arms closed.
	 *
	 * <p>A null arm draws nothing rather than guessing. It means the item left the
	 * hand, which the server also treats as the end of the guard, so the pose is
	 * about to be retracted anyway.
	 */
	private static void applyGuard(ModelPart rightArm, ModelPart leftArm,
			HumanoidArm guardArm, float blend) {
		if (guardArm == null) {
			return;
		}

		boolean right = guardArm == HumanoidArm.RIGHT;
		ModelPart arm = right ? rightArm : leftArm;

		// Both sideways values are mirrored for a left arm, the same way the symmetric
		// postures mirror theirs, so "inward" means inward on either side.
		float mirror = right ? 1.0F : -1.0F;

		arm.xRot = mix(arm.xRot, GUARD_ARM_X_ROT, blend);
		arm.yRot = mix(arm.yRot, GUARD_ARM_Y_ROT * mirror, blend);
		arm.zRot = mix(arm.zRot, GUARD_ARM_ROLL * mirror, blend);
	}

	private static Posture lerp(Posture from, Posture to, float t) {
		return new Posture(
				mix(from.armXRot(), to.armXRot(), t),
				mix(from.armSpread(), to.armSpread(), t),
				mix(from.armRoll(), to.armRoll(), t),
				mix(from.headXRot(), to.headXRot(), t));
	}

	private static float mix(float from, float to, float t) {
		return from + (to - from) * t;
	}
}

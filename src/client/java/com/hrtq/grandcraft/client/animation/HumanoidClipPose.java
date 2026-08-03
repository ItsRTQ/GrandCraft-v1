package com.hrtq.grandcraft.client.animation;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Draws an authored clip onto vanilla's humanoid rig.
 *
 * <p><strong>This is the file to edit when a clip plays but the body is wrong.</strong>
 * {@link BedrockClipLoader} decides what the numbers mean; this decides which part
 * they land on.
 *
 * <h2>The two rigs do not have the same skeleton</h2>
 *
 * The animator works on a spine chain — {@code lower body}, {@code torso} and
 * {@code Upperbody}, with the head and both arms hanging off the chest and the legs
 * off the hips. Vanilla has no spine at all: {@code head}, {@code body},
 * {@code rightArm}, {@code leftArm} and the two legs are all <em>siblings</em> of one
 * root, and {@code body} is a separate cuboid that the legs are not attached to.
 *
 * <p>Three rules follow from that, and all three were paid for in a runtime test:
 *
 * <h3>1. Yaw is a whole-body property here, never a torso one</h3>
 *
 * On the animator's rig a chest turn carries the arms and head around the body with
 * it. On vanilla the arms are siblings, so the same turn has to be paid for by
 * swinging their pivots — and a large {@code yRot} on an arm near its resting pitch
 * is very nearly a pure <em>twist</em> rather than a swing. The first version did
 * exactly that and the side dodges came out with the arms spinning about their own
 * length. The back dodge was fine, and the reason is instructive: it is the one clip
 * with no yaw in it at all.
 *
 * <p>So yaw leaves through {@link #wholeBodyYRot} and goes onto the {@code PoseStack},
 * where it turns the actor as one piece and needs no compensation from anybody. What
 * is left on {@code body} is pitch and roll, which are small, well behaved, and what
 * made the back dodge read.
 *
 * <p><strong>How that split is taken is the whole game — see {@link #swing}.</strong>
 * The first version took it by zeroing the Y term of an Euler decomposition, which is
 * not the same operation and cost a second runtime test.
 *
 * <p>The cost is that the legs turn with the chest, which on the real rig they would
 * not. That is the honest trade: vanilla has no waist to twist, and arms that orbit
 * correctly are worth more than legs that stay pointed the old way.
 *
 * <h3>2. The torso cannot translate</h3>
 *
 * The spine's position channels are dropped on the floor. On a rig whose torso is
 * welded to its hips they lean the upper body; on vanilla they slide a loose cuboid
 * off the top of the legs, which is what the forward dive did — it throws the chest
 * eleven pixels, and eleven pixels is most of the model's depth. The dive reads
 * through its -67° pitch instead, which is what was carrying it anyway.
 *
 * <p>Limb translations <em>are</em> kept. Those are small, and vanilla moves the same
 * parts the same way itself — it slides the legs back when sneaking and the arms
 * around when the body turns.
 *
 * <h3>3. Rotations compose; they do not add</h3>
 *
 * See {@link #compose}.
 */
public final class HumanoidClipPose {
	private static final String WAIST = "torso";
	private static final String CHEST = "Upperbody";
	private static final String HEAD = "head";
	private static final String RIGHT_ARM = "right arm";
	private static final String LEFT_ARM = "left arm";
	private static final String RIGHT_LEG = "right leg";
	private static final String LEFT_LEG = "left leg";

	/**
	 * Where the shoulders sit relative to the chest's origin, from
	 * {@code HumanoidModel.createMesh}: {@code offset(±5, 2 + yOffset, 0)}, and the
	 * player's mesh passes a {@code yOffset} of zero.
	 *
	 * <p>Hard-coded because {@code PartPose} keeps its fields private and exposes no
	 * getters, so {@code ModelPart.getInitialPose()} hands back something unreadable —
	 * and reading {@code part.x} at this point would sample whatever vanilla's own
	 * {@code setupAnim} left there, not the rest pivot.
	 */
	private static final float ARM_PIVOT_X = 5.0F;
	private static final float ARM_PIVOT_Y = 2.0F;

	private HumanoidClipPose() {
	}

	/**
	 * Poses the rig for one frame of a clip, yaw excepted — see {@link #wholeBodyYRot}
	 * for that half, which the caller has to put on the {@code PoseStack}.
	 *
	 * @param seconds time into the clip
	 * @param blend   how strongly to apply it, 0 to 1; 1 is the pose as authored
	 */
	public static void apply(HumanoidModel<?> model, BedrockClip clip, float seconds, float blend) {
		if (clip.isEmpty() || blend <= 0.0F) {
			return;
		}

		BonePose sample = new BonePose();

		// The chest, with its turn taken out — that has gone to the whole actor, and
		// leaving it here as well would turn the torso twice.
		Quaternionf chest = swing(spine(clip, seconds, sample));

		compose(model.body, chest, blend);

		// Under the chest. The hat and the sleeve overlays are children of these parts
		// in the mesh, so they follow without being named.
		child(model.head, clip, HEAD, seconds, chest, 0.0F, 0.0F, blend, sample);
		child(model.rightArm, clip, RIGHT_ARM, seconds, chest,
				-ARM_PIVOT_X, ARM_PIVOT_Y, blend, sample);
		child(model.leftArm, clip, LEFT_ARM, seconds, chest,
				ARM_PIVOT_X, ARM_PIVOT_Y, blend, sample);

		// Under the hips, which are not part of the model.
		direct(model.rightLeg, clip, RIGHT_LEG, seconds, blend, sample);
		direct(model.leftLeg, clip, LEFT_LEG, seconds, blend, sample);
	}

	/**
	 * Every turn the clip asks for, in radians, in {@code ModelPart}'s sense — positive
	 * turns the actor to its right.
	 *
	 * <p>This is the spine's own turn, taken as the twist half of {@link #swing}, so it
	 * and the pose left on {@code body} are two halves of one exact split rather than
	 * two independent readings that have to be trusted to agree.
	 *
	 * <p><strong>The hips are deliberately not sampled.</strong> They are authored as a
	 * sibling holding the same angle as the chest, not as a parent it hangs off: the
	 * left dodge turns the hips -42.5° against a spine twist of -40.0°, and the right
	 * turns them +52.1° against +55.0°. Two bones landing within three degrees of each
	 * other, twice, is an animator turning both halves of a torso <em>to</em> an angle.
	 * Composing them as a chain would instead give -82.5° and +72.8° — a spin rather
	 * than a sidestep, and it puts roll back on the body, which is the thing this class
	 * exists to keep off it.
	 *
	 * <p>So the hips' turn is redundant with the spine's and its remaining channels are
	 * noise: pitch and roll never exceed about a degree in these clips, and its
	 * translation is not worth applying on top of the real displacement the dodge
	 * impulse already produces.
	 */
	public static float wholeBodyYRot(BedrockClip clip, float seconds) {
		if (clip.isEmpty()) {
			return 0.0F;
		}

		Quaternionf twist = twist(spine(clip, seconds, new BonePose()));

		// The signed angle about Y that this twist represents. Reading it back off the
		// quaternion rather than out of an Euler decomposition, for the same reason the
		// split itself is not an Euler operation.
		return 2.0F * (float) Math.atan2(twist.y, twist.w);
	}

	/**
	 * The two spine bones composed into one rotation.
	 *
	 * <p>Rotations multiply where offsets would add, but the offsets are dropped
	 * anyway — see the class notes on why a vanilla torso cannot translate.
	 */
	private static Quaternionf spine(BedrockClip clip, float seconds, BonePose sample) {
		Quaternionf spine = new Quaternionf();

		clip.sample(WAIST, seconds, sample);
		spine.mul(quaternionOf(sample.rotation));

		clip.sample(CHEST, seconds, sample);
		spine.mul(quaternionOf(sample.rotation));

		return spine;
	}

	/**
	 * The part of a rotation that is <em>not</em> a turn about the vertical — what is
	 * left for {@code body} once {@link #wholeBodyYRot} has taken the turn away.
	 *
	 * <p><strong>Zeroing the Y component of an Euler decomposition does not do this,
	 * and believing it did cost a runtime test.</strong> Euler angles are not three
	 * independent contributions that can be removed one at a time; they are three
	 * rotations applied in an order. Take a chest pitched 20° and turned 40° — which is
	 * exactly the left dodge — decompose it Z-Y-X and blank the Y, and what comes back
	 * is pitched <em>25.4°</em> and rolled <em>-16.0°</em>. The clip contains no roll on
	 * any spine bone in any direction; all sixteen degrees of it were manufactured by
	 * the arithmetic. On the right dodge it was twenty. A torso cuboid rolled that far
	 * from a pivot at the hips, with the legs left where they were, is what was reported
	 * as the body detaching sideways — and the back dodge escaped only because it is the
	 * one clip with no turn in it for the decomposition to smear.
	 *
	 * <p>The operation that <em>is</em> wanted is a swing-twist decomposition: split
	 * {@code q} into a rotation purely about Y (the twist) and whatever remains (the
	 * swing), such that composing the two reproduces {@code q} exactly. The twist is
	 * built by keeping only the quaternion's Y and W terms and renormalising, and the
	 * swing is then {@code q} with that twist divided back out. Run over the same left
	 * dodge it returns the authored numbers to the digit — 20.00° of pitch, 0.00° of
	 * roll, and a 40.00° turn — which is the check to re-run if a future clip looks
	 * wrong here.
	 */
	private static Quaternionf swing(Quaternionf q) {
		return new Quaternionf(q).mul(twist(q).conjugate());
	}

	/**
	 * The turn-about-vertical half of {@code q}. See {@link #swing} for why the split is
	 * taken this way.
	 *
	 * <p>Degenerate when the rotation is a half turn about a horizontal axis, where the
	 * Y and W terms both vanish and the twist is genuinely undefined. Nothing in a dodge
	 * comes near that — it would be a body folded flat — and the identity is the right
	 * answer for it anyway, since a rotation with no vertical component has no turn to
	 * take out.
	 */
	private static Quaternionf twist(Quaternionf q) {
		Quaternionf twist = new Quaternionf(0.0F, q.y, 0.0F, q.w);

		return twist.lengthSquared() < 1.0E-6F ? new Quaternionf() : twist.normalize();
	}

	private static void child(ModelPart part, BedrockClip clip, String bone, float seconds,
			Quaternionf chest, float pivotX, float pivotY, float blend, BonePose sample) {
		clip.sample(bone, seconds, sample);

		// Where this part's pivot ends up once the chest has tipped, minus where it
		// started — i.e. how far the shoulder travelled and the arm has to travel with
		// it. Zero for the head, whose pivot is the chest's own origin.
		Vector3f offset = new Vector3f(pivotX, pivotY, 0.0F);

		chest.transform(offset);
		offset.sub(pivotX, pivotY, 0.0F).add(sample.position);

		compose(part, new Quaternionf(chest).mul(quaternionOf(sample.rotation)), blend);
		addPosition(part, offset, blend);
	}

	private static void direct(ModelPart part, BedrockClip clip, String bone, float seconds,
			float blend, BonePose sample) {
		clip.sample(bone, seconds, sample);

		if (sample.isRest()) {
			return;
		}

		compose(part, quaternionOf(sample.rotation), blend);
		addPosition(part, sample.position, blend);
	}

	/**
	 * Builds the rotation a {@code ModelPart} would produce from those three angles.
	 *
	 * <p>Z then Y then X, matching {@code ModelPart}'s own composition order — it
	 * multiplies zRot, then yRot, then xRot onto the stack, which leaves xRot innermost
	 * and applied first. JOML spells that same order {@code rotationZYX}.
	 */
	private static Quaternionf quaternionOf(Vector3f euler) {
		return new Quaternionf().rotationZYX(euler.z, euler.y, euler.x);
	}

	/**
	 * Lays a rotation over whatever the part already has.
	 *
	 * <p><strong>Composed, not added.</strong> The neighbouring procedural poses layer
	 * themselves by summing Euler components onto the part's existing ones, and that is
	 * fine for what they do — a single axis, a few tens of degrees. It is not fine
	 * here. Summing angles is not composing rotations, the error grows with both the
	 * magnitude and the number of axes involved, and the side dodges are the worst case
	 * on both counts: a big turn and a lean at the same time, over whatever vanilla's
	 * walk cycle had already put on the arms. Reading the part's rotation back into a
	 * quaternion, multiplying, and decomposing costs a few operations a frame and is
	 * exact.
	 *
	 * <p>The blend is a slerp from the identity, which is the rotation-shaped way to
	 * say "half of this pose" — scaling the Euler components would reintroduce the
	 * approximation this method exists to avoid.
	 */
	private static void compose(ModelPart part, Quaternionf rotation, float blend) {
		Quaternionf pose = blend >= 1.0F
				? rotation
				: new Quaternionf().slerp(rotation, blend);

		// Into a fresh quaternion rather than in place: the chest is composed onto the
		// body and then onto all three of its children, so mutating it here would leave
		// the arms wearing the torso's pose twice over.
		Vector3f euler = pose
				.mul(new Quaternionf().rotationZYX(part.zRot, part.yRot, part.xRot),
						new Quaternionf())
				.getEulerAnglesZYX(new Vector3f());

		part.xRot = euler.x;
		part.yRot = euler.y;
		part.zRot = euler.z;
	}

	private static void addPosition(ModelPart part, Vector3f offset, float blend) {
		part.x += offset.x * blend;
		part.y += offset.y * blend;
		part.z += offset.z * blend;
	}
}

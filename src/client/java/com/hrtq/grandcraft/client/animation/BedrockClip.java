package com.hrtq.grandcraft.client.animation;

import java.util.Map;
import org.joml.Vector3f;

/**
 * One authored animation clip, sampled by time.
 *
 * <p>The runtime half of the Blockbench pipeline for actors on the <em>vanilla</em>
 * rig. Custom mobs go through GeckoLib, which owns its own bones and its own
 * renderer; the player cannot, because it is drawn by {@code AvatarRenderer} onto
 * {@code PlayerModel}. So a clip meant for a player has to be sampled by us and
 * written onto {@code ModelPart}s — which is all this class does.
 *
 * <p>Everything here is already in vanilla's conventions. The Blockbench axis
 * conversion is done once, at load; see {@link BedrockClipLoader}.
 *
 * <h2>Interpolation follows the keyframe it arrives at</h2>
 * This file used to interpolate everything linearly, with a note saying that the day a
 * clip arrived using {@code easing} or {@code lerp_mode}, this was the file that had to
 * grow a spline. The attack delivery (2026-08-05) is that clip — 58 eased keyframes and
 * six {@code catmullrom} ones — so it grew one.
 *
 * <p><strong>A keyframe's curve shapes the segment that <em>ends</em> at it</strong>,
 * not the one that leaves it. That is not a guess: across all four attack clips the
 * keyframe at {@code t=0} is the only one that never carries an {@code easing}, which is
 * exactly the keyframe with no incoming segment. Under the opposite reading the *last*
 * key would be the bare one instead. So sampling between two keys uses the later key's
 * {@link Interpolation}.
 *
 * <p>The dodge clips carry neither field, so every one of their channels resolves to
 * {@link Interpolation#LINEAR} and they play exactly as they did before.
 */
public final class BedrockClip {
	/** What a missing clip samples to: nothing, everywhere. */
	public static final BedrockClip EMPTY = new BedrockClip(0.0F, Map.of());

	private final float length;
	private final Map<String, Bone> bones;

	BedrockClip(float length, Map<String, Bone> bones) {
		this.length = length;
		this.bones = bones;
	}

	/** One bone's three channels. A null channel means the bone never moves that way. */
	record Bone(Channel rotation, Channel position, Channel scale) {
	}

	/**
	 * How a segment gets from one keyframe to the next.
	 *
	 * <p>Only what the shipped clips actually use. Blockbench offers a few dozen easing
	 * functions; naming them all here would be writing code against clips that do not
	 * exist, and an unknown one already falls back to {@link #LINEAR} in the loader.
	 */
	enum Interpolation {
		LINEAR,
		EASE_IN_SINE,
		EASE_IN_QUAD,
		EASE_IN_CUBIC,
		EASE_OUT_QUAD,
		EASE_IN_OUT_CUBIC,

		/**
		 * The one easing here that leaves its own lane: it dips <em>below</em> zero
		 * early, so the segment first travels a little away from its destination before
		 * turning round and arriving fast.
		 *
		 * <p>That overshoot is the point, not a defect. The greatsword's downswing keys
		 * it on the strike (2026-08-07 delivery), where it reads as the arms loading a
		 * fraction further back before the blow — the anticipation a heavy swing wants.
		 * A value between two keyframes therefore leaves the range they span, which is
		 * safe here because every consumer slerps or lerps rather than indexing.
		 */
		EASE_IN_BACK,

		/**
		 * A smooth curve through the surrounding keys rather than a straight line
		 * between two of them. Unlike the easings this reads the neighbouring
		 * keyframes, so it is applied by the channel rather than by reshaping {@code t}.
		 */
		CATMULLROM;

		/** How far {@link #EASE_IN_BACK} travels the wrong way first. */
		private static final float BACK_OVERSHOOT = 1.70158F;
		private static final float BACK_OVERSHOOT_CUBIC = BACK_OVERSHOOT + 1.0F;

		/**
		 * Reshapes progress through a segment. An easing bends time and leaves the
		 * endpoints alone — 0 stays 0 and 1 stays 1 — which is what lets it be applied
		 * before an ordinary lerp.
		 */
		float shape(float t) {
			return switch (this) {
				case EASE_IN_SINE -> 1.0F - (float) Math.cos(t * Math.PI / 2.0);
				case EASE_IN_QUAD -> t * t;
				case EASE_IN_CUBIC -> t * t * t;
				case EASE_OUT_QUAD -> t * (2.0F - t);
				case EASE_IN_OUT_CUBIC -> t < 0.5F
						? 4.0F * t * t * t
						: 1.0F - (float) Math.pow(-2.0 * t + 2.0, 3) / 2.0F;
				// Blockbench's own constants: 1.70158 is the overshoot, and the cubic
				// term is one more than it so the curve still lands exactly on 1.
				case EASE_IN_BACK -> BACK_OVERSHOOT_CUBIC * t * t * t - BACK_OVERSHOOT * t * t;
				default -> t;
			};
		}
	}

	/**
	 * One channel of keyframes: times in seconds, three floats of value per time, and
	 * how the segment ending at each one is shaped.
	 *
	 * <p>Stored as flat arrays rather than a list of objects because this is sampled
	 * once per bone per channel per frame, and the whole point of doing the axis
	 * conversion at load time is that sampling stays free of allocation.
	 */
	static final class Channel {
		private final float[] times;
		private final float[] values;
		private final Interpolation[] curves;

		Channel(float[] times, float[] values, Interpolation[] curves) {
			this.times = times;
			this.values = values;
			this.curves = curves;
		}

		/**
		 * Writes the value at {@code seconds} into {@code out}, holding the first and
		 * last keys outside the clip rather than extrapolating past them.
		 */
		void sample(float seconds, Vector3f out) {
			int count = this.times.length;

			if (count == 0) {
				return;
			}

			if (seconds <= this.times[0]) {
				this.read(0, out);
				return;
			}

			if (seconds >= this.times[count - 1]) {
				this.read(count - 1, out);
				return;
			}

			// Linear scan rather than a binary search: these clips have two or three
			// keys per channel, and the loop wins outright at that size.
			int next = 1;

			while (next < count && this.times[next] < seconds) {
				next++;
			}

			int previous = next - 1;
			float span = this.times[next] - this.times[previous];
			float t = span <= 0.0F ? 0.0F : (seconds - this.times[previous]) / span;

			// The later key's curve, because a curve shapes the segment arriving at its
			// own keyframe — see the class notes for the evidence.
			Interpolation curve = this.curves[next];

			int a = previous * 3;
			int b = next * 3;

			if (curve == Interpolation.CATMULLROM) {
				// Needs the keys either side of the segment as well, so it cannot go
				// through shape(). Clamped at the ends: the first and last keys stand in
				// for the neighbours they do not have, which makes the spline flatten
				// into the clip's boundaries rather than overshoot out of them.
				int before = Math.max(previous - 1, 0) * 3;
				int after = Math.min(next + 1, count - 1) * 3;

				out.set(
						catmullRom(this.values[before], this.values[a],
								this.values[b], this.values[after], t),
						catmullRom(this.values[before + 1], this.values[a + 1],
								this.values[b + 1], this.values[after + 1], t),
						catmullRom(this.values[before + 2], this.values[a + 2],
								this.values[b + 2], this.values[after + 2], t));
				return;
			}

			float shaped = curve.shape(t);

			out.set(
					lerp(this.values[a], this.values[b], shaped),
					lerp(this.values[a + 1], this.values[b + 1], shaped),
					lerp(this.values[a + 2], this.values[b + 2], shaped));
		}

		/** The standard uniform Catmull-Rom basis, on the segment {@code p1} to {@code p2}. */
		private static float catmullRom(float p0, float p1, float p2, float p3, float t) {
			float t2 = t * t;
			float t3 = t2 * t;

			return 0.5F * ((2.0F * p1)
					+ (p2 - p0) * t
					+ (2.0F * p0 - 5.0F * p1 + 4.0F * p2 - p3) * t2
					+ (3.0F * p1 - p0 - 3.0F * p2 + p3) * t3);
		}

		private void read(int index, Vector3f out) {
			int base = index * 3;

			out.set(this.values[base], this.values[base + 1], this.values[base + 2]);
		}

		private static float lerp(float from, float to, float t) {
			return from + (to - from) * t;
		}
	}

	/** How long the clip runs, in seconds, as the animator authored it. */
	public float length() {
		return this.length;
	}

	public boolean isEmpty() {
		return this.bones.isEmpty();
	}

	/** Whether this clip touches that bone at all. */
	public boolean has(String bone) {
		return this.bones.containsKey(bone);
	}

	/**
	 * Samples one bone into {@code out}, which is reset first — so a bone the clip
	 * never mentions, or a channel it never keys, comes back as the rest pose rather
	 * than as whatever the caller last sampled.
	 *
	 * @param seconds time into the clip; outside it, the nearest key is held
	 */
	public void sample(String bone, float seconds, BonePose out) {
		out.reset();

		Bone track = this.bones.get(bone);

		if (track == null) {
			return;
		}

		if (track.rotation() != null) {
			track.rotation().sample(seconds, out.rotation);
		}

		if (track.position() != null) {
			track.position().sample(seconds, out.position);
		}

		if (track.scale() != null) {
			track.scale().sample(seconds, out.scale);
		}
	}
}

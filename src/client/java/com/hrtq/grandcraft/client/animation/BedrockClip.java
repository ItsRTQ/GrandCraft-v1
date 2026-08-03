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
 * <h2>Interpolation is linear, deliberately</h2>
 * Blockbench can export {@code lerp_mode: catmullrom}, and nothing here honours it —
 * a smooth channel would be sampled as straight segments between its keys. None of
 * the dodge clips use it. If a future clip does, this is the file that has to grow a
 * spline rather than the file that silently looks wrong, so it is worth knowing.
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
	 * One channel of keyframes: times in seconds, three floats of value per time.
	 *
	 * <p>Stored as flat arrays rather than a list of objects because this is sampled
	 * once per bone per channel per frame, and the whole point of doing the axis
	 * conversion at load time is that sampling stays free of allocation.
	 */
	static final class Channel {
		private final float[] times;
		private final float[] values;

		Channel(float[] times, float[] values) {
			this.times = times;
			this.values = values;
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

			int a = previous * 3;
			int b = next * 3;

			out.set(
					lerp(this.values[a], this.values[b], t),
					lerp(this.values[a + 1], this.values[b + 1], t),
					lerp(this.values[a + 2], this.values[b + 2], t));
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

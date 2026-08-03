package com.hrtq.grandcraft.client.animation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Reads a Blockbench "bedrock" animation file into {@link BedrockClip}s.
 *
 * <p>Gson rather than a Codec, following {@code WeaponConfigFile}. The format is
 * awkwardly polymorphic in three separate places — a channel is either a static
 * vector or a map of keyframes, a keyframe is either a bare array or an object with
 * a {@code vector}, and a component is either a number or a MoLang string — and each
 * of those would need an {@code Either} with a hand-written codec on both sides.
 * There is nothing to serialise back out, so the round trip a Codec buys is not
 * worth the reading.
 *
 * <h2>The axis conversion, which is the whole reason this class exists</h2>
 *
 * Blockbench's bedrock space and vanilla's {@code ModelPart} space differ by a half
 * turn about X: Blockbench measures Y <em>up</em> from the ground and Z
 * <em>forwards</em>, while a {@code ModelPart} measures Y <em>down</em> from its
 * pivot and Z <em>backwards</em>. So
 *
 * <pre>x_model = x, y_model = -y, z_model = -z</pre>
 *
 * That is a proper rotation, not a mirror, so rotation vectors take exactly the same
 * signs — {@code xRot = radians(x)}, {@code yRot = -radians(y)},
 * {@code zRot = -radians(z)}.
 *
 * <p><strong>Do not copy GeckoLib's conversion here.</strong> It negates X and Y on
 * rotation and X on the pivot, which looks like the answer and is not: GeckoLib
 * converts into <em>its own</em> bone space, which is X-flipped relative to both
 * Blockbench and {@code ModelPart}, and it flips back again at render time in
 * {@code RenderUtils}. Its signs are only correct in company with the rest of its
 * pipeline.
 *
 * <p>The signs above were checked against the dodge clips themselves, which is the
 * evidence to re-run if a future clip looks inside out:
 * <ul>
 *   <li>The forward dive pitches {@code Upperbody} to <strong>-67°</strong> and the
 *       backstep pitches it to <strong>+30°</strong>. So positive X is a lean
 *       <em>backwards</em> — which is also what positive {@code xRot} does to a
 *       {@code ModelPart}, hence no negation on X.</li>
 *   <li>In that same dive the head is keyed to <strong>+55°</strong> against a chest
 *       at -67°, leaving the head roughly level with the world. That only lands level
 *       if the head is a <em>child</em> of the chest, which is what
 *       {@link HumanoidClipPose} assumes.</li>
 *   <li>The left dodge turns the hips <strong>+42.5°</strong> and the right dodge
 *       turns them <strong>-52°</strong>. So positive Y is a turn to the actor's
 *       left, and vanilla yaw grows to the right — hence the negation on Y.</li>
 * </ul>
 */
public final class BedrockClipLoader {
	/**
	 * Blockbench writes rotations in degrees; a {@code ModelPart} wants radians.
	 * Positions need no scale factor — both sides count in model units.
	 */
	private static final float DEGREES_TO_RADIANS = (float) (Math.PI / 180.0);

	private BedrockClipLoader() {
	}

	/**
	 * Parses every clip in one file, keyed by the name the animator gave it.
	 *
	 * <p>Clip names are kept verbatim rather than normalised, so a re-export from
	 * Blockbench drops in without anyone having to rename anything inside it. Mapping a
	 * name to what it means is the caller's job — for the dodge, {@code DodgeAnimation}'s.
	 *
	 * @throws com.google.gson.JsonParseException if the file is not a JSON object
	 */
	public static Map<String, BedrockClip> parse(BufferedReader reader) {
		JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
		Map<String, BedrockClip> clips = new HashMap<>();

		if (!root.has("animations")) {
			return clips;
		}

		for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("animations").entrySet()) {
			if (entry.getValue().isJsonObject()) {
				clips.put(entry.getKey(), clip(entry.getValue().getAsJsonObject()));
			}
		}

		return clips;
	}

	private static BedrockClip clip(JsonObject json) {
		float length = json.has("animation_length")
				? json.get("animation_length").getAsFloat()
				: 0.0F;

		Map<String, BedrockClip.Bone> bones = new HashMap<>();

		if (json.has("bones")) {
			for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("bones").entrySet()) {
				if (!entry.getValue().isJsonObject()) {
					continue;
				}

				JsonObject bone = entry.getValue().getAsJsonObject();

				bones.put(entry.getKey(), new BedrockClip.Bone(
						channel(bone.get("rotation"), Axis.ROTATION),
						channel(bone.get("position"), Axis.POSITION),
						channel(bone.get("scale"), Axis.SCALE)));
			}
		}

		return new BedrockClip(length, bones);
	}

	/**
	 * How a channel's three components come across, which differs by what they mean:
	 * rotations become radians and lose their Y and Z signs, positions keep their
	 * units and lose the same two signs, and scales are unitless and unsigned.
	 */
	private enum Axis {
		ROTATION(DEGREES_TO_RADIANS, -DEGREES_TO_RADIANS, -DEGREES_TO_RADIANS),
		POSITION(1.0F, -1.0F, -1.0F),
		SCALE(1.0F, 1.0F, 1.0F);

		private final float x;
		private final float y;
		private final float z;

		Axis(float x, float y, float z) {
			this.x = x;
			this.y = y;
			this.z = z;
		}

		void convert(float[] into, int at, float rawX, float rawY, float rawZ) {
			into[at] = rawX * this.x;
			into[at + 1] = rawY * this.y;
			into[at + 2] = rawZ * this.z;
		}
	}

	/**
	 * One channel, in either of the two shapes Blockbench emits: a bare vector for a
	 * bone that holds a constant offset, or a map of timestamp to keyframe.
	 *
	 * @return null when the channel is absent, so the bone samples to rest on that axis
	 */
	private static BedrockClip.Channel channel(JsonElement element, Axis axis) {
		if (element == null || !element.isJsonObject()) {
			// A bare array is legal too, and means the same as a one-key channel.
			if (element != null && element.isJsonArray()) {
				return constant(element.getAsJsonArray(), axis);
			}

			return null;
		}

		JsonObject json = element.getAsJsonObject();

		if (json.has("vector")) {
			return constant(json.getAsJsonArray("vector"), axis);
		}

		// TreeMap because the keys are timestamps written as strings and Blockbench
		// does not promise them in order; sampling assumes ascending times.
		TreeMap<Float, JsonElement> keys = new TreeMap<>();

		for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
			Float time = time(entry.getKey());

			if (time != null) {
				keys.put(time, entry.getValue());
			}
		}

		if (keys.isEmpty()) {
			return null;
		}

		float[] times = new float[keys.size()];
		float[] values = new float[keys.size() * 3];
		int index = 0;

		for (Map.Entry<Float, JsonElement> entry : keys.entrySet()) {
			times[index] = entry.getKey();
			vector(entry.getValue(), axis, values, index * 3);
			index++;
		}

		return new BedrockClip.Channel(times, values);
	}

	private static BedrockClip.Channel constant(JsonArray array, Axis axis) {
		float[] values = new float[3];

		components(array, axis, values, 0);

		return new BedrockClip.Channel(new float[] {0.0F}, values);
	}

	/**
	 * One keyframe's value, in any of the three shapes it can take: a bare array, an
	 * object wrapping one in {@code vector}, or a pre/post pair at a step.
	 *
	 * <p>A pre/post pair takes {@code post} — the value the clip leaves the keyframe
	 * with, which is the one that governs everything after it.
	 */
	private static void vector(JsonElement element, Axis axis, float[] into, int at) {
		if (element.isJsonArray()) {
			components(element.getAsJsonArray(), axis, into, at);
			return;
		}

		if (!element.isJsonObject()) {
			return;
		}

		JsonObject json = element.getAsJsonObject();

		if (json.has("vector")) {
			components(json.getAsJsonArray("vector"), axis, into, at);
			return;
		}

		JsonElement step = json.has("post") ? json.get("post") : json.get("pre");

		if (step != null) {
			vector(step, axis, into, at);
		}
	}

	private static void components(JsonArray array, Axis axis, float[] into, int at) {
		if (array.size() < 3) {
			return;
		}

		axis.convert(into, at, number(array.get(0)), number(array.get(1)), number(array.get(2)));
	}

	/**
	 * One component. Blockbench can write a MoLang expression instead of a number;
	 * nothing here evaluates one, so it reads as zero rather than throwing — a bone
	 * that does not move is a far better failure than a render thread that dies.
	 */
	private static float number(JsonElement element) {
		try {
			return element.getAsFloat();
		} catch (NumberFormatException | UnsupportedOperationException ignored) {
			return 0.0F;
		}
	}

	private static Float time(String key) {
		try {
			return Float.valueOf(key);
		} catch (NumberFormatException ignored) {
			return null;
		}
	}
}

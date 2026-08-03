package com.hrtq.grandcraft.client.animation;

import org.joml.Vector3f;

/**
 * One bone's transform at one instant, already converted into vanilla's rig
 * conventions — radians, model units, {@code ModelPart} axes.
 *
 * <p>Mutable and meant to be reused, because a pose is sampled several times per
 * bone per frame and a record here would allocate three vectors each time.
 *
 * <p>The conversion out of Blockbench's axes happens once at load time, in
 * {@link BedrockClipLoader}, rather than here or at sample time. That way every
 * consumer of a clip is handed numbers it can write straight onto a
 * {@code ModelPart} without knowing the file format existed.
 */
public final class BonePose {
	/** Radians, in {@code ModelPart}'s sense: x pitches back, y turns right, z rolls. */
	public final Vector3f rotation = new Vector3f();

	/** Model units, in {@code ModelPart}'s sense: y is down, z is backwards. */
	public final Vector3f position = new Vector3f();

	public final Vector3f scale = new Vector3f(1.0F, 1.0F, 1.0F);

	/** Back to the rest pose — the identity a missing bone or channel samples to. */
	public void reset() {
		this.rotation.set(0.0F, 0.0F, 0.0F);
		this.position.set(0.0F, 0.0F, 0.0F);
		this.scale.set(1.0F, 1.0F, 1.0F);
	}

	/** Whether this pose does nothing, so a caller can skip the work of applying it. */
	public boolean isRest() {
		return this.rotation.x == 0.0F && this.rotation.y == 0.0F && this.rotation.z == 0.0F
				&& this.position.x == 0.0F && this.position.y == 0.0F && this.position.z == 0.0F;
	}
}

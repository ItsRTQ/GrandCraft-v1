package com.hrtq.grandcraft.client;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec2;

/**
 * Turns the player's movement keys into a world-space direction.
 *
 * <p>One copy, shared by every verb that asks "where is the player steering" — the dodge
 * and the Outlaw's air-dash today. Extracted rather than copied because the transform has
 * a sign that is easy to get backwards: {@code getMoveVector}'s x axis points
 * <strong>left</strong>, and a mirrored copy of this would send a verb the wrong way in a
 * manner that only shows up when strafing.
 *
 * <p><strong>Carries no default for "no input".</strong> That is the caller's, and the
 * two callers genuinely differ: a standing dodge is a backstep, because it answers
 * something coming straight at you, while a standing dash has no meaningful travel
 * direction at all and lets the server fall back to the look angle. Baking either one in
 * here would give the other the wrong answer.
 */
public final class ClientMoveInput {
	/** Vanilla's own literal, used everywhere it converts a yaw. */
	private static final float DEGREES_TO_RADIANS = 0.017453292F;

	private ClientMoveInput() {
	}

	/** The raw input vector: x is strafe (positive is left), y is forward. */
	public static Vec2 moveVector(LocalPlayer player) {
		return player.input.getMoveVector();
	}

	/**
	 * Rotates a forward/strafe pair into world space, as an (x, z) pair.
	 *
	 * <p>Exactly {@code Entity.getInputVector}'s transform rather than a hand-derived
	 * one, so a verb goes precisely where holding that key would have walked.
	 */
	public static Vec2 toWorld(LocalPlayer player, float forward, float strafe) {
		float yaw = player.getYRot() * DEGREES_TO_RADIANS;
		float sin = (float) Math.sin(yaw);
		float cos = (float) Math.cos(yaw);

		return new Vec2(strafe * cos - forward * sin, forward * cos + strafe * sin);
	}
}

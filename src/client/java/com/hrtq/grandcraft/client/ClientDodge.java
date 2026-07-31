package com.hrtq.grandcraft.client;

import com.hrtq.grandcraft.combat.CombatState;
import com.hrtq.grandcraft.network.DodgePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec2;

/**
 * Turns a dodge keypress into a direction and a request.
 *
 * <p>Deliberately thin. The client decides only <em>where</em> the player is asking
 * to go; whether the dodge happens, what it costs, how far it travels and how long
 * it protects are all the server's, and the roll comes back as an ordinary phase
 * packet like any other actor's.
 *
 * <p>That means a dodge on a remote server costs one round trip before it moves.
 * Predicting it locally would need the dodge settings pushed to the client and a
 * reconciliation path for a refused dodge; the server-authoritative version is
 * correct first, and worth revisiting if this is ever played at a real ping.
 */
public final class ClientDodge {
	/** Vanilla's own literal, used everywhere it converts a yaw. */
	private static final float DEGREES_TO_RADIANS = 0.017453292F;

	private ClientDodge() {
	}

	/**
	 * Asks the server for a dodge.
	 *
	 * @return whether a request was sent, so a caller draining buffered presses can
	 *         stop after the first one that counted
	 */
	public static boolean request(Minecraft client) {
		LocalPlayer player = client.player;

		// 26.2 moved the current screen off Minecraft and onto Gui.
		if (player == null || client.gui.screen() != null) {
			return false;
		}

		// The server refuses both of these anyway; checking here keeps a held key from
		// filling the channel with requests that are only going to be dropped. The
		// server remains the one that decides — a client that skipped these would gain
		// nothing but its own wasted packets.
		if (ClientCombatPhases.stateOf(player.getId(), Util.getMillis()) != CombatState.NEUTRAL) {
			return false;
		}

		if (!player.onGround()) {
			return false;
		}

		Vec2 direction = travelDirection(player);

		ClientPlayNetworking.send(new DodgePayload(direction.x, direction.y));
		return true;
	}

	/**
	 * Where the player is asking to go, in world space.
	 *
	 * <p>Taken from movement input rather than from the look direction, so a dodge
	 * goes where you are steering and not where you happen to be aiming — which is
	 * what makes dodging sideways out of a wind-up while still facing the attacker
	 * possible at all.
	 *
	 * <p>With no input at all this is a backstep. Standing still and dodging is a
	 * deliberate answer to something coming straight at you, and launching forwards
	 * into it would be the opposite of what was asked for.
	 */
	private static Vec2 travelDirection(LocalPlayer player) {
		Vec2 move = player.input.getMoveVector();
		float forward = move.y;
		float strafe = move.x;

		if (forward == 0.0F && strafe == 0.0F) {
			forward = -1.0F;
		}

		// Exactly Entity.getInputVector's transform, rather than a hand-derived one:
		// the strafe axis points left, which is easy to get backwards, and matching
		// vanilla means a dodge goes precisely where holding that key would walk.
		float yaw = player.getYRot() * DEGREES_TO_RADIANS;
		float sin = (float) Math.sin(yaw);
		float cos = (float) Math.cos(yaw);

		return new Vec2(strafe * cos - forward * sin, forward * cos + strafe * sin);
	}
}

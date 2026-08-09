package com.hrtq.grandcraft.client;

import com.hrtq.grandcraft.network.AirDashPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec2;

/**
 * Turns a mid-air jump press into an air-dash request.
 *
 * <p>Thin like {@link ClientDodge}, and for the same reason: the client says only where
 * the player is steering, and whether a dash happens, how many are left, what it costs
 * and how far it goes are all the server's. It is never told whether it is hanging on a
 * wall, so it cannot pick the wrong direction rule — off a wall the server uses the look
 * angle, which it has already.
 *
 * <h2>Why this exists at all rather than a jump mixin</h2>
 * A mid-air jump press produces <strong>no</strong> server-side call. Vanilla only
 * reaches {@code jumpFromGround} from the ground, and for a player it reaches it after
 * the fact, from the movement packet handler. There is nothing to hook, so the press has
 * to be observed on the client and sent.
 *
 * <h2>Two traps, both of which produce a flight mode</h2>
 * <ul>
 *   <li><strong>This must run at the head of the client tick, not the tail.</strong> By
 *       {@code END_CLIENT_TICK} the entity tick has already run, so on the very tick a
 *       player jumps off the ground {@code onGround()} already reads false — the ground
 *       jump and a dash would both fire, and every single jump would silently eat its
 *       dash charge.</li>
 *   <li><strong>A rising edge, never {@code consumeClick()}.</strong> A held key
 *       manufactures clicks through GLFW key repeat, which is the whole reason the
 *       radial wheel reads physical key state. Held jump on a click queue is unlimited
 *       dashes; held jump on an edge is one.</li>
 * </ul>
 */
public final class ClientAcrobat {
	private static boolean jumpWasDown;

	private ClientAcrobat() {
	}

	/**
	 * Watches the jump key. Called from {@code START_CLIENT_TICK}.
	 *
	 * <p>The edge is tracked unconditionally, before any of the reasons to do nothing,
	 * so that a press which happened while a screen was open or on the ground is
	 * <em>consumed</em> rather than left waiting to fire on the next tick that qualifies.
	 */
	public static void tick(Minecraft client) {
		boolean down = client.options.keyJump.isDown();
		boolean pressed = down && !jumpWasDown;
		jumpWasDown = down;

		if (!pressed) {
			return;
		}

		LocalPlayer player = client.player;

		// 26.2 moved the current screen off Minecraft and onto Gui.
		if (player == null || client.gui.screen() != null || player.isSpectator()) {
			return;
		}

		// The one check that has to happen before the entity tick, and the reason this
		// method lives where it does. A ground jump is not a dash request.
		if (player.onGround()) {
			return;
		}

		// Stamina is deliberately not checked, matching ClientDodge: the client sends and
		// lets the server refuse. Gating here would need the dash cost pushed into the
		// stamina packet and added to its staleness test, to save one packet.
		Vec2 move = ClientMoveInput.moveVector(player);
		Vec2 direction = move.x == 0.0F && move.y == 0.0F
				// Zero, not a backstep. The dodge's standing default is right for a roll
				// and wrong here — it would launch a player who jumped straight up
				// backwards out of their own jump, and only on the first dash of a trip,
				// since the server overrides with the look angle off a wall. The server
				// reads zero as "no input" and uses the look angle for both.
				? Vec2.ZERO
				: ClientMoveInput.toWorld(player, move.y, move.x);

		ClientPlayNetworking.send(new AirDashPayload(direction.x, direction.y));
	}

	/**
	 * Forgets the key state between worlds.
	 *
	 * <p>Without this, disconnecting while holding jump leaves the edge detector primed,
	 * and the first release-and-press after rejoining reads as a press that never
	 * happened.
	 */
	public static void clear() {
		jumpWasDown = false;
	}
}

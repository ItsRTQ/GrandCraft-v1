package com.hrtq.grandcraft.client;

import com.hrtq.grandcraft.combat.CombatConstants;
import com.hrtq.grandcraft.network.GiveUpPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

/**
 * Turns holding the give-up key into a request to stop waiting.
 *
 * <p>Deliberately thin, like {@link ClientGuard} and {@link ClientDodge}: the client
 * reports that a key is down and nothing else. How long the hold has to last, and
 * whether the player is even in a state where it means anything, are the server's.
 *
 * <p>Re-asserted on the guard's keepalive rather than sent once, and here the reason
 * is sharper than it is for a guard: a release packet that went missing would leave
 * the server counting a hold that has ended, and the end of that hold is a death.
 * A server that stops hearing about it forgets it within a few ticks.
 *
 * <p><strong>Only sent while the client believes it is down.</strong> Not an
 * optimisation — the key does nothing at any other time, and a player who has bound
 * it somewhere they rest their hand should not be sending seven packets a second for
 * the whole game.
 */
public final class ClientGiveUp {
	/** Whether the server has been told the key is down and not yet that it is up. */
	private static boolean holding;

	/** Ticks until the hold is re-asserted. */
	private static int keepalive;

	private ClientGiveUp() {
	}

	/** Sends the edges of a hold, and re-asserts it while it lasts. */
	public static void tick(Minecraft client) {
		if (!wantsToGiveUp(client)) {
			sendRelease();
			return;
		}

		if (!holding) {
			holding = true;
			keepalive = CombatConstants.GUARD_KEEPALIVE_TICKS;
			ClientPlayNetworking.send(new GiveUpPayload(true));
			return;
		}

		if (--keepalive <= 0) {
			keepalive = CombatConstants.GUARD_KEEPALIVE_TICKS;
			ClientPlayNetworking.send(new GiveUpPayload(true));
		}
	}

	public static void clear() {
		holding = false;
		keepalive = 0;
	}

	private static void sendRelease() {
		if (!holding) {
			return;
		}

		holding = false;
		keepalive = 0;
		ClientPlayNetworking.send(new GiveUpPayload(false));
	}

	/**
	 * Whether the player is asking to die right now.
	 *
	 * <p>{@code isDown} rather than {@code consumeClick}: this is a held key, and a
	 * decision this final should take a deliberate second of holding rather than a
	 * keypress that could be a misfire.
	 */
	private static boolean wantsToGiveUp(Minecraft client) {
		return client.player != null
				&& ClientDowned.isDowned()
				&& GrandCraftKeyMappings.GIVE_UP.isDown();
	}
}

package com.hrtq.grandcraft.client;

import com.hrtq.grandcraft.stats.StatSettings;

/**
 * The stat settings as last sent by the server.
 *
 * <p>The client keeps its own copy for the same reason it keeps
 * {@link ClientGameSettings}: the server's is unreachable from a remote client. Here
 * it is the character sheet that needs them — a stat value alone does not say what
 * that stat is currently worth.
 */
public final class ClientStatSettings {
	private static StatSettings current = StatSettings.DEFAULT;

	private ClientStatSettings() {
	}

	public static StatSettings current() {
		return current;
	}

	public static void set(StatSettings settings) {
		current = settings;
	}
}

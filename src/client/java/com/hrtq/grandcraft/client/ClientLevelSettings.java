package com.hrtq.grandcraft.client;

import com.hrtq.grandcraft.progression.LevelSettings;

/**
 * The level settings as last sent by the server.
 *
 * <p>The client keeps its own copy for the same reason it keeps
 * {@link ClientStatSettings}: the server's is unreachable from a remote client. Here
 * it is the character sheet that needs them — a player's Essence total means nothing
 * without the cost of the level they are working towards.
 */
public final class ClientLevelSettings {
	private static LevelSettings current = LevelSettings.DEFAULT;

	private ClientLevelSettings() {
	}

	public static LevelSettings current() {
		return current;
	}

	public static void set(LevelSettings settings) {
		current = settings;
	}
}

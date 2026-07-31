package com.hrtq.grandcraft.config;

/**
 * Holds the general settings currently in force on the server.
 *
 * <p>Server-thread only. Clients keep their own copy of the last synced values
 * rather than reading this, since the server is the authority and a client may be
 * remote.
 */
public final class GameTuning {
	private static GameSettings current = GameSettings.DEFAULT;

	private GameTuning() {
	}

	public static GameSettings current() {
		return current;
	}

	public static void set(GameSettings settings) {
		current = settings;
	}
}

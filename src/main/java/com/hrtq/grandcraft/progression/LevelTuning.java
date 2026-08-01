package com.hrtq.grandcraft.progression;

/**
 * Holds the level settings currently in force on the server.
 *
 * <p>Server-thread only, exactly like {@code CombatTuning}, {@code GameTuning} and
 * {@code StatTuning}. Clients keep their own copy of the last synced values in
 * {@code ClientLevelSettings} rather than reading this, since the server is the
 * authority and a client may be remote.
 *
 * <p>{@link #set} always swaps the whole object rather than mutating one, which
 * keeps the "compare by identity to notice a change" trick available to any future
 * per-tick reader, the way {@code CombatController} already uses it for stats.
 */
public final class LevelTuning {
	private static LevelSettings current = LevelSettings.DEFAULT;

	private LevelTuning() {
	}

	public static LevelSettings current() {
		return current;
	}

	public static void set(LevelSettings settings) {
		current = settings.clamped();
	}
}

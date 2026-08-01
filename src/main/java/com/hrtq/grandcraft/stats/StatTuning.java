package com.hrtq.grandcraft.stats;

/**
 * Holds the stat settings currently in force on the server.
 *
 * <p>Server-thread only, exactly like {@code CombatTuning} and {@code GameTuning}.
 * Clients keep their own copy of the last synced values in
 * {@code ClientStatSettings} rather than reading this, since the server is the
 * authority and a client may be remote.
 *
 * <p>{@link #set} always swaps the whole object rather than mutating one. That is
 * what lets {@code CombatController} decide whether stat effects need re-applying
 * with a single reference comparison per tick.
 */
public final class StatTuning {
	private static StatSettings current = StatSettings.DEFAULT;

	private StatTuning() {
	}

	public static StatSettings current() {
		return current;
	}

	public static void set(StatSettings settings) {
		current = settings.clamped();
	}
}

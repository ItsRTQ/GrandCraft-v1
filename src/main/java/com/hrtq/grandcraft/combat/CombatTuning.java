package com.hrtq.grandcraft.combat;

/**
 * Holds the combat values currently in force and is the only way to change them.
 *
 * <p>All timings and multipliers live in {@link ActorSettings}; no other combat
 * class declares a timing literal. Values are editable in game through
 * {@code /grandcraft config combat} and persisted by {@link CombatConfigFile}.
 *
 * <p>Server-thread only. Combat is server-authoritative, so nothing on the client
 * reads or writes this.
 */
public final class CombatTuning {
	private static CombatSettings current = CombatSettings.DEFAULT;

	private CombatTuning() {
	}

	public static CombatSettings current() {
		return current;
	}

	/** Replaces the active settings. Clamps first, so callers may pass raw input. */
	public static void set(CombatSettings settings) {
		current = settings.clamped();
	}
}

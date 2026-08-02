package com.hrtq.grandcraft.combat;

/**
 * Holds the weapon values currently in force and is the only way to change them.
 *
 * <p>Mirrors {@link CombatTuning}: values are editable in game through
 * {@code /grandcraft config weapons} and persisted by {@link WeaponConfigFile}.
 *
 * <p>Server-thread only, and deliberately <strong>not</strong> pushed to clients.
 * Unlike the stat and level settings, nothing on the client draws these numbers —
 * the character sheet does not explain endlag — so there is no second copy to keep
 * in step.
 */
public final class WeaponTuning {
	private static WeaponSettings current = WeaponSettings.DEFAULT;

	private WeaponTuning() {
	}

	public static WeaponSettings current() {
		return current;
	}

	/** Replaces the active settings. Clamps first, so callers may pass raw input. */
	public static void set(WeaponSettings settings) {
		current = settings.clamped();
	}
}

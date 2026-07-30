package com.hrtq.grandcraft.combat;

/**
 * Holds the combat values currently in force and is the only way to change them.
 *
 * <p>All timings and multipliers live in {@link CombatSettings}; no other combat
 * class declares a timing literal. Values are editable in game through
 * {@code /grandcraft config} and persisted by {@link CombatConfigFile}.
 *
 * <p>Server-thread only. Combat is server-authoritative, so nothing on the client
 * reads or writes this.
 */
public final class CombatTuning {
	/**
	 * How many consecutive hits can stagger before only damage remains. Structural
	 * rather than tunable: {@link CombatSettings} carries exactly two severity
	 * levels, so raising this would have no values to read.
	 */
	public static final int MAX_STAGGER_LEVEL = 2;

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

	public static int staggerTicks(int level) {
		return switch (clampLevel(level)) {
			case 1 -> current.stagger1Ticks();
			case 2 -> current.stagger2Ticks();
			default -> 0;
		};
	}

	/**
	 * The movement penalty for a stagger level, as a negative fraction ready to
	 * hand to an attribute modifier. Settings store the positive magnitude.
	 */
	public static double staggerSpeedPenalty(int level) {
		return switch (clampLevel(level)) {
			case 1 -> -current.stagger1SlowFraction();
			case 2 -> -current.stagger2SlowFraction();
			default -> 0.0;
		};
	}

	public static int staggerResetTicks() {
		return current.staggerResetTicks();
	}

	/**
	 * Jump penalty while staggered, as a negative fraction ready for an attribute
	 * modifier. Stops a staggered actor hopping out of the movement slowdown, since
	 * airborne movement barely consults movement speed.
	 */
	public static double staggerJumpPenalty() {
		return -current.staggerJumpPenalty();
	}

	/**
	 * Knockback resistance held by every combatant at all times, not only while
	 * staggered. Vanilla knockback launches a target far enough that a short
	 * stagger expires before they land, which would leave the reaction with no
	 * practical effect.
	 */
	public static double knockbackResistance() {
		return current.knockbackResistance();
	}

	private static int clampLevel(int level) {
		if (level < 0) {
			return 0;
		}

		return Math.min(level, MAX_STAGGER_LEVEL);
	}
}

package com.hrtq.grandcraft.combat;

/**
 * Timing of a single attack, in ticks.
 *
 * <p>Startup is the wind-up during which no damage may occur. Active is the only
 * window in which the attack may connect. Recovery is the commitment that
 * follows, and it is paid whether the attack hit or missed.
 *
 * <p>Instances belong in {@link CombatTuning} so timings stay in one place.
 */
public record AttackProfile(int startupTicks, int activeTicks, int recoveryTicks) {
	public AttackProfile {
		if (startupTicks < 0 || recoveryTicks < 0) {
			throw new IllegalArgumentException("Attack phase durations cannot be negative");
		}

		// An attack with no active window could never connect.
		if (activeTicks < 1) {
			throw new IllegalArgumentException("activeTicks must be at least 1");
		}
	}
}

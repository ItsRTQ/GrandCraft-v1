package com.hrtq.grandcraft.combat;

/**
 * One actor's hit reaction, resolved from its {@link ActorSettings}.
 *
 * <p>Penalties are returned already negated, ready to hand to an attribute
 * modifier; settings store the positive magnitude so the UI and JSON read
 * naturally.
 */
public record StaggerProfile(
		int level1Ticks,
		double level1SlowFraction,
		int level2Ticks,
		double level2SlowFraction,
		int resetTicks,
		double jumpPenaltyFraction) {

	/**
	 * How many consecutive hits can stagger before only damage remains. Structural
	 * rather than tunable: an actor carries exactly two severity levels, so raising
	 * this would have no values to read.
	 */
	public static final int MAX_LEVEL = 2;

	/**
	 * Builds the diminishing sequence from the single configured duration.
	 *
	 * <p>The follow-up stagger is half as long and half as heavy. Keeping the second
	 * level derived rather than configurable is what preserves the anti-stunlock
	 * guarantee — there is no way to tune the sequence into a permanent lock, because
	 * a third consecutive hit still produces nothing.
	 */
	public static StaggerProfile from(ActorSettings settings) {
		return new StaggerProfile(
				settings.staggerTicks(),
				CombatConstants.STAGGER_SLOW,
				settings.staggerTicks() / 2,
				CombatConstants.STAGGER_SLOW_WEAK,
				CombatConstants.STAGGER_RESET_TICKS,
				CombatConstants.STAGGER_JUMP_PENALTY);
	}

	/** How long this stagger level lasts, or 0 for a level that does not stagger. */
	public int ticks(int level) {
		return switch (clampLevel(level)) {
			case 1 -> this.level1Ticks;
			case 2 -> this.level2Ticks;
			default -> 0;
		};
	}

	/** The movement penalty for a stagger level, as a negative fraction. */
	public double speedPenalty(int level) {
		return switch (clampLevel(level)) {
			case 1 -> -this.level1SlowFraction;
			case 2 -> -this.level2SlowFraction;
			default -> 0.0;
		};
	}

	/**
	 * Jump penalty while staggered, as a negative fraction. Stops a staggered actor
	 * hopping out of the movement slowdown, since airborne movement barely consults
	 * movement speed.
	 */
	public double jumpPenalty() {
		return -this.jumpPenaltyFraction;
	}

	private static int clampLevel(int level) {
		if (level < 0) {
			return 0;
		}

		return Math.min(level, MAX_LEVEL);
	}
}

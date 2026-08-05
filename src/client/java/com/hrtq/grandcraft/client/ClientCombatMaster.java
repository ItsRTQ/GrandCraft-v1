package com.hrtq.grandcraft.client;

/**
 * How long the local player's Combat Master window has left.
 *
 * <p><strong>A deadline, not an extrapolation</strong>, and that is the whole
 * difference between this and {@link ClientStamina} or {@link ClientMana}. Those are
 * told a value and a rate and have to guess where the server has got to since; this is
 * told a length once and counts down to a fixed moment. It cannot drift, needs no
 * staleness test, and costs two packets per window instead of twenty a second.
 *
 * <p>The server sends only two things: the window opening, with the duration it
 * actually granted, and an <em>early</em> close as a zero. Expiry is never sent,
 * because the deadline already says when that is.
 */
public final class ClientCombatMaster {
	/** Milliseconds per tick, for turning the server's tick count into a deadline. */
	private static final long MILLIS_PER_TICK = 50L;

	private static long expiresAtMillis;

	private ClientCombatMaster() {
	}

	/**
	 * @param remainingTicks the window's length, or zero to close it now
	 */
	public static void accept(int remainingTicks, long nowMillis) {
		expiresAtMillis = remainingTicks <= 0
				? 0L
				: nowMillis + remainingTicks * MILLIS_PER_TICK;
	}

	public static boolean isActive(long nowMillis) {
		return nowMillis < expiresAtMillis;
	}

	/** Seconds left, for the badge to draw. Never negative. */
	public static float secondsLeft(long nowMillis) {
		return Math.max(0.0F, (expiresAtMillis - nowMillis) / 1000.0F);
	}

	/** Dropped on disconnect so a stale window cannot follow the player into a new world. */
	public static void clear() {
		expiresAtMillis = 0L;
	}
}

package com.hrtq.grandcraft.client;

/**
 * How long the local player still cannot attack for, as told by the server.
 *
 * <p>Counted down locally from a single packet rather than streamed, so the value
 * drifts by at most the latency of one message over a window of a few ticks. It is
 * only used to draw the attack indicator, so drift that small is invisible and the
 * server remains the thing that actually enforces the lockout.
 */
public final class ClientAttackLockout {
	private static long endMillis;
	private static long totalMillis;

	private ClientAttackLockout() {
	}

	public static void begin(int ticks, long nowMillis) {
		totalMillis = ticks * 50L;
		endMillis = nowMillis + totalMillis;
	}

	/**
	 * @return how far through the lockout we are, from 0 at the start to just under
	 *         1 at the end, or null when no lockout is running.
	 */
	public static Float progress(long nowMillis) {
		if (totalMillis <= 0L || nowMillis >= endMillis) {
			return null;
		}

		return (float) (1.0 - (endMillis - nowMillis) / (double) totalMillis);
	}

	/** Dropped on disconnect so a stale lockout cannot follow the player into a new world. */
	public static void clear() {
		endMillis = 0L;
		totalMillis = 0L;
	}
}

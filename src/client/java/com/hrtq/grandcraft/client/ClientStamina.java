package com.hrtq.grandcraft.client;

import com.hrtq.grandcraft.network.StaminaPayload;

/**
 * The local player's stamina as last reported by the server, carried forward
 * between packets.
 *
 * <p>Same design as {@link ClientAttackLockout}: the server sends a snapshot when
 * the value moves, and the client integrates regen locally so the bar animates
 * smoothly instead of stepping. Drift is bounded by the send interval, and the
 * server remains the only thing that actually enforces a cost.
 *
 * <p>Extrapolation only ever moves the value <em>up</em>. Spending is server driven
 * and arrives as a fresh snapshot, so the client never has to guess about a cost.
 */
public final class ClientStamina {
	private static float current;
	private static float max;
	private static int regenPerSecond;
	private static int regenDelayTicks;
	private static int jumpCost;
	private static boolean exhausted;
	private static long receivedMillis;

	private ClientStamina() {
	}

	public static void accept(StaminaPayload payload, long nowMillis) {
		current = payload.current();
		max = payload.max();
		regenPerSecond = payload.regenPerSecond();
		regenDelayTicks = payload.regenDelayTicks();
		jumpCost = payload.jumpCost();
		exhausted = payload.exhausted();
		receivedMillis = nowMillis;
	}

	/**
	 * Whether the player could pay for an action costing {@code cost} right now.
	 *
	 * <p>Answers true when no pool has been reported, so a client that has not heard
	 * from the server never blocks anything of its own accord.
	 *
	 * <p>The extrapolated value can differ from the server's by up to one send
	 * interval. Over a jump costing a few points out of a hundred that is far below
	 * the resolution of the decision, and erring either way costs at most one jump.
	 */
	public static boolean canAfford(int cost, long nowMillis) {
		if (!hasData() || cost <= 0) {
			return true;
		}

		return !exhausted && current(nowMillis) >= cost;
	}

	/** What a jump costs, so the client can refuse one it could not pay for. */
	public static int jumpCost() {
		return jumpCost;
	}

	/** False until the first packet arrives, which is when the bar may be drawn. */
	public static boolean hasData() {
		return max > 0.0F;
	}

	public static float max() {
		return max;
	}

	/**
	 * Whether the pool is spent. Taken from the server rather than compared against
	 * the recovery threshold locally, because the server decides when the player may
	 * act again and the bar must say the same thing.
	 */
	public static boolean exhausted() {
		return exhausted;
	}

	/** The pool as it stands now, with regen since the last packet applied. */
	public static float current(long nowMillis) {
		if (!hasData()) {
			return 0.0F;
		}

		long elapsed = nowMillis - receivedMillis;
		long delay = regenDelayTicks * 50L;

		if (elapsed <= delay) {
			return current;
		}

		float regenerated = regenPerSecond * (elapsed - delay) / 1000.0F;
		return Math.min(max, current + regenerated);
	}

	/** Dropped on disconnect so a stale pool cannot follow the player into a new world. */
	public static void clear() {
		current = 0.0F;
		max = 0.0F;
		regenPerSecond = 0;
		regenDelayTicks = 0;
		jumpCost = 0;
		exhausted = false;
		receivedMillis = 0L;
	}
}

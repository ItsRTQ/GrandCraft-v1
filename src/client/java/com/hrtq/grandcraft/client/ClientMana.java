package com.hrtq.grandcraft.client;

import com.hrtq.grandcraft.network.ManaPayload;

/**
 * The local player's mana as last reported by the server, carried forward between
 * packets.
 *
 * <p>Same design as {@link ClientStamina}, minus everything stamina needs in order
 * to <em>decide</em> something: mana has no exhaustion state and nothing on the
 * client refuses an action for want of it, so this only has to be able to draw the
 * number on the character sheet.
 */
public final class ClientMana {
	private static float current;
	private static float max;
	private static int regenPerSecond;
	private static int regenDelayTicks;
	private static long receivedMillis;

	private ClientMana() {
	}

	public static void accept(ManaPayload payload, long nowMillis) {
		current = payload.current();
		max = payload.max();
		regenPerSecond = payload.regenPerSecond();
		regenDelayTicks = payload.regenDelayTicks();
		receivedMillis = nowMillis;
	}

	/** False until the first packet arrives, or when mana is switched off entirely. */
	public static boolean hasData() {
		return max > 0.0F;
	}

	public static float max() {
		return max;
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
		receivedMillis = 0L;
	}
}

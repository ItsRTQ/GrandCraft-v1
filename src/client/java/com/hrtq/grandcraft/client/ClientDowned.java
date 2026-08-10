package com.hrtq.grandcraft.client;

import com.hrtq.grandcraft.network.DownedPayload;

/**
 * The local player's own bleed-out clock as last reported, carried forward between
 * packets.
 *
 * <p>Same design as {@link ClientStamina}: the server sends a snapshot when something
 * it cannot extrapolate changes, and the client counts the seconds down itself in
 * between so the timer ticks smoothly rather than in half-second steps. The server
 * remains the only thing that decides when the clock actually runs out.
 *
 * <p><strong>Extrapolation only ever moves the clock down.</strong> Every way it can
 * move the other way — a hit taking a chunk out of it, standing up — arrives as a
 * fresh snapshot, so the client never guesses about a jump. The one thing it must not
 * do is reach zero on its own and act on it: this is a readout, and the death is
 * announced by the server like any other.
 *
 * <p>Two other things read this beyond the HUD, and both are gates the server cannot
 * enforce alone: jumping and sprinting are client-predicted, so a client that did not
 * know it was down would hop about on the floor. See {@code ClientJumpMixin}.
 */
public final class ClientDowned {
	private static boolean downed;
	private static int bleedOutTicks;
	private static int bleedOutTotalTicks;
	private static int reviveTicks;
	private static int reviveTotalTicks;
	private static int giveUpTicks;
	private static int giveUpTotalTicks;
	private static long receivedMillis;

	private static final double MILLIS_PER_TICK = 50.0;

	private ClientDowned() {
	}

	public static void accept(DownedPayload payload, long nowMillis) {
		downed = payload.downed();
		bleedOutTicks = payload.bleedOutTicks();
		bleedOutTotalTicks = payload.bleedOutTotalTicks();
		reviveTicks = payload.reviveTicks();
		reviveTotalTicks = payload.reviveTotalTicks();
		giveUpTicks = payload.giveUpTicks();
		giveUpTotalTicks = payload.giveUpTotalTicks();
		receivedMillis = nowMillis;
	}

	/**
	 * Forgotten on disconnect, like every other client store keyed to one connection.
	 * A player who logs into a second world still lying on the floor of the first is
	 * the failure this prevents.
	 */
	public static void clear() {
		downed = false;
		bleedOutTicks = 0;
		bleedOutTotalTicks = 0;
		reviveTicks = 0;
		reviveTotalTicks = 0;
		giveUpTicks = 0;
		giveUpTotalTicks = 0;
	}

	public static boolean isDowned() {
		return downed;
	}

	/** Ticks of clock left, counted down locally since the last packet. */
	public static int bleedOutTicks(long nowMillis) {
		if (!downed) {
			return 0;
		}

		int elapsed = (int) ((nowMillis - receivedMillis) / MILLIS_PER_TICK);

		return Math.max(0, bleedOutTicks - Math.max(0, elapsed));
	}

	public static int bleedOutTotalTicks() {
		return bleedOutTotalTicks;
	}

	/**
	 * How far someone else has got picking this player up, in ticks.
	 *
	 * <p>Not extrapolated, unlike the clock. A revive stops the instant the ally lets
	 * go or steps away and the client has no way to know that has happened — guessing
	 * forward would draw a bar filling for someone who walked off.
	 */
	public static int reviveTicks() {
		return reviveTicks;
	}

	public static int giveUpTicks() {
		return giveUpTicks;
	}

	/**
	 * The two totals the bars are drawn against, straight from the server's live
	 * settings.
	 *
	 * <p>They travel on the packet because combat tuning is server-held: a client only
	 * ever sees it if an admin opens the config screen, so there is nothing local to
	 * read them from. Floored at one, because a bar divided by a zero an admin typed is
	 * a crash rather than an empty bar.
	 */
	public static int reviveTotalTicks() {
		return Math.max(1, reviveTotalTicks);
	}

	public static int giveUpTotalTicks() {
		return Math.max(1, giveUpTotalTicks);
	}
}

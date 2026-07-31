package com.hrtq.grandcraft.combat;

/**
 * The combat states shared by every GrandCraft combatant.
 *
 * <p>NEUTRAL is the baseline: combat is built around returning here often rather
 * than maintaining long attack chains.
 *
 * <p>STAGGERED is a hit reaction, not a stun. It is only ever entered from
 * NEUTRAL or ATTACK_STARTUP — see {@link CombatController#applyStagger}.
 */
public enum CombatState {
	NEUTRAL,
	ATTACK_STARTUP,
	ATTACK_ACTIVE,
	ATTACK_RECOVERY,
	STAGGERED,

	/**
	 * The invulnerable part of a dodge. Entered directly from NEUTRAL — there is no
	 * vulnerable startup, so committing to a dodge always beats an attack already in
	 * flight.
	 */
	DODGE_ACTIVE,

	/**
	 * The tail of a dodge: no longer invulnerable and not yet able to act. This is
	 * the whole cost of the verb, and the reason dodging early is punished — the
	 * roll ends before the attack does and lands the actor in recovery underneath it.
	 */
	DODGE_RECOVERY;

	private static final CombatState[] BY_ID = values();

	/** Whether this is part of a dodge, in either of its two halves. */
	public boolean isDodge() {
		return this == DODGE_ACTIVE || this == DODGE_RECOVERY;
	}

	/**
	 * The state a wire id names, for the animation packet.
	 *
	 * <p>Falls back to NEUTRAL rather than throwing: this is decoded on the netty
	 * thread from a value the client cannot vouch for, and "no pose" is the right
	 * way to fail. Ordinals are only ever compared within one connection, so
	 * reordering the constants is safe.
	 */
	public static CombatState byId(int id) {
		return id < 0 || id >= BY_ID.length ? NEUTRAL : BY_ID[id];
	}
}

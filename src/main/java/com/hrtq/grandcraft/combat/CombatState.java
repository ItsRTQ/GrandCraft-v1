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
	DODGE_RECOVERY,

	/**
	 * A guard coming up, and deliberately not yet protecting anything. This is the
	 * whole cost of the verb paid in advance — the mirror image of a dodge, which
	 * protects immediately and charges for it afterwards.
	 */
	GUARD_RAISE,

	/**
	 * The guard is up and absorbing. Held for as long as the actor asks for it, so
	 * unlike every other state its length is not known when it begins — see
	 * {@link CombatController} on the lease that expresses that.
	 */
	GUARDING,

	/** The tail of a guard: no longer absorbing and not yet able to act. */
	GUARD_RECOVERY,

	/**
	 * Prone, bleeding out, and not dead yet. Entered instead of dying; left by being
	 * revived, giving up, or the clock running out — {@link Downed} owns all three.
	 *
	 * <p><strong>Being a state rather than a flag is the whole design.</strong> Every
	 * "you cannot do that while down" rule is inherited rather than written:
	 * {@link CombatController#canStartAttack} already refuses anything that is not
	 * NEUTRAL, {@link CombatController#canActFreely} already gates the dodge, the guard
	 * and the Outlaw's dash, and {@code CombatPhasePayload} already tells every watching
	 * client what state an actor is in, which is what draws the prone pose.
	 *
	 * <p>Unlike every other state here its length is not a duration. The phase clock
	 * runs on a renewing lease, exactly as {@link #GUARDING}'s does and for the same
	 * reason; the bleed-out clock is {@link Downed}'s and is reported on its own packet.
	 */
	DOWNED;

	private static final CombatState[] BY_ID = values();

	/** Whether this is part of an attack, in any of its three phases. */
	public boolean isAttack() {
		return this == ATTACK_STARTUP || this == ATTACK_ACTIVE || this == ATTACK_RECOVERY;
	}

	/** Whether this is part of a dodge, in either of its two halves. */
	public boolean isDodge() {
		return this == DODGE_ACTIVE || this == DODGE_RECOVERY;
	}

	/** Whether this is part of a guard, in any of its three phases. */
	public boolean isGuard() {
		return this == GUARD_RAISE || this == GUARDING || this == GUARD_RECOVERY;
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

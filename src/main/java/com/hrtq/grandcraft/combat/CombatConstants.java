package com.hrtq.grandcraft.combat;

/**
 * Combat values that are fixed rather than configurable.
 *
 * <p>These were all sliders once. They were taken out of the config screen to keep
 * it to the handful of settings that actually get tuned, but the behaviour they
 * drive is unchanged — nothing here is dead, and promoting any of them back to a
 * per-actor setting is a field in {@link ActorSettings} plus a slider.
 */
public final class CombatConstants {
	/**
	 * The damage window. Structural rather than a matter of taste: an attack with no
	 * active window could never connect, and a long one only widens the slot in
	 * which a target who has stepped away can wander back in.
	 */
	public static final int ACTIVE_TICKS = 2;

	/** Movement lost on a first stagger, and on the weaker follow-up. */
	public static final double STAGGER_SLOW = 0.20;
	public static final double STAGGER_SLOW_WEAK = 0.10;

	/**
	 * Ticks without a qualifying hit before the stagger sequence resets. Vanilla
	 * invulnerability floors re-hits near 10 ticks and a sword swings every 12-13,
	 * so much below 15 the strong / weak / none sequence stops forming.
	 */
	public static final int STAGGER_RESET_TICKS = 30;

	/**
	 * Jump strength removed while staggered. Movement speed only governs ground
	 * acceleration, so without this a staggered actor jumps and carries its momentum
	 * straight through the slowdown.
	 */
	public static final double STAGGER_JUMP_PENALTY = 0.50;

	/**
	 * Held permanently by every combatant, not only while staggered. Vanilla
	 * knockback throws a target far enough that a short stagger expires before they
	 * land, which would leave the reaction with no practical effect.
	 */
	public static final double KNOCKBACK_RESISTANCE = 0.50;

	/**
	 * Extra wait before regen starts after a pool is emptied, on top of the actor's
	 * configured regen delay. Structural rather than tunable: bottoming out has to
	 * cost more than an ordinary spend or there is no reason to keep a reserve.
	 */
	public static final int EXHAUSTION_RECOVERY_TICKS = 20;

	/**
	 * How far a pool must refill before an exhausted actor may act again. Without a
	 * threshold an actor would recover a single point and immediately spend it,
	 * turning exhaustion into a stutter instead of a punished mistake.
	 */
	public static final double EXHAUSTION_CLEAR_FRACTION = 0.25;

	/**
	 * Routine stamina packets are held at least this many ticks apart. The client
	 * integrates regen between them, so this trades a little drift on a bar nobody
	 * reads to the point against streaming a packet per tick per player. A change of
	 * state ignores it, and so does a swing.
	 */
	public static final int STAMINA_SYNC_INTERVAL_TICKS = 4;

	/**
	 * How far stamina must move before a routine packet is worth sending. Below a
	 * point of difference the bar would not visibly change.
	 */
	public static final float STAMINA_SYNC_EPSILON = 0.5F;

	/**
	 * How long a stretch of {@link CombatState#GUARDING} is granted at a time.
	 *
	 * <p>A held guard has no known length, but every other phase does, and the whole
	 * phase pipeline — the payload, the client store, the pose — is built on "this
	 * lasts N ticks". Rather than teach all of it about an indefinite phase, a guard
	 * is granted in fixed leases that renew themselves while the actor still wants
	 * it. That makes a lost release packet self-healing: the guard expires on its own
	 * within one lease instead of standing up forever.
	 */
	public static final int GUARD_LEASE_TICKS = 20;

	/**
	 * How often a guarding client re-asserts that it is still holding the key. The
	 * server forgets the hold after {@link #GUARD_HOLD_TIMEOUT_TICKS} without one, so
	 * this has to be comfortably the shorter of the two.
	 *
	 * <p>Also the worst case for <em>raising</em> a guard the server was refusing —
	 * mid-recovery, or exhausted — because the same message is what asks again. Kept
	 * short for that reason rather than for the timeout's: at three ticks the wait to
	 * re-guard after a swing is never worse than the raise itself, and a one byte
	 * payload seven times a second is nothing beside the twenty movement packets
	 * already going the same way.
	 */
	public static final int GUARD_KEEPALIVE_TICKS = 3;

	/**
	 * The two terms of how much an absorbed hit wears the item doing the blocking:
	 * {@code floor(BASE + FACTOR * absorbed)}.
	 *
	 * <p>Vanilla's own figures for a shield, so a shield lasts about as long behind
	 * GrandCraft's guard as it did behind vanilla's. <strong>Vanilla's threshold is
	 * deliberately not kept.</strong> It ignored anything under three damage, which
	 * made a great many real blocks free here: a zombie deals about three before its
	 * rolled damage multiplier, and difficulty scaling takes an Easy hit to 2.5, so
	 * the threshold silently exempted roughly half of Normal and all of Easy. Vanilla
	 * could afford it because a shield also absorbs tiny incidental ticks; every hit
	 * this guard absorbs is a real block, so every one of them costs something.
	 *
	 * <p>Absorbing is the only thing that wears the item — holding the guard costs
	 * stamina, not durability, or standing still would be expensive.
	 */
	public static final float GUARD_WEAR_BASE = 1.0F;
	public static final float GUARD_WEAR_FACTOR = 1.0F;

	/**
	 * How long the server keeps believing a guard is held without hearing from the
	 * client. Generous enough to ride out a dropped keepalive or a lag spike, short
	 * enough that a disconnect mid-guard does not leave one standing.
	 */
	public static final int GUARD_HOLD_TIMEOUT_TICKS = 10;

	/**
	 * Melee reach, as the horizontal inflation of the attacker's hitbox rather than
	 * a centre-to-centre distance. Set to vanilla's own value, so the reach mixin
	 * currently reproduces vanilla exactly and exists to make this one number
	 * adjustable.
	 */
	public static final double MELEE_REACH = Math.sqrt(2.04) - 0.6;

	private CombatConstants() {
	}
}

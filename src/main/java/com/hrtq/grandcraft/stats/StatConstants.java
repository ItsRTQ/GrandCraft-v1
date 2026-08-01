package com.hrtq.grandcraft.stats;

/**
 * Fixed quantities of the stat system, deliberately not exposed in the config
 * screen — the same split {@code CombatConstants} makes for combat.
 *
 * <p>{@link #NEUTRAL} is the one that has to stay put. Every effect in
 * {@link StatSettings} is priced per point <em>above</em> it, so moving it would
 * silently change the meaning of every configured number at once, in a direction
 * nobody editing that number asked for. A stat sitting exactly at neutral is
 * defined to do nothing at all, which is what makes an unclassed player the honest
 * baseline to compare a class against.
 */
public final class StatConstants {
	/** The value at which a stat has no effect either way. */
	public static final double NEUTRAL = 10.0;

	/** Floor of the attribute range. Zero, not negative — a stat is a quantity. */
	public static final double MIN = 0.0;

	/**
	 * Ceiling of the attribute range. Far above anything the class table or a level
	 * would produce; it exists so a bad modifier cannot run away, not as a design
	 * target. The real bound on what a stat does is the multiplier clamp in
	 * {@link StatSettings}.
	 */
	public static final double MAX = 1000.0;

	private StatConstants() {
	}
}

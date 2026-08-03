package com.hrtq.grandcraft.combat;

/**
 * Weapon figures that are rules of the system rather than values to tune.
 *
 * <p>The same bargain {@link CombatConstants} strikes: everything an admin should be
 * able to move lives in the config, and everything that would break an invariant if it
 * moved lives here, where changing it costs a rebuild and a moment's thought.
 */
public final class WeaponConstants {
	/**
	 * How a weapon nobody has authored a requirement for works out what to demand.
	 *
	 * <p>{@code requirement = round(SLOPE * vanillaDamage - OFFSET)}, in hundredths.
	 *
	 * <p>These two numbers exist so that vanilla — and every weapon from every mod this
	 * one has never heard of — arrives already gated, without a table anyone has to
	 * maintain. They were fitted to the intended vanilla sword ladder and reproduce it
	 * exactly: wooden 4 damage asks 5, stone 5 asks 7, iron 6 asks 8, diamond 7 asks 10,
	 * netherite 8 asks 12.
	 *
	 * <p><strong>Not config fields.</strong> A slope is not something anyone can judge
	 * by feel — the number a person actually has an opinion about is what a specific
	 * weapon demands, and that belongs on the weapon as a component. This is the safety
	 * net underneath, and a safety net with a slider is a trapdoor.
	 */
	public static final double REQUIREMENT_SLOPE = 1.70;
	public static final double REQUIREMENT_OFFSET = 1.80;

	private WeaponConstants() {
	}
}

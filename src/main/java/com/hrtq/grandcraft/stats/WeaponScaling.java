package com.hrtq.grandcraft.stats;

/**
 * What a character's stats do to the weapon they are holding.
 *
 * <p>The melee counterpart of {@link ArcaneScaling}, and deliberately the same shape:
 * a snapshot of the finished multipliers rather than a live view, so the numbers that
 * decided a swing cannot change halfway through resolving it.
 *
 * <p><strong>Built per swing, not cached.</strong> Same reasoning as
 * {@link ArcaneScaling} rather than {@link StaminaScaling}: a swing is a discrete
 * event a handful of times a second, not a per-tick read from eleven places, so a
 * cached copy would buy nothing and would be one more thing to go stale after a
 * reclass, a level-up, or picking up a different sword.
 *
 * <h2>Knows nothing about weapons</h2>
 * Every weapon-shaped input — the base, the requirement, the weights — arrives already
 * resolved. That keeps this package free of any dependency on the combat layer's
 * categories and tags, and it means the client's tooltip and the server's swing reach
 * the same answer by calling the same method with the same arguments rather than by
 * two implementations agreeing. {@code MeleeDamage} is what resolves them.
 */
public record WeaponScaling(
		float base,
		float baseScale,
		float multiplier,
		boolean meetsRequirement,
		WeaponRequirement requirement,
		double blend,
		float failedDamage) {

	/**
	 * Resolves a character against an already-resolved weapon.
	 *
	 * @param base what survives of the weapon after the global down-scale
	 * @param baseScale the down-scale that produced it, kept because the enchantment
	 *     term has to be brought onto the same footing separately
	 * @param requirement the minimum this weapon demands; {@link WeaponRequirement#NONE}
	 *     for anything that demands nothing
	 * @param blend this character's effective stat for the weapon's weights
	 * @param failedDamage what a swing deals when the requirement is not met
	 */
	public static WeaponScaling of(float base, float baseScale, WeaponRequirement requirement,
			double blend, boolean meetsRequirement, float failedDamage, StatSettings settings) {
		// Priced against the requirement rather than against neutral, which is the one
		// way this rate differs from every other in StatSettings. Meeting a weapon's
		// demand exactly is what "1.0x" means here, so the curve starts where the weapon
		// says it starts and a heavier weapon is not punished for demanding more.
		double surplus = blend - requirement.value();

		return new WeaponScaling(base, baseScale, settings.meleeDamageMultiplier(surplus),
				meetsRequirement, requirement, blend, failedDamage);
	}

	/** What this swing deals, before enchantments. */
	public float damage() {
		return this.meetsRequirement ? this.base * this.multiplier : this.failedDamage;
	}

	/**
	 * An enchantment's flat bonus, brought onto the same scale as everything else.
	 *
	 * <p>Down-scaled and stat-multiplied along with the weapon rather than added on top
	 * of it. Vanilla's Sharpness V is +3 against a base that is now around 3.5, so
	 * letting it ride un-scaled would make an enchantment worth more than the character
	 * — which is precisely the arrangement this whole system replaced. Both terms are
	 * applied for the same reason: the down-scale is what put the weapon on this scale,
	 * and the multiplier is what the character does to things on it.
	 *
	 * <p>Zero on a failed requirement. A weapon you cannot lift is not made liftable by
	 * being sharp, and an enchanted claymore that dealt real damage to a Sorcerer would
	 * be the one hole in the gate.
	 */
	public float enchantBonus(float vanillaBonus) {
		return this.meetsRequirement ? vanillaBonus * this.baseScale * this.multiplier : 0.0F;
	}

	/** How far this character sits above (or below) what the weapon demands. */
	public double surplus() {
		return this.blend - this.requirement.value();
	}
}

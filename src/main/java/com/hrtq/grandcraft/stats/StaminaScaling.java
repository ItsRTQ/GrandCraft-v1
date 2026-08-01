package com.hrtq.grandcraft.stats;

import net.minecraft.world.entity.LivingEntity;

/**
 * What one actor's stats do to its stamina: Agility makes every cost cheaper,
 * Constitution makes the pool come back faster.
 *
 * <h2>Why a multiplier and not a modified StaminaSettings</h2>
 * The obvious move is to hand the controller a per-player copy of
 * {@code StaminaSettings} with the numbers already adjusted. It does not work: every
 * field of that record is an {@code int} by design, so a deliberately slight effect
 * rounds away. At a regen of 15 per second and 3% per point, one point above neutral
 * gives 15.45, which truncates back to 15 — no effect at all — while two points give
 * 16. The stat would appear to do nothing until it suddenly did, and the first
 * report would be that Constitution is broken.
 *
 * <p>So the settings stay shared and integral, exactly as configured, and the
 * scaling is applied as a float at the point of use.
 */
public record StaminaScaling(float costMultiplier, float regenMultiplier) {

	/** No stats in play — mobs, and anything before its first stat pass. */
	public static final StaminaScaling NONE = new StaminaScaling(1.0F, 1.0F);

	public static StaminaScaling of(LivingEntity entity, StatSettings settings) {
		double agility = StatEffects.statOf(entity, GrandCraftAttributes.AGILITY);
		double constitution = StatEffects.statOf(entity, GrandCraftAttributes.CONSTITUTION);

		return new StaminaScaling(
				settings.staminaCostMultiplier(agility),
				settings.staminaRegenMultiplier(constitution));
	}

	/**
	 * A cost with Agility applied.
	 *
	 * <p>One method rather than an {@code int}/{@code float} pair: the two would do
	 * exactly the same thing, and an overload set that differs only by a widening
	 * conversion is a trap for whoever adds the next caller.
	 */
	public float cost(float base) {
		return base * this.costMultiplier;
	}
}

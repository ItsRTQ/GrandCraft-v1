package com.hrtq.grandcraft.stats;

import net.minecraft.world.entity.LivingEntity;

/**
 * What an actor's Arcane does to the spells it casts.
 *
 * <p>Shaped after {@link StaminaScaling}, and for the same underlying reason: the
 * configured figures are whole numbers so they survive a config field, and a slight
 * per-point effect would round away entirely if it were folded back into them. The
 * multiplier is applied at the point of use instead.
 *
 * <p><strong>Built per cast, not cached on the controller.</strong> That is the one
 * way it differs from {@code StaminaScaling}, and the difference is deliberate rather
 * than an oversight: stamina is read from eleven places inside a per-tick pass, so
 * recomputing it every tick would be waste; a spell is cast on a right-click, which
 * is rare enough that a cached copy would only be another thing that could go stale
 * after a reclass.
 */
public record ArcaneScaling(float damageMultiplier, float cooldownMultiplier) {
	/** Neutral Arcane, or an actor with no stats at all. */
	public static final ArcaneScaling NONE = new ArcaneScaling(1.0F, 1.0F);

	/**
	 * The shortest cooldown any amount of Arcane may produce.
	 *
	 * <p>A hard floor in ticks, not a tuning preference. A held right-click re-enters
	 * {@code Minecraft.startUseItem} every four ticks, so a cooldown at or below that
	 * stops gating anything and the implement fires continuously for as long as the
	 * button is down. Five is one tick clear of it.
	 *
	 * <p>Enforced here rather than at the call site so it cannot be bypassed by
	 * whatever casts next: this is the only place a cooldown figure is produced.
	 */
	public static final int MIN_COOLDOWN_TICKS = 5;

	public static ArcaneScaling of(LivingEntity entity, StatSettings settings) {
		double arcane = StatEffects.statOf(entity, GrandCraftAttributes.ARCANE);

		return new ArcaneScaling(
				settings.spellDamageMultiplier(arcane),
				settings.spellCooldownMultiplier(arcane));
	}

	public float damage(float base) {
		return base * this.damageMultiplier;
	}

	/**
	 * How long the implement is unusable after this caster casts.
	 *
	 * <p>Rounded rather than truncated, so a multiplier just under 1 does not read as
	 * a free tick, and floored at {@link #MIN_COOLDOWN_TICKS}.
	 */
	public int cooldown(int baseTicks) {
		return Math.max(MIN_COOLDOWN_TICKS, Math.round(baseTicks * this.cooldownMultiplier));
	}
}

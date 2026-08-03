package com.hrtq.grandcraft.effect;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * Applying a timed effect so that drinking two of something is worth twice one.
 *
 * <h2>Vanilla refreshes; it does not extend</h2>
 *
 * <p>{@code LivingEntity.addEffect} resolves a clash through
 * {@code MobEffectInstance.update}, and at equal amplifier that keeps the <em>longer</em>
 * of the two durations rather than their sum. For a status effect that is the right
 * rule — twenty seconds of Strength is twenty seconds of Strength however many times you
 * top it up. For a consumable that returns a fixed quantity of a resource it is quietly
 * destructive: three vials drunk back to back would have granted one vial's worth and
 * silently eaten the other two, because each drink takes less time than the effect it
 * applies.
 *
 * <p>{@code MobEffectInstance.duration} is private with no setter, so the only way to
 * lengthen a running effect is to take it off and put a longer one back on. That is all
 * this does, and it lives here rather than in either potion because both of them need
 * it and the reasoning above should not be written down twice.
 */
public final class StackingEffects {
	private StackingEffects() {
	}

	/**
	 * Adds {@code ticks} of {@code effect} at {@code amplifier} to however much of the
	 * same strength is already running, or starts it fresh if none is.
	 *
	 * <h2>Only equal strengths are summed</h2>
	 *
	 * <p>Anything else is handed to {@code addEffect} and arbitrated by vanilla's rules,
	 * which are right for that case and which this must not paper over. Summing across
	 * amplifiers has no honest answer: adding three seconds of Regeneration IV to a
	 * golden apple's remaining thirty seconds of Regeneration I would either upgrade half
	 * a minute of slow healing to fast, or slow our own drink to a crawl, and both are
	 * surprises the player did not ask for. Vanilla already solves it properly — it hides
	 * the weaker instance, runs the stronger, and restores the remainder afterwards.
	 *
	 * <p>That case stopped being hypothetical when the health drinks moved onto vanilla
	 * {@code REGENERATION}: golden apples, potions and beacons all grant it, so a player
	 * arriving with one running is ordinary rather than exotic.
	 *
	 * <p>An endless instance is likewise left to vanilla. There is nothing sensible to
	 * add to it, and replacing it would cut short a source that meant to last.
	 */
	public static void extend(LivingEntity entity, Holder<MobEffect> effect, int ticks,
			int amplifier) {
		if (ticks <= 0) {
			return;
		}

		MobEffectInstance active = entity.getEffect(effect);

		if (active == null || active.isInfiniteDuration() || active.getAmplifier() != amplifier) {
			entity.addEffect(new MobEffectInstance(effect, ticks, amplifier));
			return;
		}

		// Removed first because addEffect would take the longer duration rather than the
		// sum, which is the whole reason this class exists.
		entity.removeEffect(effect);
		entity.addEffect(new MobEffectInstance(effect, ticks + active.getDuration(), amplifier));
	}
}

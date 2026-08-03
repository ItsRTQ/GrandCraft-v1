package com.hrtq.grandcraft.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * Mana regeneration: a timed marker saying this character's mana comes back faster
 * than usual, and by how much.
 *
 * <h2>It deliberately does not grant the mana itself</h2>
 *
 * <p><strong>{@code applyEffectTick} is not overridden, and that is the design rather
 * than an omission.</strong> Do not "fix" it by writing mana from here.
 *
 * <p>The pool is advanced once per tick by {@code CombatController.tickMana}, which is
 * also the only thing that tells the client about it. Granting mana here as well would
 * make two writers for one number, and — the part that actually breaks — the client
 * <em>extrapolates</em> the bar forward from the rate the sync packet carries. Mana
 * arriving from a source the sync knew nothing about would draw a bar that sat still
 * and then jumped every time a packet landed. Same rule as the scaled jump cost the
 * stamina payload carries: ship the effective number, never the configured one.
 *
 * <p>So this class exists to be <em>read</em>. What it buys by being a real
 * {@link MobEffect} rather than a timer on the mana attachment is everything vanilla
 * already does for status effects: the inventory icon and countdown, the particles, the
 * potion-style tooltip on the item, cure/removal by milk, and the sync to the client —
 * none of which would be worth hand-building.
 *
 * <h2>The rate</h2>
 *
 * <p>Baked here rather than in {@code /grandcraft config stats}, which is the same call
 * the weapons made for damage and reach: a number describing a specific object rather
 * than a rule of the ruleset. Changing it costs a rebuild.
 */
public class ManaRegenEffect extends MobEffect {
	/**
	 * Mana restored per tick at amplifier 0, i.e. Mana Regeneration I.
	 *
	 * <p>Twenty ticks to the second, so 0.5 here is <strong>10 mana per second</strong>:
	 * a three second drink returns 30 against a default pool of 100.
	 *
	 * <p><strong>Ten per second is not an arbitrary round number.</strong> A gust costs 8
	 * mana on a 16 tick cooldown, which is 10 mana a second — so this rate exactly
	 * cancels the drain of a staff cast as fast as its base cooldown allows. A vial
	 * therefore buys three seconds of casting that costs nothing, which is a far more
	 * legible thing to hand a player than a number that merely refills them. The
	 * cancellation is exact only at base cooldown; Arcane shortens it, so a heavy
	 * investment still outspends a vial.
	 *
	 * <p>Keep it a rate that divides into twenty cleanly. The sync packet carries mana
	 * per <em>second</em> as an {@code int} and the client extrapolates the bar from it,
	 * so a rate like 0.375/tick would be sent as 8 rather than 7.5 and the bar would
	 * creep ahead of the server between packets.
	 */
	public static final float MANA_PER_TICK = 0.5F;

	public ManaRegenEffect(MobEffectCategory category, int color) {
		super(category, color);
	}

	/**
	 * What this character's mana regeneration is worth per tick right now, or zero when
	 * they have none.
	 *
	 * <p>Scales with the amplifier so a stronger potion is one number rather than a
	 * second effect. Additive, and deliberately <em>not</em> multiplied by the Arcane
	 * recovery multiplier: that multiplier is zero for anyone below the threshold, and a
	 * potion that did nothing for a non-caster would be a potion reported as broken.
	 * Being drinkable by anyone is the whole point — it is the only mana a
	 * non-caster can get.
	 */
	public static float bonusPerTick(LivingEntity entity) {
		MobEffectInstance instance = entity.getEffect(GrandCraftEffects.MANA_REGEN);

		return instance == null ? 0.0F : MANA_PER_TICK * (instance.getAmplifier() + 1);
	}
}

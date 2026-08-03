package com.hrtq.grandcraft.item;

import com.hrtq.grandcraft.effect.StackingEffects;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A drinkable that restores health: some at once, some over the seconds that follow.
 *
 * <p>The mana drinks' twin, and shaped the same way on purpose — an instant amount of
 * zero means "purely a timed drink", which is the vial.
 *
 * <p>Simpler than {@link ManaPotionItem} in one respect: healing needs no controller.
 * A character's health ceiling is {@code getMaxHealth()}, which vanilla already keeps
 * correct through every modifier the mod adds to it — Constitution's bonus and bought
 * attribute points included — so {@code heal} clamps itself and there is no second
 * source of truth to consult.
 *
 * <p>The timed half stacks by adding durations rather than refreshing them; see
 * {@link StackingEffects} for why that cannot be left to vanilla, and why the
 * {@code CONSUMABLE} component therefore carries no consume effect.
 *
 * <h2>The timed half is vanilla Regeneration, not an effect of ours</h2>
 *
 * <p>Healing over time is something Minecraft already does, and it does it at exactly
 * the rate wanted here — see {@link #REGEN_AMPLIFIER}. Defining a {@code health_regen}
 * effect would have been {@code RegenerationMobEffect} copied out under a new name, and
 * worse than redundant: two effects that heal identically but ignore each other's
 * durations, so a golden apple and a health vial would run side by side at double rate
 * instead of vanilla arbitrating between them.
 *
 * <p>Mana keeps its own effect for the opposite reason — nothing in vanilla restores a
 * resource vanilla does not have.
 */
public class HealthPotionItem extends Item {
	/**
	 * Vanilla Regeneration heals once every {@code 50 >> amplifier} ticks, so amplifier 3
	 * — Regeneration IV — is one health every six ticks: <strong>ten health over three
	 * seconds</strong>, which is what the vial is worth.
	 *
	 * <p>Six divides sixty exactly, and that matters. The duration is tested with
	 * {@code duration % interval == 0} <em>before</em> it is decremented, so a 60 tick
	 * effect is tested at 60 down to 1 and heals on ten of them — never nine, never
	 * eleven, and exactly thirty when three vials stack it to 180. The neighbouring
	 * amplifiers are not interchangeable: III heals every 12 ticks and V every 3, so
	 * either would need a different duration to reach a whole number.
	 */
	private static final int REGEN_AMPLIFIER = 3;

	private final float instantHealth;
	private final int regenTicks;

	public HealthPotionItem(Properties properties, float instantHealth, int regenTicks) {
		super(properties);
		this.instantHealth = instantHealth;
		this.regenTicks = regenTicks;
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		// Super first: it is what consumes the stack and runs the component's own drink
		// behaviour, and it must run whichever halves this particular drink has.
		ItemStack result = super.finishUsingItem(stack, level, entity);

		// Server only. Health and status effects are both replicated from the server, and
		// the client runs this same method to predict the drink — healing here would move
		// a value the next update would immediately contradict.
		if (level.isClientSide()) {
			return result;
		}

		StackingEffects.extend(entity, MobEffects.REGENERATION, this.regenTicks, REGEN_AMPLIFIER);

		if (this.instantHealth > 0.0F) {
			entity.heal(this.instantHealth);
		}

		return result;
	}
}

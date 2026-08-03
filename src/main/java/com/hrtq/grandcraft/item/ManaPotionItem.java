package com.hrtq.grandcraft.item;

import com.hrtq.grandcraft.combat.CombatController;
import com.hrtq.grandcraft.combat.GrandCraftCombat;
import com.hrtq.grandcraft.effect.GrandCraftEffects;
import com.hrtq.grandcraft.effect.StackingEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A drinkable that restores mana: some at once, some over the seconds that follow.
 *
 * <p>An instant amount of zero is legal and means "purely a timed drink" — the vial
 * takes that path and needs no second item class.
 *
 * <h2>Both halves are applied here, and the timed one has to be</h2>
 *
 * <p>The obvious way to attach the effect is an {@code ApplyStatusEffectsConsumeEffect}
 * on the {@code CONSUMABLE} component, and that is how this was first written. It had
 * to go, because it applies the effect through {@code LivingEntity.addEffect}, whose
 * rule is <strong>refresh, never extend</strong> — see {@link StackingEffects}, which is
 * what this uses instead. Three vials is nine seconds is ninety mana, and a vial drunk
 * on top of a potion adds to it rather than replacing it.
 *
 * <p>The cost of leaving the component behind is the free potion-style tooltip line the
 * consume effect used to draw. Little is lost: with durations stacking, a line reading
 * a flat "0:03" would be wrong as soon as anyone drank two, and the live countdown on
 * the effect in the inventory is both accurate and already there.
 *
 * <h2>Why the instant half goes through the controller</h2>
 *
 * A character's mana ceiling is the configured pool plus whatever they bought with
 * attribute points, and that bonus lives on their {@link CombatController}. Reaching
 * for {@code ManaPool} directly from here would mean guessing at a ceiling, and
 * guessing low would silently cap a potion for exactly the characters who invested in
 * having more mana to fill.
 */
public class ManaPotionItem extends Item {
	/**
	 * Every mana drink runs at the base rate; the potion is distinguished by its instant
	 * chunk rather than by a stronger tail. Named rather than inlined so that adding a
	 * stronger drink is an obvious edit here instead of a bare number at a call site.
	 */
	private static final int MANA_REGEN_AMPLIFIER = 0;

	private final float instantMana;
	private final int regenTicks;

	public ManaPotionItem(Properties properties, float instantMana, int regenTicks) {
		super(properties);
		this.instantMana = instantMana;
		this.regenTicks = regenTicks;
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		// Super first: it is what consumes the stack and runs the component's own drink
		// behaviour, and it must run whichever halves this particular drink has.
		ItemStack result = super.finishUsingItem(stack, level, entity);

		// Server only. Mana and status effects are both server state, and the client runs
		// this same method to predict the drink — acting on it here would move numbers the
		// next sync packet would immediately contradict.
		if (level.isClientSide()) {
			return result;
		}

		StackingEffects.extend(entity, GrandCraftEffects.MANA_REGEN, this.regenTicks,
				MANA_REGEN_AMPLIFIER);

		if (this.instantMana > 0.0F) {
			CombatController controller = GrandCraftCombat.controllerOf(entity);

			if (controller != null) {
				controller.restoreMana(entity, this.instantMana);
			}
		}

		return result;
	}

}

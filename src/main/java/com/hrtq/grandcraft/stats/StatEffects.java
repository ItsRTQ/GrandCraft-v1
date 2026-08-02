package com.hrtq.grandcraft.stats;

import com.hrtq.grandcraft.GrandCraft;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Turns stat values into the vanilla attributes they modify.
 *
 * <p>Only Constitution has anything to express <em>here</em>, which is not the same
 * as being the only stat that does anything. A stat earns an attribute modifier only
 * when its effect is a standing change to the character; the others are applied at
 * the point of use instead, because that is where a whole-number config value can
 * still express a slight per-point effect without rounding it away. Agility goes
 * through {@link StaminaScaling} and Arcane through {@link ArcaneScaling}.
 *
 * <p>Strength alone is still inert: it is registered and shown, and waits on the
 * weapon layer to give it something to scale.
 *
 * <p>Each effect carries its own {@link Identifier}, distinct from the eight
 * {@code CombatController} owns, so a stat bonus composes with a rolled stat or a
 * stagger penalty rather than replacing it.
 *
 * <p>Nothing here decides <em>when</em> to run. {@code CombatController} drives it
 * from the one per-entity attribute pass it already owns, so there is never a second
 * source of truth about which modifiers are currently on an entity.
 */
public final class StatEffects {
	private static final Identifier ARMOUR_ID = GrandCraft.id("stat_armour");
	private static final Identifier HEALTH_ID = GrandCraft.id("stat_health");

	/**
	 * Bought health rides on its own identifier rather than being folded into
	 * {@link #HEALTH_ID}, so the two compose instead of overwriting each other. They
	 * come from different places — one from a stat that can move on its own, one from
	 * points the player committed — and a single modifier would let a Constitution
	 * change silently wipe a purchase.
	 */
	private static final Identifier POOL_HEALTH_ID = GrandCraft.id("pool_health");

	private StatEffects() {
	}

	/**
	 * A stat's current value, gear and effects included, or
	 * {@link StatConstants#NEUTRAL} for anything that has no stats at all.
	 *
	 * <p>The fallback is not defensive padding: only players carry these attributes,
	 * so every mob reaching a stat-aware code path lands here, and neutral is the
	 * value defined to change nothing.
	 *
	 * <p>Reads the modified value rather than the base one, so an item granting
	 * {@code +2 Strength} will count the day such an item exists, with no change here.
	 */
	public static double statOf(LivingEntity entity, Holder<Attribute> stat) {
		AttributeInstance instance = entity.getAttribute(stat);
		return instance == null ? StatConstants.NEUTRAL : instance.getValue();
	}

	/**
	 * Applies Constitution's armour and maximum health to an entity.
	 *
	 * <p>Deliberately does not touch current health. Gaining Constitution raises the
	 * ceiling and leaves the player to fill it — healing on a stat change would make
	 * re-equipping gear a heal. The one exception is a respawn, where vanilla has
	 * already set health to a maximum that did not yet include this bonus; that
	 * top-up lives in {@code GrandCraftStats}, next to the event that causes it.
	 */
	public static void apply(LivingEntity entity, StatSettings settings, PoolBlock spentPools) {
		double constitution = statOf(entity, GrandCraftAttributes.CONSTITUTION);

		// Absolute points rather than a multiplier, for the same reason the rolled
		// defence stat is: a player's base armour is 0, and any multiple of 0 is 0.
		// Vanilla clamps the finished attribute at 30 including worn gear, so a large
		// bonus here is quietly capped rather than rejected.
		add(entity, Attributes.ARMOR, ARMOUR_ID, settings.armourBonus(constitution));
		add(entity, Attributes.MAX_HEALTH, HEALTH_ID, settings.healthBonus(constitution));

		// Health bought outright with attribute points. Like the Constitution bonus it
		// raises the ceiling without healing — see the class note above.
		add(entity, Attributes.MAX_HEALTH, POOL_HEALTH_ID, settings.poolHealthBonus(spentPools));
	}

	private static void add(LivingEntity entity, Holder<Attribute> attribute,
			Identifier id, double amount) {
		AttributeInstance instance = entity.getAttribute(attribute);

		if (instance != null) {
			instance.addOrUpdateTransientModifier(
					new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
		}
	}
}

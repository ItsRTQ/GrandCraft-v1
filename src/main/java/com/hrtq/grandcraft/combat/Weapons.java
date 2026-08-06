package com.hrtq.grandcraft.combat;

import net.minecraft.world.item.ItemStack;

/**
 * Turns a held item into the attack it produces.
 *
 * <p>The one place the weapon layer and the combat state machine meet.
 * {@link WeaponCategory} says what kind of thing is being held, {@link WeaponTuning}
 * says what that kind costs, and this puts the two together into the
 * {@link WeaponProfile} the controller latches for the swing.
 */
public final class Weapons {
	private Weapons() {
	}

	/**
	 * The attack the given stack produces for an actor that fights with weapons.
	 *
	 * @param stack what is in the actor's main hand; may be empty
	 */
	public static WeaponProfile profileFor(ItemStack stack) {
		WeaponCategory category = WeaponCategory.of(stack);
		CategorySettings values = WeaponTuning.current().forCategory(category);

		// Startup used to be forced to zero here, because the player had no attack pose
		// and a wind-up nobody can see is indistinguishable from lag. The pose arrived on
		// 2026-08-05 and the literal went with it — these are the animator's telegraphs
		// now, and every one of these three figures has been configured and persisted in
		// CategorySettings since long before anything read them.
		return new WeaponProfile(category,
				new AttackProfile(values.startupTicks(), values.activeTicks(),
						values.recoveryTicks()),
				values.staminaCost());
	}
}

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

		// Startup is forced to zero for exactly the reason CombatProfiles does the same
		// to any actor without PHASED_MELEE: the player has no attack pose yet, and a
		// wind-up nobody can see is indistinguishable from lag. The configured value is
		// carried in CategorySettings and persisted, so the slice that adds player
		// attack phases is three edits — delete the literal below, point the player's
		// attack path at beginAttack instead of enterRecoveryOnly, and give PLAYER the
		// PHASED_MELEE verb. Nothing else in the state machine has to learn anything.
		//
		// The hit window is passed through rather than zeroed because AttackProfile
		// refuses anything under one tick, and it costs nothing while startup is zero:
		// the player jumps straight to recovery, so no active window ever opens.
		return new WeaponProfile(category,
				new AttackProfile(0, values.activeTicks(), values.recoveryTicks()),
				values.staminaCost());
	}
}

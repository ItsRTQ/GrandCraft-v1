package com.hrtq.grandcraft.combat;

import com.hrtq.grandcraft.item.GrandCraftComponents;
import com.hrtq.grandcraft.stats.CharacterStat;
import com.hrtq.grandcraft.stats.WeaponRequirement;
import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.minecraft.world.item.Items;

/**
 * The handful of vanilla weapons the derived requirement gets wrong.
 *
 * <p>Almost every vanilla weapon is gated correctly by
 * {@link WeaponConstants#REQUIREMENT_SLOPE} without anyone writing anything down —
 * that is the whole point of deriving it from the weapon's own damage, and it is what
 * makes a sword from an unpatched mod arrive already gated. These two are the
 * exceptions, and both are exceptions for a reason the formula structurally cannot
 * see.
 *
 * <p>Applied as the same component the mod's own weapons carry, rather than as a
 * lookup table consulted at damage time. One resolution rule, one place to look, and
 * the client's tooltip reads the answer off the stack with no packet involved.
 *
 * <p><strong>Keep this list short.</strong> Every entry is a claim that the derived
 * value is wrong, and a long list means the formula is wrong instead.
 */
public final class VanillaWeaponRequirements {

	private VanillaWeaponRequirements() {
	}

	public static void register() {
		DefaultItemComponentEvents.MODIFY.register(context -> {
			// Copper's attack bonus is 1.0, byte for byte the same as stone's, so a
			// copper sword and a stone sword are numerically the same weapon and no
			// damage-derived formula could ever separate them. Copper sits between stone
			// and iron everywhere else in the game, so it is gated between them here.
			context.modify(Items.COPPER_SWORD, builder -> builder.set(
					GrandCraftComponents.WEAPON_REQUIREMENT,
					new WeaponRequirement(CharacterStat.STRENGTH, 6)));

			// The mace's 6 damage would derive a Strength 8 gate — the same as an iron
			// sword, and below the claymore's 14 — which would make the game's heaviest
			// weapon its most accessible one. It is in heavy_weapons, so it scales off
			// pure Strength; it should demand pure Strength to match.
			context.modify(Items.MACE, builder -> builder.set(
					GrandCraftComponents.WEAPON_REQUIREMENT,
					new WeaponRequirement(CharacterStat.STRENGTH, 13)));
		});
	}
}

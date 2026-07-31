package com.hrtq.grandcraft.combat;

import com.hrtq.grandcraft.GrandCraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/**
 * Item tags GrandCraft asks questions of.
 */
public final class GrandCraftTags {
	/**
	 * What may be used to guard with, when there is no shield in the off-hand.
	 *
	 * <p>A tag rather than a data component test. The obvious component,
	 * {@code DataComponents.WEAPON}, turns out to mean "loses durability when it hits
	 * something" rather than "is a weapon" — {@code ToolMaterial.applyToolProperties}
	 * puts it on every pickaxe, shovel and hoe as well as on swords and axes, so
	 * testing for it let a player guard with a pickaxe.
	 *
	 * <p>A tag is also the right answer for a mod: what counts as a weapon is a
	 * judgement about the game, not a fact about the item, and a datapack can widen or
	 * narrow it without touching this code. The default is in
	 * {@code data/grandcraft/tags/item/guard_implements.json}.
	 */
	public static final TagKey<Item> GUARD_IMPLEMENTS =
			TagKey.create(Registries.ITEM, GrandCraft.id("guard_implements"));

	private GrandCraftTags() {
	}
}

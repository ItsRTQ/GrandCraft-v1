package com.hrtq.grandcraft.item;

import com.hrtq.grandcraft.GrandCraft;
import com.hrtq.grandcraft.stats.WeaponRequirement;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * The mod's own data components.
 *
 * <p>Same registration convention as everything else here: static fields plus a no-op
 * {@link #register()} whose only job is to force this class to initialise at a moment
 * of our choosing. It must run <em>before</em> {@link GrandCraftItems}, whose item
 * properties reference {@link #WEAPON_REQUIREMENT} directly.
 */
public final class GrandCraftComponents {

	/**
	 * What a character must have before this weapon works properly in their hands.
	 *
	 * <p>A component rather than a config entry because a requirement is a fact about
	 * one specific weapon — the counterpart to the scaling weights, which describe a
	 * whole class and therefore live in the category config. The split is the same one
	 * {@code CategorySettings} already draws between an item's damage and its category's
	 * endlag.
	 *
	 * <p><strong>Persistent and network-synchronised, both deliberately.</strong>
	 * Persistent so a datapack or an anvil-shaped future feature can write one onto a
	 * stack and have it survive; synchronised so the client can draw the tooltip from
	 * the stack it already has, with no packet of ours in the loop. Items that carry it
	 * as a <em>default</em> component — every weapon here, plus the vanilla overrides in
	 * {@code VanillaWeaponRequirements} — get both for free and cost nothing on the wire.
	 */
	public static final DataComponentType<WeaponRequirement> WEAPON_REQUIREMENT = Registry.register(
			BuiltInRegistries.DATA_COMPONENT_TYPE, GrandCraft.id("weapon_requirement"),
			DataComponentType.<WeaponRequirement>builder()
					.persistent(WeaponRequirement.CODEC)
					.networkSynchronized(WeaponRequirement.STREAM_CODEC)
					.build());

	private GrandCraftComponents() {
	}

	/** No-op: exists to force this class to initialise from {@code onInitialize}. */
	public static void register() {
	}
}

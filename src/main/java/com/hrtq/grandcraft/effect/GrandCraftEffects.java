package com.hrtq.grandcraft.effect;

import com.hrtq.grandcraft.GrandCraft;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * The mod's own status effects.
 *
 * <p>Registered through {@code Registry.registerForHolder}, which is what vanilla's own
 * {@code MobEffects.register} does — a plain {@code Registry.register} returns the
 * effect rather than a {@link Holder}, and everything that applies or reads an effect
 * wants the holder.
 */
public final class GrandCraftEffects {
	/**
	 * The mana bar's own blue, so the effect's particles and its inventory swirl match
	 * the thing they refill.
	 *
	 * <p>Six digits, not eight: a {@code MobEffect}'s colour is plain RGB and has no
	 * alpha byte. That is the opposite of the GUI convention elsewhere in this mod,
	 * where leaving {@code 0xFF} off makes a colour fully transparent.
	 */
	private static final int MANA_COLOUR = 0x4C7DF0;

	/**
	 * Mana regeneration, and the only effect this mod needs to define.
	 *
	 * <p><strong>There is deliberately no health equivalent.</strong> Healing over time
	 * is something Minecraft already does, and vanilla {@code REGENERATION} at amplifier
	 * 3 heals once every {@code 50 >> 3} = 6 ticks — the exact rate the health drinks
	 * want. A custom effect was written first and thrown away: it was a copy of
	 * {@code RegenerationMobEffect} under a different name, and it would have meant the
	 * mod's drinks and vanilla's potions ignoring each other's durations instead of
	 * arbitrating properly. Mana gets its own effect only because mana is the mod's own
	 * resource and nothing in vanilla restores it.
	 */
	public static final Holder<MobEffect> MANA_REGEN = Registry.registerForHolder(
			BuiltInRegistries.MOB_EFFECT,
			GrandCraft.id("mana_regen"),
			new ManaRegenEffect(MobEffectCategory.BENEFICIAL, MANA_COLOUR));

	private GrandCraftEffects() {
	}

	/** No-op: exists to force this class to initialise from {@code onInitialize}. */
	public static void register() {
	}
}

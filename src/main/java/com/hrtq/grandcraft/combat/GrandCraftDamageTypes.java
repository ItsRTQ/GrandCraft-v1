package com.hrtq.grandcraft.combat;

import com.hrtq.grandcraft.GrandCraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;

/**
 * The mod's own damage types.
 *
 * <p>Damage types are data, not code: the key here names an entry the game loads
 * from {@code data/grandcraft/damage_type/}, and what it does is decided by that
 * file and by which vanilla damage-type tags it appears in.
 */
public final class GrandCraftDamageTypes {
	/**
	 * The staff's gust.
	 *
	 * <h2>Why not {@code DamageSources.indirectMagic}</h2>
	 * That would be one line and no files, and it is the wrong answer:
	 * {@code minecraft:indirect_magic} is a member of {@code bypasses_armor}, so the
	 * sorcerer's basic attack would ignore armour, ignore the Constitution armour
	 * bonus, and ignore every mob's rolled defence — a balance rule inherited by
	 * accident from a type built for potions. Copy the reason, not the numbers.
	 *
	 * <p>Ours is in <strong>no tags at all</strong>, which is the point. Armour
	 * applies, the guard covers it because the projectile is a real entity with a
	 * position, and dodge invulnerability applies. It behaves like a thrown thing,
	 * because it is one.
	 */
	public static final ResourceKey<DamageType> GUST =
			ResourceKey.create(Registries.DAMAGE_TYPE, GrandCraft.id("gust"));

	private GrandCraftDamageTypes() {
	}
}

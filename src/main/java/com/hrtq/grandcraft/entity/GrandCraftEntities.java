package com.hrtq.grandcraft.entity;

import com.hrtq.grandcraft.GrandCraft;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * The mod's own entities.
 *
 * <p>{@link #SUMMONABLE} is the list {@code /grandcraft summon} completes against
 * and resolves from, so a new mob becomes summonable by being added here rather
 * than by touching the command.
 */
public final class GrandCraftEntities {
	/**
	 * Name to type, in declaration order, for the summon command.
	 *
	 * <p><strong>Declared before any entity below it.</strong> Static initialisers run
	 * in source order, and each entity's initialiser calls {@link #register}, which
	 * writes into this map — so a field declared afterwards would still be null and
	 * the class would fail to initialise.
	 */
	private static final Map<String, EntityType<?>> SUMMONABLE = new LinkedHashMap<>();

	public static final EntityType<ZombieHumanEntity> ZOMBIE_HUMAN =
			registerSummonable("zombie_human", EntityType.Builder.of(ZombieHumanEntity::new, MobCategory.MONSTER)
					// Vanilla zombie proportions: the model is the vanilla humanoid rig, so
					// anything else would leave the hitbox disagreeing with what is drawn.
					.sized(0.6F, 1.95F)
					.eyeHeight(1.74F)
					.clientTrackingRange(8));

	/**
	 * The dropped form of Essence Power.
	 *
	 * <p>Registered through {@link #register} rather than
	 * {@link #registerSummonable}: it is a pickup, not a mob, and offering it as a
	 * {@code /grandcraft summon} target would be offering a way to mint progression
	 * out of nothing.
	 *
	 * <p>Vanilla's experience orb proportions, which is what the movement and
	 * attraction behaviour in {@link LifeEssenceOrb} is modelled on.
	 */
	public static final EntityType<LifeEssenceOrb> LIFE_ESSENCE_ORB =
			register("life_essence_orb", EntityType.Builder.<LifeEssenceOrb>of(LifeEssenceOrb::new, MobCategory.MISC)
					.sized(0.5F, 0.5F)
					.clientTrackingRange(6)
					.updateInterval(20));

	/**
	 * The staff's default attack.
	 *
	 * <p>Registered through {@link #register} rather than
	 * {@link #registerSummonable}: it is a projectile rather than a mob, and the
	 * narrow {@code /grandcraft summon} exists for things with no other way to be
	 * reached. Vanilla's own {@code /summon} still works on it, which is how it is
	 * tested before the staff can fire one.
	 *
	 * <p>A shorter tracking range and a faster update interval than the essence orb:
	 * this moves at 1.5 blocks a tick, so a 20-tick update would have clients
	 * extrapolating a straight line for a whole second of flight.
	 */
	public static final EntityType<GustProjectile> GUST =
			register("gust", EntityType.Builder.<GustProjectile>of(GustProjectile::new, MobCategory.MISC)
					.sized(0.3F, 0.3F)
					.clientTrackingRange(4)
					.updateInterval(10));

	private GrandCraftEntities() {
	}

	/** Registers a type without offering it to {@code /grandcraft summon}. */
	private static <T extends Entity> EntityType<T> register(
			String name, EntityType.Builder<T> builder) {
		Identifier id = GrandCraft.id(name);

		return Registry.register(BuiltInRegistries.ENTITY_TYPE, id,
				builder.build(ResourceKey.create(Registries.ENTITY_TYPE, id)));
	}

	private static <T extends Entity> EntityType<T> registerSummonable(
			String name, EntityType.Builder<T> builder) {
		EntityType<T> type = register(name, builder);

		SUMMONABLE.put(name, type);
		return type;
	}

	/** The names {@code /grandcraft summon} accepts. */
	public static Iterable<String> summonableNames() {
		return SUMMONABLE.keySet();
	}

	/** @return the type for a summon name, or null when nothing matches. */
	public static EntityType<?> summonable(String name) {
		return SUMMONABLE.get(name);
	}

	public static void register() {
		// Attributes are registered here rather than in the static block above so the
		// entity types exist before anything is attached to them.
		FabricDefaultAttributeRegistry.register(ZOMBIE_HUMAN, ZombieHumanEntity.createAttributes());
	}
}

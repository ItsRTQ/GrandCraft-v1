package com.hrtq.grandcraft.combat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.RandomSource;

/**
 * The stats one individual actor rolled when it first spawned.
 *
 * <p>Persisted on the entity, so a zombie that rolled tough stays tough across a
 * chunk unload or a restart. Rolling on every load instead would quietly change a
 * mob's difficulty whenever the player walked away and came back.
 *
 * <p>Health, damage and speed are multipliers; defence is absolute armour points.
 * A change to the configured ranges does not re-roll anything already in the
 * world — existing mobs keep what they rolled, and only new spawns use the new
 * band.
 */
public record RolledStats(double health, double damage, double speed, double defence) {

	/** What an actor that does not roll uses: exactly vanilla. */
	public static final RolledStats VANILLA = new RolledStats(1.0, 1.0, 1.0, 0.0);

	public static final Codec<RolledStats> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.DOUBLE.optionalFieldOf("health", VANILLA.health()).forGetter(RolledStats::health),
			Codec.DOUBLE.optionalFieldOf("damage", VANILLA.damage()).forGetter(RolledStats::damage),
			Codec.DOUBLE.optionalFieldOf("speed", VANILLA.speed()).forGetter(RolledStats::speed),
			Codec.DOUBLE.optionalFieldOf("defence", VANILLA.defence()).forGetter(RolledStats::defence)
	).apply(instance, RolledStats::new));

	public static RolledStats roll(CombatProfile profile, RandomSource random) {
		return new RolledStats(
				profile.health().roll(random),
				profile.damage().roll(random),
				profile.speed().roll(random),
				profile.defence().roll(random));
	}
}

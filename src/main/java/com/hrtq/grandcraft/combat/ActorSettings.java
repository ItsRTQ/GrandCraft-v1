package com.hrtq.grandcraft.combat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * The tunable combat values for one {@link CombatActor}.
 *
 * <p>Deliberately small. Everything that is not routinely tuned lives in
 * {@link CombatConstants} instead, so the config screen stays readable — the test
 * for belonging here is "would someone balancing a mob reach for this?", not
 * "could this be a number?".
 *
 * <p>Stamina is a nested group rather than six more fields at this level, so the
 * top level stays scannable and the config screen can show it as its own section.
 *
 * <p>Health, damage and speed are multipliers of whatever the mob's vanilla value
 * is, so one setting means the same thing for a zombie as for anything added
 * later. Defence is absolute armour points because vanilla mobs have none, and a
 * multiplier of zero is still zero.
 */
public record ActorSettings(
		int startupTicks,
		int recoveryTicks,
		int staggerTicks,
		StatRange health,
		StatRange damage,
		StatRange speed,
		StatRange defence,
		StaminaSettings stamina,
		DodgeSettings dodge) {

	/** Bounds shared by the config fields and the server-side clamp. */
	public static final int MAX_PHASE_TICKS = 40;
	public static final int MAX_STAGGER_TICKS = 20;

	/**
	 * Vanilla's {@code MAX_HEALTH} attribute stops at 1024, so 20x clears anything
	 * with a base below about 51 health — every mob currently in the table by a wide
	 * margin. A mob with a large base health would hit the attribute's own ceiling
	 * before this one.
	 */
	public static final double MAX_HEALTH_MULTIPLIER = 20.0;

	/** {@code ATTACK_DAMAGE} allows up to 2048, so nothing here is clipped by it. */
	public static final double MAX_DAMAGE_MULTIPLIER = 20.0;

	/**
	 * The attribute itself allows far more, so this ceiling is about behaviour rather
	 * than range: much past 3x, pathfinding and collision stop keeping up and mobs
	 * start overshooting their target and clipping through corners.
	 */
	public static final double MAX_SPEED_MULTIPLIER = 10.0;

	/**
	 * Vanilla's own hard ceiling on the armour attribute, and not raisable from here
	 * — a larger value would be silently clamped by the attribute. Damage reduction
	 * flattens out around 20 armour anyway.
	 */
	public static final double MAX_DEFENCE = 30.0;

	public static final double VANILLA_MULTIPLIER = 1.0;

	public static Codec<ActorSettings> codec(ActorSettings fallback) {
		return RecordCodecBuilder.create(instance -> instance.group(
				Codec.INT.optionalFieldOf("startup_ticks", fallback.startupTicks())
						.forGetter(ActorSettings::startupTicks),
				Codec.INT.optionalFieldOf("recovery_ticks", fallback.recoveryTicks())
						.forGetter(ActorSettings::recoveryTicks),
				Codec.INT.optionalFieldOf("stagger_ticks", fallback.staggerTicks())
						.forGetter(ActorSettings::staggerTicks),
				StatRange.codec(fallback.health()).optionalFieldOf("health", fallback.health())
						.forGetter(ActorSettings::health),
				StatRange.codec(fallback.damage()).optionalFieldOf("damage", fallback.damage())
						.forGetter(ActorSettings::damage),
				StatRange.codec(fallback.speed()).optionalFieldOf("speed", fallback.speed())
						.forGetter(ActorSettings::speed),
				StatRange.codec(fallback.defence()).optionalFieldOf("defence", fallback.defence())
						.forGetter(ActorSettings::defence),
				StaminaSettings.codec(fallback.stamina()).optionalFieldOf("stamina", fallback.stamina())
						.forGetter(ActorSettings::stamina),
				DodgeSettings.codec(fallback.dodge()).optionalFieldOf("dodge", fallback.dodge())
						.forGetter(ActorSettings::dodge)
		).apply(instance, ActorSettings::new));
	}

	public static final StreamCodec<ByteBuf, ActorSettings> STREAM_CODEC = StreamCodec.of(
			(buf, settings) -> {
				buf.writeInt(settings.startupTicks());
				buf.writeInt(settings.recoveryTicks());
				buf.writeInt(settings.staggerTicks());
				StatRange.STREAM_CODEC.encode(buf, settings.health());
				StatRange.STREAM_CODEC.encode(buf, settings.damage());
				StatRange.STREAM_CODEC.encode(buf, settings.speed());
				StatRange.STREAM_CODEC.encode(buf, settings.defence());
				StaminaSettings.STREAM_CODEC.encode(buf, settings.stamina());
				DodgeSettings.STREAM_CODEC.encode(buf, settings.dodge());
			},
			buf -> new ActorSettings(
					buf.readInt(), buf.readInt(), buf.readInt(),
					StatRange.STREAM_CODEC.decode(buf),
					StatRange.STREAM_CODEC.decode(buf),
					StatRange.STREAM_CODEC.decode(buf),
					StatRange.STREAM_CODEC.decode(buf),
					StaminaSettings.STREAM_CODEC.decode(buf),
					DodgeSettings.STREAM_CODEC.decode(buf)));

	/**
	 * A copy with every value forced inside its bounds. Always applied to anything
	 * arriving over the network or read off disk, so a hand-built packet cannot push
	 * a phase duration out of range and throw inside {@link AttackProfile}.
	 */
	public ActorSettings clamped() {
		return new ActorSettings(
				clamp(this.startupTicks, 0, MAX_PHASE_TICKS),
				clamp(this.recoveryTicks, 0, MAX_PHASE_TICKS),
				clamp(this.staggerTicks, 0, MAX_STAGGER_TICKS),
				this.health.clamped(0.0, MAX_HEALTH_MULTIPLIER),
				this.damage.clamped(0.0, MAX_DAMAGE_MULTIPLIER),
				this.speed.clamped(0.0, MAX_SPEED_MULTIPLIER),
				this.defence.clamped(0.0, MAX_DEFENCE),
				this.stamina.clamped(),
				this.dodge.clamped());
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(value, max));
	}
}

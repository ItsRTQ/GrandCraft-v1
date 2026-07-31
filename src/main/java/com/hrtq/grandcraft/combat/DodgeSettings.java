package com.hrtq.grandcraft.combat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * One actor's dodge: how long it protects, how long it costs afterwards, how far
 * it travels and what it takes out of the stamina pool.
 *
 * <p>The shape of the window is the whole design. Invulnerability starts on the
 * first tick, so a dodge always beats an attack already in flight and panicking out
 * of a wind-up is a legitimate answer. It then <em>ends before the roll does</em>,
 * leaving {@link #recoveryTicks} where the actor is neither protected nor able to
 * act. That tail is what makes the timing a decision: dodge into the wrong moment
 * and the next attack lands on a target that cannot answer.
 *
 * <p>All four values are whole numbers so the config fields stay integer-typed and
 * no locale-dependent decimal separator is introduced — hence {@link #speed} being
 * in hundredths of a block per tick rather than a raw double.
 */
public record DodgeSettings(
		int invulnerableTicks,
		int recoveryTicks,
		int speed,
		int cost) {

	/** Bounds shared by the config fields and the server-side clamp. */
	public static final int MAX_TICKS = 40;

	/**
	 * A ceiling on the launch impulse, in hundredths of a block per tick. Past
	 * roughly this the roll outruns collision checks and starts putting actors
	 * through corners, the same failure the speed multiplier's ceiling guards.
	 */
	public static final int MAX_SPEED = 300;

	public static final int MAX_COST = 1000;

	/** Hundredths of a block per tick, as configured, into blocks per tick. */
	private static final double SPEED_SCALE = 100.0;

	/**
	 * Whether the dodge actually applies. A window of zero on both halves is the
	 * config-level off switch: an actor that has {@link CombatVerb#DODGE} at compile
	 * time can still be returned to no dodge at all by an admin without a rebuild.
	 *
	 * <p>Checked through {@link CombatProfile#usesDodge()}, which combines it with
	 * the verb so no caller has to remember both halves.
	 */
	public boolean enabled() {
		return this.invulnerableTicks > 0 || this.recoveryTicks > 0;
	}

	/** The launch impulse in blocks per tick. */
	public double speedPerTick() {
		return this.speed / SPEED_SCALE;
	}

	public static Codec<DodgeSettings> codec(DodgeSettings fallback) {
		return RecordCodecBuilder.create(instance -> instance.group(
				Codec.INT.optionalFieldOf("invulnerable_ticks", fallback.invulnerableTicks())
						.forGetter(DodgeSettings::invulnerableTicks),
				Codec.INT.optionalFieldOf("recovery_ticks", fallback.recoveryTicks())
						.forGetter(DodgeSettings::recoveryTicks),
				Codec.INT.optionalFieldOf("speed", fallback.speed())
						.forGetter(DodgeSettings::speed),
				Codec.INT.optionalFieldOf("cost", fallback.cost())
						.forGetter(DodgeSettings::cost)
		).apply(instance, DodgeSettings::new));
	}

	public static final StreamCodec<ByteBuf, DodgeSettings> STREAM_CODEC = StreamCodec.of(
			(buf, settings) -> {
				buf.writeInt(settings.invulnerableTicks());
				buf.writeInt(settings.recoveryTicks());
				buf.writeInt(settings.speed());
				buf.writeInt(settings.cost());
			},
			// Java evaluates arguments left to right, so the read order is well defined
			// and matches the writes above.
			buf -> new DodgeSettings(
					buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt()));

	/** A copy with every value forced inside its bounds. */
	public DodgeSettings clamped() {
		return new DodgeSettings(
				clamp(this.invulnerableTicks, 0, MAX_TICKS),
				clamp(this.recoveryTicks, 0, MAX_TICKS),
				clamp(this.speed, 0, MAX_SPEED),
				clamp(this.cost, 0, MAX_COST));
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(value, max));
	}
}

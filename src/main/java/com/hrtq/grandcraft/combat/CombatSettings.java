package com.hrtq.grandcraft.combat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * The tunable combat values, as one immutable snapshot.
 *
 * <p>Immutable and swapped atomically, which is what lets {@link CombatProfiles}
 * detect a change by identity instead of needing an invalidation callback.
 *
 * <p>Fractions are stored as positive magnitudes (0.2 meaning 20%) so the config
 * UI and the JSON file read naturally. {@link CombatTuning} applies the sign when
 * building attribute modifiers.
 */
public record CombatSettings(
		int stagger1Ticks,
		double stagger1SlowFraction,
		int stagger2Ticks,
		double stagger2SlowFraction,
		int staggerResetTicks,
		double staggerJumpPenalty,
		double knockbackResistance,
		int zombieStartupTicks,
		int zombieActiveTicks,
		int zombieRecoveryTicks,
		int playerRecoveryTicks) {

	/** Bounds shared by the config sliders and the server-side clamp. */
	public static final int MAX_STAGGER_TICKS = 20;
	public static final int MAX_RESET_TICKS = 100;
	public static final int MAX_PHASE_TICKS = 40;
	public static final double MAX_SLOW_FRACTION = 0.9;

	/** A full 1.0 is meaningful for both: no jump at all, and total knockback immunity. */
	public static final double MAX_JUMP_PENALTY = 1.0;
	public static final double MAX_KNOCKBACK_RESISTANCE = 1.0;

	/**
	 * An attack with no active window could never connect, so this floor is
	 * structural rather than a matter of taste — {@link AttackProfile} rejects
	 * anything lower.
	 */
	public static final int MIN_ACTIVE_TICKS = 1;

	/**
	 * The Phase 1 starting values. Prototype tuning, not balance.
	 *
	 * <p>The 30 tick stagger reset is deliberate: vanilla invulnerability floors
	 * re-hits near 10 ticks and a sword swings every 12-13, so dropping this much
	 * below 15 makes the strong / weak / none sequence stop forming.
	 */
	public static final CombatSettings DEFAULT = new CombatSettings(
			4, 0.20,
			2, 0.10,
			30,
			0.50,
			0.50,
			8, 3, 10,
			3);

	/**
	 * Every field is optional and falls back to its {@link #DEFAULT} value, so a
	 * config file written by an older build keeps loading as values are added
	 * rather than failing to parse and silently reverting everything.
	 */
	public static final Codec<CombatSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.optionalFieldOf("stagger_1_ticks", DEFAULT.stagger1Ticks())
					.forGetter(CombatSettings::stagger1Ticks),
			Codec.DOUBLE.optionalFieldOf("stagger_1_slow_fraction", DEFAULT.stagger1SlowFraction())
					.forGetter(CombatSettings::stagger1SlowFraction),
			Codec.INT.optionalFieldOf("stagger_2_ticks", DEFAULT.stagger2Ticks())
					.forGetter(CombatSettings::stagger2Ticks),
			Codec.DOUBLE.optionalFieldOf("stagger_2_slow_fraction", DEFAULT.stagger2SlowFraction())
					.forGetter(CombatSettings::stagger2SlowFraction),
			Codec.INT.optionalFieldOf("stagger_reset_ticks", DEFAULT.staggerResetTicks())
					.forGetter(CombatSettings::staggerResetTicks),
			Codec.DOUBLE.optionalFieldOf("stagger_jump_penalty", DEFAULT.staggerJumpPenalty())
					.forGetter(CombatSettings::staggerJumpPenalty),
			Codec.DOUBLE.optionalFieldOf("knockback_resistance", DEFAULT.knockbackResistance())
					.forGetter(CombatSettings::knockbackResistance),
			Codec.INT.optionalFieldOf("zombie_startup_ticks", DEFAULT.zombieStartupTicks())
					.forGetter(CombatSettings::zombieStartupTicks),
			Codec.INT.optionalFieldOf("zombie_active_ticks", DEFAULT.zombieActiveTicks())
					.forGetter(CombatSettings::zombieActiveTicks),
			Codec.INT.optionalFieldOf("zombie_recovery_ticks", DEFAULT.zombieRecoveryTicks())
					.forGetter(CombatSettings::zombieRecoveryTicks),
			Codec.INT.optionalFieldOf("player_recovery_ticks", DEFAULT.playerRecoveryTicks())
					.forGetter(CombatSettings::playerRecoveryTicks)
	).apply(instance, CombatSettings::new));

	// Written by hand rather than via StreamCodec.composite, whose overloads run
	// out well before eleven fields. Java guarantees left-to-right argument
	// evaluation, so the decoder's read order is well defined.
	public static final StreamCodec<ByteBuf, CombatSettings> STREAM_CODEC = StreamCodec.of(
			(buf, settings) -> {
				buf.writeInt(settings.stagger1Ticks());
				buf.writeDouble(settings.stagger1SlowFraction());
				buf.writeInt(settings.stagger2Ticks());
				buf.writeDouble(settings.stagger2SlowFraction());
				buf.writeInt(settings.staggerResetTicks());
				buf.writeDouble(settings.staggerJumpPenalty());
				buf.writeDouble(settings.knockbackResistance());
				buf.writeInt(settings.zombieStartupTicks());
				buf.writeInt(settings.zombieActiveTicks());
				buf.writeInt(settings.zombieRecoveryTicks());
				buf.writeInt(settings.playerRecoveryTicks());
			},
			buf -> new CombatSettings(
					buf.readInt(), buf.readDouble(),
					buf.readInt(), buf.readDouble(),
					buf.readInt(),
					buf.readDouble(),
					buf.readDouble(),
					buf.readInt(), buf.readInt(), buf.readInt(),
					buf.readInt()));

	/**
	 * A copy with every value forced inside its bounds.
	 *
	 * <p>Always applied to anything arriving over the network or read off disk: an
	 * out-of-range active window would otherwise throw inside
	 * {@link AttackProfile}, and a hostile client must not be able to crash the
	 * server with a hand-built packet.
	 */
	public CombatSettings clamped() {
		return new CombatSettings(
				clamp(this.stagger1Ticks, 0, MAX_STAGGER_TICKS),
				clamp(this.stagger1SlowFraction, 0.0, MAX_SLOW_FRACTION),
				clamp(this.stagger2Ticks, 0, MAX_STAGGER_TICKS),
				clamp(this.stagger2SlowFraction, 0.0, MAX_SLOW_FRACTION),
				clamp(this.staggerResetTicks, 0, MAX_RESET_TICKS),
				clamp(this.staggerJumpPenalty, 0.0, MAX_JUMP_PENALTY),
				clamp(this.knockbackResistance, 0.0, MAX_KNOCKBACK_RESISTANCE),
				clamp(this.zombieStartupTicks, 0, MAX_PHASE_TICKS),
				clamp(this.zombieActiveTicks, MIN_ACTIVE_TICKS, MAX_PHASE_TICKS),
				clamp(this.zombieRecoveryTicks, 0, MAX_PHASE_TICKS),
				clamp(this.playerRecoveryTicks, 0, MAX_PHASE_TICKS));
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(value, max));
	}

	private static double clamp(double value, double min, double max) {
		// Rejects NaN too, which would otherwise survive a min/max pair.
		if (!Double.isFinite(value)) {
			return min;
		}

		return Math.max(min, Math.min(value, max));
	}
}

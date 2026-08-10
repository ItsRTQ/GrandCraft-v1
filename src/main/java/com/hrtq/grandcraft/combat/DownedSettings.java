package com.hrtq.grandcraft.combat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * One actor's downed state: how long it has left, how little it can move, and what
 * it takes to end it either way.
 *
 * <p>The shape is a clock with three ends. A blow that would kill instead knocks the
 * actor prone with {@link #bleedOutTicks} on the clock; from there an ally can spend
 * {@link #reviveTicks} standing over them, the actor can give up, or the clock runs
 * out and the death happens after all.
 *
 * <p><strong>The clock is a second health bar denominated in seconds.</strong> Damage
 * taken while down does not reduce health — it takes {@link #ticksLostPerDamage} off
 * the clock instead, and at the shipped 20 that is exactly one second per point of
 * damage: four damage costs four seconds, forty costs forty. The user's own framing
 * (2026-08-09) — <em>"the seconds the player has left are the health he has left"</em>.
 * So a mob standing over a downed player is a real threat, reviving under fire is a
 * gamble, and how long someone has is a number their ally can estimate by looking at
 * what is hitting them.
 *
 * <p>Every value is a whole number, following {@link DodgeSettings}: the config
 * fields stay integer-typed and no locale-dependent decimal separator is introduced.
 * That is why the two fractions are stored as whole percent and the reach in tenths
 * of a block.
 */
public record DownedSettings(
		int bleedOutTicks,
		int movePercent,
		int ticksLostPerDamage,
		int reviveTicks,
		int reviveReach,
		int reviveHealthPercent,
		int giveUpHoldTicks) {

	/** Ten minutes. Well past anything playable, and short of an integer overflow. */
	public static final int MAX_BLEED_OUT_TICKS = 12000;

	/** 100 is full speed, which is the setting for "prone but not slowed". */
	public static final int MAX_MOVE_PERCENT = 100;

	/** Twenty seconds off the clock for a single point of damage. */
	public static final int MAX_TICKS_LOST_PER_DAMAGE = 400;

	public static final int MAX_REVIVE_TICKS = 400;

	/** Tenths of a block. Ten blocks is far past any reasonable reach. */
	public static final int MAX_REVIVE_REACH = 100;

	public static final int MAX_REVIVE_HEALTH_PERCENT = 100;

	public static final int MAX_GIVE_UP_HOLD_TICKS = 200;

	private static final double PERCENT = 100.0;
	private static final double TENTHS = 10.0;

	/**
	 * Whether the state applies at all. A bleed-out of zero is the config-level off
	 * switch: an actor that has {@link CombatVerb#DOWNED} at compile time can be
	 * returned to plain instant death by an admin without a rebuild.
	 *
	 * <p>Checked through {@link CombatProfile#usesDowned()}, which combines it with the
	 * verb so no caller has to remember both halves.
	 */
	public boolean enabled() {
		return this.bleedOutTicks > 0;
	}

	/** The movement multiplier, 0.07 at the shipped 7. */
	public double moveFraction() {
		return this.movePercent / PERCENT;
	}

	/** What a revived actor gets back, as a fraction of its maximum health. */
	public double reviveHealthFraction() {
		return this.reviveHealthPercent / PERCENT;
	}

	/** How close a reviver has to be, in blocks. */
	public double reviveReachBlocks() {
		return this.reviveReach / TENTHS;
	}

	/**
	 * The clock left after taking this much damage, never below zero.
	 *
	 * <p>Rounded down, so a scratch that does not amount to a whole tick costs
	 * nothing — the alternative is a hit for a fraction of a heart taking a tick off,
	 * which over a long fight would be a clock nobody could predict.
	 *
	 * <p><strong>The amount is the blow before armour.</strong> It has to be: the hit is
	 * vetoed before vanilla resolves any of its reductions, so there is no mitigated
	 * figure to read. The consequence is that armour does not protect the clock, which
	 * is a defensible reading of being stamped on while prone rather than an oversight —
	 * but it is the reason a downed player in full plate loses time as fast as one in
	 * none.
	 */
	public int bleedOutAfterDamage(int remaining, float amount) {
		if (amount <= 0.0F || this.ticksLostPerDamage <= 0) {
			return remaining;
		}

		return Math.max(0, remaining - (int) (amount * this.ticksLostPerDamage));
	}

	public static Codec<DownedSettings> codec(DownedSettings fallback) {
		return RecordCodecBuilder.create(instance -> instance.group(
				Codec.INT.optionalFieldOf("bleed_out_ticks", fallback.bleedOutTicks())
						.forGetter(DownedSettings::bleedOutTicks),
				Codec.INT.optionalFieldOf("move_percent", fallback.movePercent())
						.forGetter(DownedSettings::movePercent),
				Codec.INT.optionalFieldOf("ticks_lost_per_damage", fallback.ticksLostPerDamage())
						.forGetter(DownedSettings::ticksLostPerDamage),
				Codec.INT.optionalFieldOf("revive_ticks", fallback.reviveTicks())
						.forGetter(DownedSettings::reviveTicks),
				Codec.INT.optionalFieldOf("revive_reach", fallback.reviveReach())
						.forGetter(DownedSettings::reviveReach),
				Codec.INT.optionalFieldOf("revive_health_percent", fallback.reviveHealthPercent())
						.forGetter(DownedSettings::reviveHealthPercent),
				Codec.INT.optionalFieldOf("give_up_hold_ticks", fallback.giveUpHoldTicks())
						.forGetter(DownedSettings::giveUpHoldTicks)
		).apply(instance, DownedSettings::new));
	}

	public static final StreamCodec<ByteBuf, DownedSettings> STREAM_CODEC = StreamCodec.of(
			(buf, settings) -> {
				buf.writeInt(settings.bleedOutTicks());
				buf.writeInt(settings.movePercent());
				buf.writeInt(settings.ticksLostPerDamage());
				buf.writeInt(settings.reviveTicks());
				buf.writeInt(settings.reviveReach());
				buf.writeInt(settings.reviveHealthPercent());
				buf.writeInt(settings.giveUpHoldTicks());
			},
			// Java evaluates arguments left to right, so the read order is well defined
			// and matches the writes above.
			buf -> new DownedSettings(
					buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(),
					buf.readInt(), buf.readInt(), buf.readInt()));

	/** A copy with every value forced inside its bounds. */
	public DownedSettings clamped() {
		return new DownedSettings(
				clamp(this.bleedOutTicks, 0, MAX_BLEED_OUT_TICKS),
				clamp(this.movePercent, 0, MAX_MOVE_PERCENT),
				clamp(this.ticksLostPerDamage, 0, MAX_TICKS_LOST_PER_DAMAGE),
				clamp(this.reviveTicks, 0, MAX_REVIVE_TICKS),
				clamp(this.reviveReach, 0, MAX_REVIVE_REACH),
				clamp(this.reviveHealthPercent, 0, MAX_REVIVE_HEALTH_PERCENT),
				clamp(this.giveUpHoldTicks, 0, MAX_GIVE_UP_HOLD_TICKS));
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(value, max));
	}
}

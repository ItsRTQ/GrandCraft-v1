package com.hrtq.grandcraft.combat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * One actor's guard: how long it takes to raise and to drop, what it costs to hold
 * and to absorb with, how wide it covers, and what breaking it costs.
 *
 * <p>The guard is the safe answer to a telegraph, and its shape is deliberately the
 * opposite of the dodge's. A dodge protects from its very first tick and pays for it
 * with a vulnerable tail; a guard pays <em>up front</em> with {@link #raiseTicks},
 * during which nothing is stopped at all. That is what keeps it from being a free
 * instant-react button: reading the wind-up early is rewarded, and slapping the key
 * the moment something moves is not.
 *
 * <p>Absorbing costs stamina in proportion to the damage stopped, so the decision at
 * every incoming hit is simply "can I afford this one?". Running the pool dry still
 * absorbs the hit that emptied it — the punishment is the guard break that follows,
 * not damage sneaking through.
 *
 * <p>All eight values are whole numbers so the config fields stay integer-typed and
 * no locale-dependent decimal separator is introduced — hence {@link #costPerDamage}
 * being in hundredths of a stamina point rather than a raw float.
 */
public record BlockSettings(
		int raiseTicks,
		int recoveryTicks,
		int breakTicks,
		int costPerDamage,
		int holdCostPerSecond,
		int arcDegrees,
		int moveSlowPercent,
		int shieldCostPercent) {

	/** Bounds shared by the config fields and the server-side clamp. */
	public static final int MAX_TICKS = 40;

	/** Fifty stamina a point of damage is already far past a pool's worth per hit. */
	public static final int MAX_COST_PER_DAMAGE = 5000;

	public static final int MAX_HOLD_COST = 200;

	/**
	 * A half-angle, measured from where the actor is facing, so 180 is every direction
	 * at once and 90 is everything in front of the shoulders.
	 *
	 * <p>Vanilla's shield uses 90. The default here is wider, because 90 puts the
	 * cutoff exactly where an attacker shoving into the actor's side comes to rest —
	 * see {@link CombatActor} for why that made blocking feel random at melee range.
	 */
	public static final int MAX_ARC_DEGREES = 180;

	/**
	 * Stops short of 100 deliberately: the penalty is applied as a fraction of the
	 * finished movement speed, and taking all of it away would leave a guarding actor
	 * unable to move at all rather than merely slow.
	 */
	public static final int MAX_SLOW_PERCENT = 90;

	/** Above 100 a shield costs more than a bare weapon, which is allowed but odd. */
	public static final int MAX_SHIELD_PERCENT = 200;

	private static final double HUNDREDTHS = 100.0;
	private static final int TICKS_PER_SECOND = 20;

	/**
	 * Whether the guard actually applies. An arc of zero is the config-level off
	 * switch — nothing can ever fall inside it — so an actor that has
	 * {@link CombatVerb#BLOCK} at compile time can still be returned to no guard at
	 * all by an admin without a rebuild.
	 *
	 * <p>Checked through {@link CombatProfile#usesBlock()}, which combines it with the
	 * verb so no caller has to remember both halves.
	 */
	public boolean enabled() {
		return this.arcDegrees > 0;
	}

	/** Stamina spent per point of damage absorbed. */
	public float costPerDamageScale() {
		return (float) (this.costPerDamage / HUNDREDTHS);
	}

	/** The passive cost of standing behind the guard, configured per second. */
	public float holdCostPerTick() {
		return this.holdCostPerSecond / (float) TICKS_PER_SECOND;
	}

	/** Half the width of the protected cone, measured from where the actor is facing. */
	public double arcRadians() {
		return Math.toRadians(this.arcDegrees);
	}

	/** Movement lost while guarding, as a positive fraction. */
	public double moveSlowFraction() {
		return this.moveSlowPercent / HUNDREDTHS;
	}

	/** What an off-hand shield multiplies the absorb cost by. */
	public float shieldCostScale() {
		return (float) (this.shieldCostPercent / HUNDREDTHS);
	}

	public static Codec<BlockSettings> codec(BlockSettings fallback) {
		return RecordCodecBuilder.create(instance -> instance.group(
				Codec.INT.optionalFieldOf("raise_ticks", fallback.raiseTicks())
						.forGetter(BlockSettings::raiseTicks),
				Codec.INT.optionalFieldOf("recovery_ticks", fallback.recoveryTicks())
						.forGetter(BlockSettings::recoveryTicks),
				Codec.INT.optionalFieldOf("break_ticks", fallback.breakTicks())
						.forGetter(BlockSettings::breakTicks),
				Codec.INT.optionalFieldOf("cost_per_damage", fallback.costPerDamage())
						.forGetter(BlockSettings::costPerDamage),
				Codec.INT.optionalFieldOf("hold_cost_per_second", fallback.holdCostPerSecond())
						.forGetter(BlockSettings::holdCostPerSecond),
				Codec.INT.optionalFieldOf("arc_degrees", fallback.arcDegrees())
						.forGetter(BlockSettings::arcDegrees),
				Codec.INT.optionalFieldOf("move_slow_percent", fallback.moveSlowPercent())
						.forGetter(BlockSettings::moveSlowPercent),
				Codec.INT.optionalFieldOf("shield_cost_percent", fallback.shieldCostPercent())
						.forGetter(BlockSettings::shieldCostPercent)
		).apply(instance, BlockSettings::new));
	}

	public static final StreamCodec<ByteBuf, BlockSettings> STREAM_CODEC = StreamCodec.of(
			(buf, settings) -> {
				buf.writeInt(settings.raiseTicks());
				buf.writeInt(settings.recoveryTicks());
				buf.writeInt(settings.breakTicks());
				buf.writeInt(settings.costPerDamage());
				buf.writeInt(settings.holdCostPerSecond());
				buf.writeInt(settings.arcDegrees());
				buf.writeInt(settings.moveSlowPercent());
				buf.writeInt(settings.shieldCostPercent());
			},
			// Java evaluates arguments left to right, so the read order is well defined
			// and matches the writes above.
			buf -> new BlockSettings(
					buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(),
					buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt()));

	/** A copy with every value forced inside its bounds. */
	public BlockSettings clamped() {
		return new BlockSettings(
				clamp(this.raiseTicks, 0, MAX_TICKS),
				clamp(this.recoveryTicks, 0, MAX_TICKS),
				clamp(this.breakTicks, 0, MAX_TICKS),
				clamp(this.costPerDamage, 0, MAX_COST_PER_DAMAGE),
				clamp(this.holdCostPerSecond, 0, MAX_HOLD_COST),
				clamp(this.arcDegrees, 0, MAX_ARC_DEGREES),
				clamp(this.moveSlowPercent, 0, MAX_SLOW_PERCENT),
				clamp(this.shieldCostPercent, 0, MAX_SHIELD_PERCENT));
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(value, max));
	}
}

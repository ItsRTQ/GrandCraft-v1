package com.hrtq.grandcraft.stats;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * How much one point of a stat is worth.
 *
 * <p>Everything here is priced <em>per point above</em>
 * {@link StatConstants#NEUTRAL}, so a stat at neutral does nothing and a stat below
 * it costs the character something. That symmetry is what lets one number describe
 * both a class's strength and its weakness.
 *
 * <p>Every value is a whole number, so the config fields stay integer-typed and no
 * locale-dependent decimal separator is introduced. Where a whole number is too
 * coarse the unit is hundredths, which is the same answer the block settings
 * already give for cost-per-damage.
 *
 * <p>Like the general settings this is server-authoritative <em>and</em> pushed to
 * every client, because the character sheet explains what each stat is currently
 * doing and cannot do that from the stat value alone.
 *
 * <p>Fields are optional in the codec so a file written by an older build keeps
 * loading as settings are added.
 */
public record StatSettings(
		int armourPerConstitution,
		int healthPerConstitution,
		int staminaRegenPerConstitution,
		int staminaCostPerAgility,
		int maxMana,
		int manaRegenPerSecond,
		int manaRegenDelayTicks,
		int healthPerPoolPoint,
		int staminaPerPoolPoint,
		int manaPerPoolPoint) {

	/**
	 * The three pool figures are deliberately chunky where the per-stat ones are
	 * slight. A stat point arrives every level and its effect is meant to be felt
	 * across a character rather than noticed on the spot; an attribute point arrives
	 * three at a time and only every fifth level, so each one should be worth
	 * stopping to think about.
	 *
	 * <p>Health is in half-hearts, so 2 is one heart per point — three points at the
	 * first milestone is three hearts. Stamina at 10 is most of an extra swing per
	 * point against an attack cost of 12. Mana matches stamina; nothing spends it yet,
	 * so that figure is a placeholder the magic layer should revisit.
	 */
	public static final StatSettings DEFAULT =
			new StatSettings(50, 50, 3, 2, 100, 5, 20, 2, 10, 10);

	/**
	 * Bounds shared by the config fields and the server-side clamp.
	 *
	 * <p>{@link #MAX_ARMOUR_PER_CONSTITUTION} looks generous and is not: vanilla
	 * clamps the armour attribute at 30 points <em>including worn gear</em>, so most
	 * of the top of this range is unreachable in practice.
	 */
	public static final int MAX_ARMOUR_PER_CONSTITUTION = 300;
	public static final int MAX_HEALTH_PER_CONSTITUTION = 500;
	public static final int MAX_PERCENT_PER_POINT = 25;
	public static final int MAX_MANA = 10000;
	public static final int MAX_RATE = 200;
	public static final int MAX_DELAY_TICKS = 200;

	/**
	 * Ceiling on what one attribute point buys.
	 *
	 * <p>Generous rather than tight — these are meant to be tunable into a very
	 * different game — but not unbounded: max health is a vanilla attribute that stops
	 * at 1024, so a rate high enough to blow past that would silently stop paying out.
	 */
	public static final int MAX_PER_POOL_POINT = 100;

	/**
	 * Bounds on the finished multipliers, which is where the clamp has to be rather
	 * than only on the per-point rate. 25% per point sounds reasonable and, twenty
	 * points above neutral, turns a cost into a refund — the clamp is what makes an
	 * extreme setting merely strong instead of a different game.
	 *
	 * <p>The cost floor is well clear of zero on purpose. A guard's hold cost is also
	 * what suppresses stamina regeneration while the guard is up, so a multiplier that
	 * reached zero would quietly restore regen behind a raised guard — a rule change
	 * wearing a tuning change's clothes.
	 */
	public static final double MIN_COST_MULTIPLIER = 0.50;
	public static final double MAX_COST_MULTIPLIER = 2.00;
	public static final double MIN_REGEN_MULTIPLIER = 0.25;
	public static final double MAX_REGEN_MULTIPLIER = 4.00;

	/** Extra armour points from Constitution. Negative below neutral. */
	public double armourBonus(double constitution) {
		return points(constitution) * this.armourPerConstitution / 100.0;
	}

	/** Extra maximum health from Constitution, in half-hearts. Negative below neutral. */
	public double healthBonus(double constitution) {
		return points(constitution) * this.healthPerConstitution / 100.0;
	}

	/** What Constitution does to the rate stamina comes back at. 1.0 is unchanged. */
	public float staminaRegenMultiplier(double constitution) {
		double scale = 1.0 + points(constitution) * this.staminaRegenPerConstitution / 100.0;
		return (float) clamp(scale, MIN_REGEN_MULTIPLIER, MAX_REGEN_MULTIPLIER);
	}

	/** What Agility does to every stamina cost. 1.0 is unchanged; lower is cheaper. */
	public float staminaCostMultiplier(double agility) {
		double scale = 1.0 - points(agility) * this.staminaCostPerAgility / 100.0;
		return (float) clamp(scale, MIN_COST_MULTIPLIER, MAX_COST_MULTIPLIER);
	}

	public ManaSettings mana() {
		return new ManaSettings(this.maxMana, this.manaRegenPerSecond, this.manaRegenDelayTicks);
	}

	/**
	 * Extra maximum health bought with attribute points, in half-hearts.
	 *
	 * <p>Whole numbers here, unlike the per-Constitution figure which is in hundredths.
	 * That difference is deliberate and safe: the per-stat effects are slight enough
	 * that an integer would round them away entirely, while a point of Health is a
	 * chunky, occasional purchase where a whole half-heart is the natural unit.
	 */
	public double poolHealthBonus(PoolBlock spent) {
		return (double) spent.health() * this.healthPerPoolPoint;
	}

	/** Extra maximum stamina bought with attribute points, in stamina points. */
	public int poolStaminaBonus(PoolBlock spent) {
		return spent.stamina() * this.staminaPerPoolPoint;
	}

	/** Extra maximum mana bought with attribute points. */
	public int poolManaBonus(PoolBlock spent) {
		return spent.mana() * this.manaPerPoolPoint;
	}

	/** How far a stat sits above neutral, which is what everything here is priced against. */
	private static double points(double stat) {
		return stat - StatConstants.NEUTRAL;
	}

	public static final Codec<StatSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.optionalFieldOf("armour_per_constitution", DEFAULT.armourPerConstitution())
					.forGetter(StatSettings::armourPerConstitution),
			Codec.INT.optionalFieldOf("health_per_constitution", DEFAULT.healthPerConstitution())
					.forGetter(StatSettings::healthPerConstitution),
			Codec.INT.optionalFieldOf("stamina_regen_per_constitution", DEFAULT.staminaRegenPerConstitution())
					.forGetter(StatSettings::staminaRegenPerConstitution),
			Codec.INT.optionalFieldOf("stamina_cost_per_agility", DEFAULT.staminaCostPerAgility())
					.forGetter(StatSettings::staminaCostPerAgility),
			Codec.INT.optionalFieldOf("max_mana", DEFAULT.maxMana())
					.forGetter(StatSettings::maxMana),
			Codec.INT.optionalFieldOf("mana_regen_per_second", DEFAULT.manaRegenPerSecond())
					.forGetter(StatSettings::manaRegenPerSecond),
			Codec.INT.optionalFieldOf("mana_regen_delay_ticks", DEFAULT.manaRegenDelayTicks())
					.forGetter(StatSettings::manaRegenDelayTicks),
			Codec.INT.optionalFieldOf("health_per_pool_point", DEFAULT.healthPerPoolPoint())
					.forGetter(StatSettings::healthPerPoolPoint),
			Codec.INT.optionalFieldOf("stamina_per_pool_point", DEFAULT.staminaPerPoolPoint())
					.forGetter(StatSettings::staminaPerPoolPoint),
			Codec.INT.optionalFieldOf("mana_per_pool_point", DEFAULT.manaPerPoolPoint())
					.forGetter(StatSettings::manaPerPoolPoint)
	).apply(instance, StatSettings::new));

	public static final StreamCodec<ByteBuf, StatSettings> STREAM_CODEC = StreamCodec.of(
			(buf, settings) -> {
				buf.writeInt(settings.armourPerConstitution());
				buf.writeInt(settings.healthPerConstitution());
				buf.writeInt(settings.staminaRegenPerConstitution());
				buf.writeInt(settings.staminaCostPerAgility());
				buf.writeInt(settings.maxMana());
				buf.writeInt(settings.manaRegenPerSecond());
				buf.writeInt(settings.manaRegenDelayTicks());
				buf.writeInt(settings.healthPerPoolPoint());
				buf.writeInt(settings.staminaPerPoolPoint());
				buf.writeInt(settings.manaPerPoolPoint());
			},
			// Java evaluates arguments left to right, so this matches the writes above.
			buf -> new StatSettings(
					buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(),
					buf.readInt(), buf.readInt(), buf.readInt(),
					buf.readInt(), buf.readInt(), buf.readInt()));

	/** A copy with every value forced inside its bounds. */
	public StatSettings clamped() {
		return new StatSettings(
				clamp(this.armourPerConstitution, 0, MAX_ARMOUR_PER_CONSTITUTION),
				clamp(this.healthPerConstitution, 0, MAX_HEALTH_PER_CONSTITUTION),
				clamp(this.staminaRegenPerConstitution, 0, MAX_PERCENT_PER_POINT),
				clamp(this.staminaCostPerAgility, 0, MAX_PERCENT_PER_POINT),
				clamp(this.maxMana, 0, MAX_MANA),
				clamp(this.manaRegenPerSecond, 0, MAX_RATE),
				clamp(this.manaRegenDelayTicks, 0, MAX_DELAY_TICKS),
				clamp(this.healthPerPoolPoint, 0, MAX_PER_POOL_POINT),
				clamp(this.staminaPerPoolPoint, 0, MAX_PER_POOL_POINT),
				clamp(this.manaPerPoolPoint, 0, MAX_PER_POOL_POINT));
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(value, max));
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(value, max));
	}
}

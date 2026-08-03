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
		int manaPerPoolPoint,
		int spellDamagePerArcane,
		int spellCooldownPerArcane,
		int manaRegenMinArcane,
		int meleeDamagePerPoint) {

	/**
	 * The three pool figures are deliberately chunky where the per-stat ones are
	 * slight. A stat point arrives every level and its effect is meant to be felt
	 * across a character rather than noticed on the spot; an attribute point arrives
	 * three at a time and only every fifth level, so each one should be worth
	 * stopping to think about.
	 *
	 * <p>Health is in half-hearts, so 2 is one heart per point — three points at the
	 * first milestone is three hearts. Stamina at 10 is most of an extra swing per
	 * point against an attack cost of 12, and mana matches it.
	 *
	 * <p>The two Arcane figures spread the classes without shutting any of them out,
	 * and they compound on purpose. Against a 2.00 gust on a 16 tick cooldown, a
	 * Sorcerer (Arcane 16) throws 2.6 every 13 ticks and a Warrior (6) throws 1.6
	 * every 18 — a little over twice the output, from two modest per-point rates
	 * rather than one severe one. Neither locks the staff away from a character who
	 * never invested in Arcane; it is simply worth less in their hands.
	 *
	 * <p>Mana recovers slowly and only for a character who invested in Arcane. Both
	 * halves are the point: a free projectile is always worth carrying, so without a
	 * real cost every class would keep a staff in a spare slot and magic would stop
	 * being a caster's identity. Natural regeneration is meant to be the floor that
	 * potions, enchantments, gear and passives build on rather than the whole supply.
	 *
	 * <p>Of the shipped classes only the Sorcerer (Arcane 16) clears the threshold on
	 * day one. A Cleric starts at 13 and reaches it after two stat points — deliberate:
	 * recovery is something a character grows into, not something a class is handed.
	 *
	 * <p>{@code meleeDamagePerPoint} is far and away the steepest rate here, and that is
	 * the design rather than an oversight: a weapon now supplies a low base and the
	 * character supplies the rest, so this one number is what makes a class visible in a
	 * fight and a level worth reaching. At 10 a character twenty points past a weapon's
	 * requirement hits three times as hard as one who has only just qualified — a
	 * Warrior's claymore goes from 4.5 to 13.5 across roughly twenty levels.
	 */
	public static final StatSettings DEFAULT =
			new StatSettings(50, 50, 3, 2, 100, 2, 20, 2, 10, 10, 5, 3, 15, 10);

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

	/**
	 * Bounds on what Arcane does to spell damage.
	 *
	 * <p>The floor is above zero deliberately: a character far below neutral Arcane
	 * should cast feebly, not cast for nothing. A spell that reliably deals zero is
	 * indistinguishable from a spell that is broken, and it would be reported as one.
	 */
	public static final double MIN_SPELL_DAMAGE_MULTIPLIER = 0.25;
	public static final double MAX_SPELL_DAMAGE_MULTIPLIER = 3.00;

	/**
	 * Bounds on what Arcane does to a spell's cooldown. Lower is faster.
	 *
	 * <p>The floor is not the real safety net — {@link ArcaneScaling} imposes an
	 * absolute minimum in ticks as well, because a cooldown short enough to fall
	 * under the client's own four-tick use repeat stops being a cooldown at all.
	 */
	public static final double MIN_SPELL_COOLDOWN_MULTIPLIER = 0.25;
	public static final double MAX_SPELL_COOLDOWN_MULTIPLIER = 2.00;

	/**
	 * Bounds on what a character's stats do to a weapon they can actually lift.
	 *
	 * <p>The ceiling sits deliberately <em>above</em> the 3x the shipped rate is aimed
	 * at, so it acts as a runaway guard rather than as the design's real limit. At 3.00
	 * a Warrior would reach it at around level twenty and every further point would
	 * silently pay nothing — a progression system that stops paying out without saying
	 * so is worse than one that pays out slowly.
	 *
	 * <p>The floor is above zero for the reason the spell one is, plus one of its own:
	 * the blend can fall below a requirement even when the gate stat clears it, because
	 * a gate reads one stat and a blend reads several. A strong, clumsy character
	 * scraping a sword's Strength gate should swing feebly, not for free.
	 */
	public static final double MIN_MELEE_DAMAGE_MULTIPLIER = 0.50;
	public static final double MAX_MELEE_DAMAGE_MULTIPLIER = 5.00;

	/**
	 * Ceiling on the Arcane a character must reach before mana recovers at all.
	 *
	 * <p>Well above any starting spread, so an admin can push recovery out of reach
	 * entirely, and well below {@code StatConstants.MAX} so the field cannot be set to
	 * a value no character could ever meet by accident.
	 */
	public static final int MAX_MANA_REGEN_MIN_ARCANE = 100;

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

	/**
	 * What Arcane does to spell damage. 1.0 is unchanged; higher hits harder.
	 *
	 * <p>Clamped on the finished multiplier rather than on the per-point rate, for the
	 * same reason the stamina ones are: a rate that looks reasonable becomes absurd
	 * twenty points from neutral.
	 */
	public float spellDamageMultiplier(double arcane) {
		double scale = 1.0 + points(arcane) * this.spellDamagePerArcane / 100.0;
		return (float) clamp(scale, MIN_SPELL_DAMAGE_MULTIPLIER, MAX_SPELL_DAMAGE_MULTIPLIER);
	}

	/**
	 * What Arcane does to how often a spell can be cast. 1.0 is unchanged; lower is
	 * faster.
	 *
	 * <p>Subtracts like the Agility cost multiplier rather than adding like the damage
	 * one, because for a cooldown "better" means "smaller".
	 */
	public float spellCooldownMultiplier(double arcane) {
		double scale = 1.0 - points(arcane) * this.spellCooldownPerArcane / 100.0;
		return (float) clamp(scale, MIN_SPELL_COOLDOWN_MULTIPLIER, MAX_SPELL_COOLDOWN_MULTIPLIER);
	}

	/**
	 * Whether this character's mana recovers on its own, as a multiplier on the
	 * configured rate. 1.0 is the full rate; 0.0 is none at all.
	 *
	 * <p>A hard gate today rather than a curve, because the design question it answers
	 * is binary: a free projectile is always worth carrying, so if every class trickled
	 * mana back then every class would keep a staff and magic would stop being a
	 * caster's identity. Below the threshold a character can still cast — they simply
	 * have to find mana somewhere rather than wait for it.
	 *
	 * <p><strong>This is the seam for everything that will grant recovery later</strong>
	 * — potions, enchantments, gear, passive abilities. Those become further terms
	 * multiplied in here, which is why this returns a multiplier rather than a boolean:
	 * a character below the threshold with a mana-regen source should end up above
	 * zero, not stay at it.
	 */
	public float manaRegenMultiplier(double arcane) {
		return arcane >= this.manaRegenMinArcane ? 1.0F : 0.0F;
	}

	/**
	 * What a character's stats do to the weapon they are holding. 1.0 is the weapon's
	 * own base; higher hits harder.
	 *
	 * <p><strong>Takes a surplus, not a stat value.</strong> Every other rate in this
	 * record is priced against {@link StatConstants#NEUTRAL}, because a stat is measured
	 * against what an ordinary person has. A weapon is not: it states its own demand,
	 * and the question that matters is how far past <em>that</em> the character is. So
	 * the caller subtracts the requirement and hands the difference in — which is also
	 * what stops a heavier weapon being punished twice for asking for more.
	 *
	 * <p>Clamped on the finished multiplier rather than on the per-point rate, for the
	 * same reason the others are.
	 */
	public float meleeDamageMultiplier(double surplus) {
		double scale = 1.0 + surplus * this.meleeDamagePerPoint / 100.0;
		return (float) clamp(scale, MIN_MELEE_DAMAGE_MULTIPLIER, MAX_MELEE_DAMAGE_MULTIPLIER);
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
					.forGetter(StatSettings::manaPerPoolPoint),
			Codec.INT.optionalFieldOf("spell_damage_per_arcane", DEFAULT.spellDamagePerArcane())
					.forGetter(StatSettings::spellDamagePerArcane),
			Codec.INT.optionalFieldOf("spell_cooldown_per_arcane", DEFAULT.spellCooldownPerArcane())
					.forGetter(StatSettings::spellCooldownPerArcane),
			Codec.INT.optionalFieldOf("mana_regen_min_arcane", DEFAULT.manaRegenMinArcane())
					.forGetter(StatSettings::manaRegenMinArcane),
			Codec.INT.optionalFieldOf("melee_damage_per_point", DEFAULT.meleeDamagePerPoint())
					.forGetter(StatSettings::meleeDamagePerPoint)
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
				buf.writeInt(settings.spellDamagePerArcane());
				buf.writeInt(settings.spellCooldownPerArcane());
				buf.writeInt(settings.manaRegenMinArcane());
				buf.writeInt(settings.meleeDamagePerPoint());
			},
			// Java evaluates arguments left to right, so this matches the writes above.
			buf -> new StatSettings(
					buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(),
					buf.readInt(), buf.readInt(), buf.readInt(),
					buf.readInt(), buf.readInt(), buf.readInt(),
					buf.readInt(), buf.readInt(), buf.readInt(),
					buf.readInt()));

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
				clamp(this.manaPerPoolPoint, 0, MAX_PER_POOL_POINT),
				clamp(this.spellDamagePerArcane, 0, MAX_PERCENT_PER_POINT),
				clamp(this.spellCooldownPerArcane, 0, MAX_PERCENT_PER_POINT),
				clamp(this.manaRegenMinArcane, 0, MAX_MANA_REGEN_MIN_ARCANE),
				clamp(this.meleeDamagePerPoint, 0, MAX_PERCENT_PER_POINT));
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(value, max));
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(value, max));
	}
}

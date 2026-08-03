package com.hrtq.grandcraft.stats;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.LivingEntity;

/**
 * Which stats a weapon converts into damage, and in what proportion.
 *
 * <p>This is the whole of what makes a Sorcerer's claymore worthless. The weapon no
 * longer carries a damage number worth having; it carries a <em>blend</em> — an
 * instruction to read 100% Strength, or 60% Strength and 40% Agility — and the
 * character supplies the value. A blend that reads a stat the holder never invested in
 * resolves to almost nothing, and no requirement, penalty or special case had to be
 * written to make that true.
 *
 * <h2>Weights are ratios, not percentages</h2>
 * {@link #blend} divides by the actual sum rather than by a hard 100, so a pair of
 * weights is never wrong — only differently scaled. 60/40 and 30/20 describe the same
 * weapon. That is deliberate: the alternative is normalising on the way in, which is
 * lossy (60/60 has no exact halves in whole numbers) and would silently rewrite an
 * admin's typed values in the config file the next time it was saved.
 *
 * <p>The config screen still shows a running total and nudges toward 100, because 100
 * is the only sum where a weight can be read as a percentage at a glance. That is a
 * presentational convention, not a constraint the maths depends on.
 *
 * <h2>Constitution is here and is meant to stay unused</h2>
 * Every stat gets a slot so the record's shape never has to change, and so a future
 * weapon that genuinely wants to scale off toughness has somewhere to say it. Nothing
 * ships with a non-zero Constitution weight; do not add one to make a category feel
 * stronger, because that is a damage lever wearing a design decision's clothes.
 */
public record StatWeights(int strength, int agility, int constitution, int arcane) {

	/** Bounds shared by the config fields and the server-side clamp. */
	public static final int MAX_WEIGHT = 1000;

	/**
	 * What a weapon nobody has described scales off.
	 *
	 * <p>Strength rather than nothing at all: a zero-sum blend has no meaningful
	 * dominant stat and would resolve to a flat neutral for every character, which
	 * reads in game as "this weapon ignores my build" — the exact failure this whole
	 * system exists to remove.
	 */
	public static final StatWeights FALLBACK = new StatWeights(100, 0, 0, 0);

	public int weightOf(CharacterStat stat) {
		return switch (stat) {
			case STRENGTH -> this.strength;
			case AGILITY -> this.agility;
			case CONSTITUTION -> this.constitution;
			case ARCANE -> this.arcane;
		};
	}

	/** The sum every weight is measured against. Zero only when nothing was set. */
	public int total() {
		return this.strength + this.agility + this.constitution + this.arcane;
	}

	/**
	 * This character's effective stat for a weapon with these weights.
	 *
	 * <p>The single number the damage curve is priced against — a weighted average of
	 * the stats the weapon reads, on the same scale as an individual stat, so it can be
	 * compared directly against a requirement.
	 *
	 * <p>Reads through {@link StatEffects#statOf}, so a mob (which carries none of these
	 * attributes) blends to {@link StatConstants#NEUTRAL} rather than to zero. That
	 * matters more than it looks: mobs never reach this code today, but a neutral answer
	 * degrades to "no opinion" instead of to "unarmed and helpless".
	 */
	public double blend(LivingEntity entity) {
		int total = total();

		if (total <= 0) {
			return FALLBACK.blend(entity);
		}

		double sum = 0.0;

		for (CharacterStat stat : CharacterStat.values()) {
			int weight = weightOf(stat);

			// Skipped rather than multiplied by zero so a stat this weapon does not read
			// is never even looked up. Not a performance point — it keeps a weapon from
			// depending on an attribute it has no business knowing about.
			if (weight != 0) {
				sum += weight * StatEffects.statOf(entity, stat.attribute());
			}
		}

		return sum / total;
	}

	/**
	 * The stat this weapon most wants, and therefore the one it demands a minimum of.
	 *
	 * <p>Derived rather than configured, so a weapon's gate can never disagree with what
	 * it actually scales off — a claymore that reads Strength and demands Agility would
	 * be a bug nobody could see in the numbers, only in play.
	 *
	 * <p>Ties break in declaration order, which puts Strength first. An even blend has
	 * no more correct answer than that, and picking deterministically at least makes the
	 * behaviour reproducible.
	 */
	public CharacterStat dominant() {
		CharacterStat best = CharacterStat.STRENGTH;
		int bestWeight = -1;

		for (CharacterStat stat : CharacterStat.values()) {
			int weight = weightOf(stat);

			if (weight > bestWeight) {
				best = stat;
				bestWeight = weight;
			}
		}

		return bestWeight <= 0 ? CharacterStat.STRENGTH : best;
	}

	public static Codec<StatWeights> codec(StatWeights fallback) {
		return RecordCodecBuilder.create(instance -> instance.group(
				Codec.INT.optionalFieldOf("strength", fallback.strength())
						.forGetter(StatWeights::strength),
				Codec.INT.optionalFieldOf("agility", fallback.agility())
						.forGetter(StatWeights::agility),
				Codec.INT.optionalFieldOf("constitution", fallback.constitution())
						.forGetter(StatWeights::constitution),
				Codec.INT.optionalFieldOf("arcane", fallback.arcane())
						.forGetter(StatWeights::arcane)
		).apply(instance, StatWeights::new));
	}

	public static final StreamCodec<ByteBuf, StatWeights> STREAM_CODEC = StreamCodec.of(
			(buf, weights) -> {
				buf.writeInt(weights.strength());
				buf.writeInt(weights.agility());
				buf.writeInt(weights.constitution());
				buf.writeInt(weights.arcane());
			},
			// Java evaluates arguments left to right, so the read order is well defined
			// and matches the writes above.
			buf -> new StatWeights(buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt()));

	/** A copy with every weight forced inside its bounds. */
	public StatWeights clamped() {
		return new StatWeights(
				clamp(this.strength),
				clamp(this.agility),
				clamp(this.constitution),
				clamp(this.arcane));
	}

	private static int clamp(int weight) {
		return Math.max(0, Math.min(weight, MAX_WEIGHT));
	}
}

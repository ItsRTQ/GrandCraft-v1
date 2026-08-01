package com.hrtq.grandcraft.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.RandomSource;

/**
 * How Essence Power is earned and what a level is worth.
 *
 * <p>Essence Power is GrandCraft's own progression track and is deliberately
 * <em>parallel</em> to vanilla experience rather than a replacement for it. Vanilla
 * keeps its bar, its level number, and its role as the currency for enchanting,
 * anvils and grindstones; nothing here touches any of that.
 *
 * <p>Every value is a whole number, so the config fields stay integer-typed and no
 * locale-dependent decimal separator is introduced — the same rule the combat and
 * stat settings already follow.
 *
 * <p>Server-authoritative <em>and</em> pushed to every client, like
 * {@link com.hrtq.grandcraft.stats.StatSettings}, because the character sheet has to
 * show progress towards the next level and cannot work the cost out on its own.
 *
 * <p>Fields are optional in the codec so a file written by an older build keeps
 * loading as settings are added.
 */
public record LevelSettings(
		int baseCost,
		int costPerLevel,
		int dropWeight1,
		int dropWeight2,
		int dropWeight3,
		int statPointsPerLevel,
		int milestoneInterval,
		int poolPointsPerMilestone) {

	/**
	 * Level 1 costs 10 Essence, and each level after it costs 5 more — so level 5,
	 * the first milestone, costs 30 and arrives after roughly sixty kills at the
	 * default drop weights.
	 *
	 * <p>The weights are the user's: a kill is worth 1 Essence 85% of the time, 2
	 * Essence 12% of the time and 3 Essence 3% of the time, averaging about 1.18.
	 * Per-mob and boss overrides are expected later; this table is where the
	 * <em>shape</em> lives, and a per-mob figure would sit alongside it rather than
	 * replace it.
	 */
	public static final LevelSettings DEFAULT = new LevelSettings(10, 5, 85, 12, 3, 1, 5, 3);

	/** Bounds shared by the config fields and the server-side clamp. */
	public static final int MAX_COST = 100_000;
	public static final int MAX_COST_PER_LEVEL = 10_000;
	public static final int MAX_WEIGHT = 10_000;
	public static final int MAX_POINTS = 100;
	public static final int MAX_MILESTONE_INTERVAL = 100;

	/**
	 * The largest Essence value a single orb can be worth, which is also how many
	 * weights this record carries. Raising it means adding a field, not changing a
	 * number — deliberately, because each weight needs its own config row.
	 */
	public static final int MAX_ORB_VALUE = 3;

	/**
	 * Essence needed to go from {@code level - 1} to {@code level}.
	 *
	 * <p>Never returns less than one. A cost of zero would make the level-up loop in
	 * {@link EssenceAwards} spin forever on a single orb, so the floor is a
	 * termination guarantee rather than a tuning choice.
	 */
	public int costOf(int level) {
		long cost = (long) this.baseCost + (long) this.costPerLevel * Math.max(level - 1, 0);

		return (int) Math.clamp(cost, 1L, MAX_COST);
	}

	/**
	 * Rolls what one kill's orb is worth.
	 *
	 * <p>Weighted rather than uniform so the common case stays common and a 3 reads
	 * as a small piece of luck. Weights are relative, not percentages, so an admin
	 * can write 85/12/3 or 850/120/30 and mean the same thing.
	 *
	 * <p>Falls back to 1 when every weight has been zeroed, rather than to 0: a kill
	 * that drops nothing at all is indistinguishable from the feature being broken,
	 * and turning drops off is what the drop hook's own gate is for.
	 */
	public int rollOrbValue(RandomSource random) {
		int total = this.dropWeight1 + this.dropWeight2 + this.dropWeight3;

		if (total <= 0) {
			return 1;
		}

		int roll = random.nextInt(total);

		if (roll < this.dropWeight1) {
			return 1;
		}

		return roll < this.dropWeight1 + this.dropWeight2 ? 2 : 3;
	}

	/**
	 * Attribute points granted for reaching this level, on top of the stat point
	 * every level grants.
	 *
	 * <p>"Attribute" is the user's term for the pools — Health, Stamina and Mana —
	 * and is deliberately distinct from "Stat", which means Strength, Agility,
	 * Constitution and Arcane. Both words appear in the UI and they do not mean the
	 * same thing; see the stats package.
	 */
	public int poolPointsFor(int level) {
		int interval = Math.max(this.milestoneInterval, 1);

		return level > 0 && level % interval == 0 ? this.poolPointsPerMilestone : 0;
	}

	public static final Codec<LevelSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.optionalFieldOf("base_cost", DEFAULT.baseCost())
					.forGetter(LevelSettings::baseCost),
			Codec.INT.optionalFieldOf("cost_per_level", DEFAULT.costPerLevel())
					.forGetter(LevelSettings::costPerLevel),
			Codec.INT.optionalFieldOf("drop_weight_1", DEFAULT.dropWeight1())
					.forGetter(LevelSettings::dropWeight1),
			Codec.INT.optionalFieldOf("drop_weight_2", DEFAULT.dropWeight2())
					.forGetter(LevelSettings::dropWeight2),
			Codec.INT.optionalFieldOf("drop_weight_3", DEFAULT.dropWeight3())
					.forGetter(LevelSettings::dropWeight3),
			Codec.INT.optionalFieldOf("stat_points_per_level", DEFAULT.statPointsPerLevel())
					.forGetter(LevelSettings::statPointsPerLevel),
			Codec.INT.optionalFieldOf("milestone_interval", DEFAULT.milestoneInterval())
					.forGetter(LevelSettings::milestoneInterval),
			Codec.INT.optionalFieldOf("pool_points_per_milestone", DEFAULT.poolPointsPerMilestone())
					.forGetter(LevelSettings::poolPointsPerMilestone)
	).apply(instance, LevelSettings::new));

	public static final StreamCodec<ByteBuf, LevelSettings> STREAM_CODEC = StreamCodec.of(
			(buf, settings) -> {
				buf.writeInt(settings.baseCost());
				buf.writeInt(settings.costPerLevel());
				buf.writeInt(settings.dropWeight1());
				buf.writeInt(settings.dropWeight2());
				buf.writeInt(settings.dropWeight3());
				buf.writeInt(settings.statPointsPerLevel());
				buf.writeInt(settings.milestoneInterval());
				buf.writeInt(settings.poolPointsPerMilestone());
			},
			// Java evaluates arguments left to right, so this matches the writes above.
			buf -> new LevelSettings(
					buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(),
					buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt()));

	/**
	 * A copy with every value forced inside its bounds. Always applied to anything
	 * arriving over the network or read off disk, so a hand-built packet cannot stall
	 * the level-up loop or hand out a thousand points a level.
	 */
	public LevelSettings clamped() {
		return new LevelSettings(
				// Floor of one on both cost fields: see costOf.
				clamp(this.baseCost, 1, MAX_COST),
				clamp(this.costPerLevel, 0, MAX_COST_PER_LEVEL),
				clamp(this.dropWeight1, 0, MAX_WEIGHT),
				clamp(this.dropWeight2, 0, MAX_WEIGHT),
				clamp(this.dropWeight3, 0, MAX_WEIGHT),
				clamp(this.statPointsPerLevel, 0, MAX_POINTS),
				// Floor of one: poolPointsFor takes a modulus by this.
				clamp(this.milestoneInterval, 1, MAX_MILESTONE_INTERVAL),
				clamp(this.poolPointsPerMilestone, 0, MAX_POINTS));
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(value, max));
	}
}

package com.hrtq.grandcraft.progression;

import com.hrtq.grandcraft.stats.CharacterPool;
import com.hrtq.grandcraft.stats.CharacterStat;
import com.hrtq.grandcraft.stats.PoolBlock;
import com.hrtq.grandcraft.stats.StatBlock;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * One character's Essence Power: how far through the current level they are, what
 * level they have reached, and the points they have not yet spent.
 *
 * <p><strong>{@link #essence} is progress towards the <em>next</em> level, not a
 * lifetime total.</strong> It is reduced by the level's cost each time one is gained,
 * exactly as vanilla's experience bar behaves, which is what lets the character sheet
 * draw "X / Y" from this record and the settings alone without a second stored figure.
 *
 * <p>Immutable, like every other record in the mod, so an award is a replacement
 * rather than a mutation and there is no way to half-apply one.
 *
 * <h2>Two kinds of point, and they are not interchangeable</h2>
 * {@link #statPoints} buy <em>Stats</em> — Strength, Agility, Constitution, Arcane.
 * {@link #poolPoints} buy <em>Attributes</em> — the Health, Stamina and Mana pools.
 * Both words are the user's and both appear in the UI; the stats package documents
 * why they must not be collapsed into one term.
 */
public record EssenceProgress(
		int essence, int level, int statPoints, int poolPoints,
		StatBlock spentStats, PoolBlock spentPools) {

	/** A character who has never picked up an orb. */
	public static final EssenceProgress NONE =
			new EssenceProgress(0, 0, 0, 0, StatBlock.NONE, PoolBlock.NONE);

	public EssenceProgress {
		// Nothing here is ever meaningfully negative, and a negative value read off
		// disk would make the sheet draw nonsense rather than fail loudly. Clamping in
		// the canonical constructor means every route in — codec, packet, award — is
		// covered by one guard.
		essence = Math.max(essence, 0);
		level = Math.max(level, 0);
		statPoints = Math.max(statPoints, 0);
		poolPoints = Math.max(poolPoints, 0);

		// A record read from a file written before spent points existed decodes with
		// nothing here rather than with an empty block.
		spentStats = spentStats == null ? StatBlock.NONE : spentStats;
		spentPools = spentPools == null ? PoolBlock.NONE : spentPools;
	}

	/**
	 * Moves one unspent stat point into the given stat.
	 *
	 * <p>Does not check that a point is available — the server receiver does that
	 * before calling, because refusing there is what lets it also log the attempt.
	 */
	public EssenceProgress spendStatPoint(CharacterStat stat) {
		return new EssenceProgress(this.essence, this.level, this.statPoints - 1,
				this.poolPoints, this.spentStats.plusOne(stat), this.spentPools);
	}

	/** Moves one unspent attribute point into the given pool. See {@link #spendStatPoint}. */
	public EssenceProgress spendPoolPoint(CharacterPool pool) {
		return new EssenceProgress(this.essence, this.level, this.statPoints,
				this.poolPoints - 1, this.spentStats, this.spentPools.plusOne(pool));
	}

	/** Essence still needed to reach the next level. */
	public int essenceToNextLevel(LevelSettings settings) {
		return Math.max(settings.costOf(this.level + 1) - this.essence, 0);
	}

	/** What the level in progress costs in total, which is the sheet's denominator. */
	public int currentLevelCost(LevelSettings settings) {
		return settings.costOf(this.level + 1);
	}

	public boolean hasUnspentPoints() {
		return this.statPoints > 0 || this.poolPoints > 0;
	}

	public static final Codec<EssenceProgress> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.optionalFieldOf("essence", NONE.essence()).forGetter(EssenceProgress::essence),
			Codec.INT.optionalFieldOf("level", NONE.level()).forGetter(EssenceProgress::level),
			Codec.INT.optionalFieldOf("stat_points", NONE.statPoints()).forGetter(EssenceProgress::statPoints),
			Codec.INT.optionalFieldOf("pool_points", NONE.poolPoints()).forGetter(EssenceProgress::poolPoints),
			// Optional like the rest, so a character saved before points could be spent
			// loads with none spent rather than failing to load at all.
			StatBlock.CODEC.optionalFieldOf("spent_stats", NONE.spentStats())
					.forGetter(EssenceProgress::spentStats),
			PoolBlock.CODEC.optionalFieldOf("spent_pools", NONE.spentPools())
					.forGetter(EssenceProgress::spentPools)
	).apply(instance, EssenceProgress::new));

	public static final StreamCodec<ByteBuf, EssenceProgress> STREAM_CODEC = StreamCodec.of(
			(buf, progress) -> {
				buf.writeInt(progress.essence());
				buf.writeInt(progress.level());
				buf.writeInt(progress.statPoints());
				buf.writeInt(progress.poolPoints());
				StatBlock.STREAM_CODEC.encode(buf, progress.spentStats());
				PoolBlock.STREAM_CODEC.encode(buf, progress.spentPools());
			},
			// Java evaluates arguments left to right, so this matches the writes above.
			buf -> new EssenceProgress(buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(),
					StatBlock.STREAM_CODEC.decode(buf), PoolBlock.STREAM_CODEC.decode(buf)));
}

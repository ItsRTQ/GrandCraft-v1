package com.hrtq.grandcraft.stats;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * One character's four stats, as whole points.
 *
 * <p>Used for two things that are the same shape: the per-class baselines on
 * {@code PlayerClass}, and the points a character has spent into each stat. Adding
 * the two is what {@code PlayerStats} writes to the attributes.
 *
 * <p>The live values are not read from here — they are the base values of the
 * attributes registered in {@link GrandCraftAttributes}, so that gear, effects and
 * spent points all compose through vanilla's own modifier system rather than through
 * a second one invented here.
 *
 * <p>{@link StatConstants#NEUTRAL} is the do-nothing value, so a block of tens is a
 * character with no advantages and no weaknesses.
 */
public record StatBlock(int strength, int agility, int constitution, int arcane) {

	/** Every stat at neutral. */
	public static final StatBlock NEUTRAL = new StatBlock(10, 10, 10, 10);

	/** Nothing spent anywhere: what a character starts a life with. */
	public static final StatBlock NONE = new StatBlock(0, 0, 0, 0);

	/** The points in one stat. */
	public int get(CharacterStat stat) {
		return switch (stat) {
			case STRENGTH -> this.strength;
			case AGILITY -> this.agility;
			case CONSTITUTION -> this.constitution;
			case ARCANE -> this.arcane;
		};
	}

	/** A copy with one more point in the given stat. */
	public StatBlock plusOne(CharacterStat stat) {
		return switch (stat) {
			case STRENGTH -> new StatBlock(this.strength + 1, this.agility, this.constitution, this.arcane);
			case AGILITY -> new StatBlock(this.strength, this.agility + 1, this.constitution, this.arcane);
			case CONSTITUTION -> new StatBlock(this.strength, this.agility, this.constitution + 1, this.arcane);
			case ARCANE -> new StatBlock(this.strength, this.agility, this.constitution, this.arcane + 1);
		};
	}

	public static final Codec<StatBlock> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.optionalFieldOf("strength", NONE.strength()).forGetter(StatBlock::strength),
			Codec.INT.optionalFieldOf("agility", NONE.agility()).forGetter(StatBlock::agility),
			Codec.INT.optionalFieldOf("constitution", NONE.constitution()).forGetter(StatBlock::constitution),
			Codec.INT.optionalFieldOf("arcane", NONE.arcane()).forGetter(StatBlock::arcane)
	).apply(instance, StatBlock::new));

	public static final StreamCodec<ByteBuf, StatBlock> STREAM_CODEC = StreamCodec.of(
			(buf, block) -> {
				buf.writeInt(block.strength());
				buf.writeInt(block.agility());
				buf.writeInt(block.constitution());
				buf.writeInt(block.arcane());
			},
			// Java evaluates arguments left to right, so this matches the writes above.
			buf -> new StatBlock(buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt()));
}

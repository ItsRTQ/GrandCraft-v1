package com.hrtq.grandcraft.stats;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * Points a character has committed to each of the three pools.
 *
 * <p>The counterpart to {@link StatBlock}, and the same shape for the same reason:
 * one value per {@link CharacterPool}, immutable, with the codecs needed to persist
 * and sync it.
 *
 * <p>What a point is <em>worth</em> is not here — that is a tuning figure and lives
 * in {@link StatSettings}, so an admin can change the exchange rate without every
 * character's record meaning something different afterwards.
 */
public record PoolBlock(int health, int stamina, int mana) {

	/** Nothing spent anywhere: what a character starts a life with. */
	public static final PoolBlock NONE = new PoolBlock(0, 0, 0);

	/** The points in one pool. */
	public int get(CharacterPool pool) {
		return switch (pool) {
			case HEALTH -> this.health;
			case STAMINA -> this.stamina;
			case MANA -> this.mana;
		};
	}

	/** A copy with one more point in the given pool. */
	public PoolBlock plusOne(CharacterPool pool) {
		return switch (pool) {
			case HEALTH -> new PoolBlock(this.health + 1, this.stamina, this.mana);
			case STAMINA -> new PoolBlock(this.health, this.stamina + 1, this.mana);
			case MANA -> new PoolBlock(this.health, this.stamina, this.mana + 1);
		};
	}

	public static final Codec<PoolBlock> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.optionalFieldOf("health", NONE.health()).forGetter(PoolBlock::health),
			Codec.INT.optionalFieldOf("stamina", NONE.stamina()).forGetter(PoolBlock::stamina),
			Codec.INT.optionalFieldOf("mana", NONE.mana()).forGetter(PoolBlock::mana)
	).apply(instance, PoolBlock::new));

	public static final StreamCodec<ByteBuf, PoolBlock> STREAM_CODEC = StreamCodec.of(
			(buf, block) -> {
				buf.writeInt(block.health());
				buf.writeInt(block.stamina());
				buf.writeInt(block.mana());
			},
			// Java evaluates arguments left to right, so this matches the writes above.
			buf -> new PoolBlock(buf.readInt(), buf.readInt(), buf.readInt()));
}

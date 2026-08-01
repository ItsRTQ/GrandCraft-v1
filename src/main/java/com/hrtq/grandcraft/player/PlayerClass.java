package com.hrtq.grandcraft.player;

import com.hrtq.grandcraft.stats.StatBlock;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/**
 * A character's class, and the stat spread it starts with.
 *
 * <h2>The baseline table</h2>
 * The four numbers on each constant are that class's starting Strength, Agility,
 * Constitution and Arcane. They live here, on the constant, so the class list and
 * its numbers cannot drift apart and adding a class stays a single line.
 *
 * <p>{@link com.hrtq.grandcraft.stats.StatConstants#NEUTRAL} is 10 — the value at
 * which a stat does nothing. Peasant sits at 5 across the board, so an unclassed
 * character is measurably worse off than a classed one in every direction. That is
 * the intent: picking a class should be a step up, not a sideways move.
 *
 * <p>These numbers are provisional. The classes themselves are expected to change
 * before release, and this table is the only place to change them.
 */
public enum PlayerClass implements StringRepresentable {
	//                            STR AGI CON ARC
	PEASANT("peasant", new StatBlock(5, 5, 5, 5)),
	ARCANIST("arcanist", new StatBlock(7, 10, 9, 15)),
	TEMPLAR("templar", new StatBlock(13, 10, 14, 8)),
	WITCH("witch", new StatBlock(7, 13, 9, 14)),
	RITUALIST("ritualist", new StatBlock(8, 11, 11, 13)),
	MERCENARY("mercenary", new StatBlock(14, 12, 13, 6));

	public static final Codec<PlayerClass> CODEC = StringRepresentable.fromEnum(PlayerClass::values);
	public static final StreamCodec<ByteBuf, PlayerClass> STREAM_CODEC =
			ByteBufCodecs.STRING_UTF8.map(PlayerClass::byId, PlayerClass::getSerializedName);

	public static final List<PlayerClass> SELECTABLE = List.of(ARCANIST, TEMPLAR, WITCH, RITUALIST, MERCENARY);

	private final String id;
	private final StatBlock baseStats;

	PlayerClass(String id, StatBlock baseStats) {
		this.id = id;
		this.baseStats = baseStats;
	}

	/**
	 * The stats a character of this class starts with, before any points they have
	 * spent themselves.
	 */
	public StatBlock baseStats() {
		return this.baseStats;
	}

	@Override
	public String getSerializedName() {
		return this.id;
	}

	public static PlayerClass byId(String id) {
		for (PlayerClass playerClass : values()) {
			if (playerClass.id.equals(id)) {
				return playerClass;
			}
		}

		return PEASANT;
	}

	public Component displayName() {
		return Component.translatable("class.grandcraft." + this.id);
	}
}

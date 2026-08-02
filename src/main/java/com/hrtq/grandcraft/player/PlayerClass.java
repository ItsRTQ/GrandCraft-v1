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
 * <h2>Four archetypes, not four finished identities</h2>
 * Warrior, Outlaw, Sorcerer and Cleric are deliberately broad starting points. A
 * character narrows through play, not at the picker: class skill-lines unlocked by
 * levelling are what separate two Warriors. So resist the urge to add a fifth class
 * to express a playstyle — that is a skill-line's job, and a class list that grows
 * to cover every fantasy is the shape this one replaced.
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
 * <p>The four classed spreads each total 44, so no class is simply stronger than
 * another; they differ only in where the points sit. Note that Strength and Arcane
 * are still inert, so today a class is <em>felt</em> only through Constitution and
 * Agility — the spreads are written for the finished game, not for what currently
 * reads them.
 *
 * <p>These numbers are provisional, and this table is the only place to change them.
 */
public enum PlayerClass implements StringRepresentable {
	//                            STR AGI CON ARC
	PEASANT("peasant", new StatBlock(5, 5, 5, 5)),
	WARRIOR("warrior", new StatBlock(14, 10, 14, 6)),
	OUTLAW("outlaw", new StatBlock(10, 15, 11, 8)),
	SORCERER("sorcerer", new StatBlock(7, 11, 10, 16)),
	CLERIC("cleric", new StatBlock(9, 10, 12, 13));

	public static final Codec<PlayerClass> CODEC = StringRepresentable.fromEnum(PlayerClass::values);
	public static final StreamCodec<ByteBuf, PlayerClass> STREAM_CODEC =
			ByteBufCodecs.STRING_UTF8.map(PlayerClass::byId, PlayerClass::getSerializedName);

	/** The classes a player may pick, in the order the picker lists them. */
	public static final List<PlayerClass> SELECTABLE = List.of(WARRIOR, OUTLAW, SORCERER, CLERIC);

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

	/**
	 * A line or two on how this class plays, shown while browsing the class picker.
	 *
	 * <p>Alongside {@link #displayName()} so the {@code class.grandcraft.<id>} key
	 * convention lives in one place rather than being rebuilt by string concatenation
	 * wherever a class is drawn.
	 */
	public Component description() {
		return Component.translatable("class.grandcraft." + this.id + ".description");
	}
}

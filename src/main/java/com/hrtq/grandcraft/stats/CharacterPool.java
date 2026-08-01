package com.hrtq.grandcraft.stats;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/**
 * The three Attributes — the pools a character can buy into with the points a
 * milestone level grants.
 *
 * <p>These are <em>Attributes</em> in the user's vocabulary, which is not the same
 * word as Minecraft's own "attribute" and not the same thing as a {@link CharacterStat}.
 * Health, Stamina and Mana are quantities you have; Strength, Agility, Constitution
 * and Arcane are numbers that describe you.
 *
 * <p>The counterpart to {@link CharacterStat}, and deliberately a separate enum rather
 * than more constants on that one: the two are earned separately, spent separately,
 * and land in different systems — health on a vanilla attribute, stamina and mana on
 * pools this mod owns.
 */
public enum CharacterPool implements StringRepresentable {
	HEALTH("health"),
	STAMINA("stamina"),
	MANA("mana");

	/**
	 * Decodes to null rather than to a default when the name is not one of the three,
	 * for the same reason {@link CharacterStat#STREAM_CODEC} does: a point spent into
	 * the wrong pool cannot be taken back.
	 */
	public static final StreamCodec<ByteBuf, CharacterPool> STREAM_CODEC =
			ByteBufCodecs.STRING_UTF8.map(CharacterPool::byId, CharacterPool::getSerializedName);

	private final String id;

	CharacterPool(String id) {
		this.id = id;
	}

	/** The key both the character sheet's row and its tooltip are built from. */
	public String translationKey() {
		return this.id;
	}

	public Component displayName() {
		return Component.translatable("screen.grandcraft.sheet." + this.id);
	}

	@Override
	public String getSerializedName() {
		return this.id;
	}

	/** @return the pool with this name, or null when nothing matches. */
	public static CharacterPool byId(String id) {
		for (CharacterPool pool : values()) {
			if (pool.id.equals(id)) {
				return pool;
			}
		}

		return null;
	}
}

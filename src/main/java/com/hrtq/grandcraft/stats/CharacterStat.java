package com.hrtq.grandcraft.stats;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.ai.attributes.Attribute;

/**
 * The four Stats, as a value that can be named on the wire and used as a map key.
 *
 * <p>These are <em>Stats</em> — Strength, Agility, Constitution, Arcane — and not
 * <em>Attributes</em>, which in this mod's vocabulary means the Health, Stamina and
 * Mana pools. Both words are in the UI and they do not mean the same thing.
 *
 * <p>Exists because {@link GrandCraftAttributes} holds {@code Holder<Attribute>}
 * constants, which are fine to read a value through and useless for everything else:
 * they cannot be sent in a packet, switched over, or iterated to build four identical
 * rows. This enum is the identity; the holder is looked up from it.
 */
public enum CharacterStat implements StringRepresentable {
	STRENGTH("strength"),
	AGILITY("agility"),
	CONSTITUTION("constitution"),
	ARCANE("arcane");

	/**
	 * Decodes to null rather than to a default when the name is not one of the four.
	 *
	 * <p>{@code PlayerClass} falls back to Peasant on a bad name because there is a
	 * sensible neutral answer there. Here there is not: quietly resolving rubbish to
	 * Strength would spend a player's point into a stat they did not choose. The
	 * receiver checks for null and ignores the message instead.
	 */
	public static final StreamCodec<ByteBuf, CharacterStat> STREAM_CODEC =
			ByteBufCodecs.STRING_UTF8.map(CharacterStat::byId, CharacterStat::getSerializedName);

	/**
	 * The saved form, used by {@link WeaponRequirement} on item stacks.
	 *
	 * <p>{@code fromEnum} errors on a name it does not know rather than resolving it,
	 * which is the same refusal {@link #STREAM_CODEC} makes and is wanted here for a
	 * sharper reason: this one decodes off disk, where a stale name means a weapon that
	 * would otherwise come back gated on a stat nobody authored it against.
	 */
	public static final Codec<CharacterStat> CODEC = StringRepresentable.fromEnum(CharacterStat::values);

	private final String id;

	CharacterStat(String id) {
		this.id = id;
	}

	/**
	 * The registered attribute this stat is stored in.
	 *
	 * <p>Resolved through a switch at call time rather than held in a field on the
	 * constant, which would make initialising this enum force
	 * {@link GrandCraftAttributes} to initialise with it. That class registers from its
	 * static field initialisers and its timing against {@code DefaultAttributes} is
	 * already delicate; nothing here needs to be part of that.
	 */
	public Holder<Attribute> attribute() {
		return switch (this) {
			case STRENGTH -> GrandCraftAttributes.STRENGTH;
			case AGILITY -> GrandCraftAttributes.AGILITY;
			case CONSTITUTION -> GrandCraftAttributes.CONSTITUTION;
			case ARCANE -> GrandCraftAttributes.ARCANE;
		};
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

	/** @return the stat with this name, or null when nothing matches. */
	public static CharacterStat byId(String id) {
		for (CharacterStat stat : values()) {
			if (stat.id.equals(id)) {
				return stat;
			}
		}

		return null;
	}
}

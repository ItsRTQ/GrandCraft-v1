package com.hrtq.grandcraft.player;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

public enum PlayerClass implements StringRepresentable {
	PEASANT("peasant"),
	ARCANIST("arcanist"),
	TEMPLAR("templar"),
	WITCH("witch"),
	RITUALIST("ritualist"),
	MERCENARY("mercenary");

	public static final Codec<PlayerClass> CODEC = StringRepresentable.fromEnum(PlayerClass::values);
	public static final StreamCodec<ByteBuf, PlayerClass> STREAM_CODEC =
			ByteBufCodecs.STRING_UTF8.map(PlayerClass::byId, PlayerClass::getSerializedName);

	public static final List<PlayerClass> SELECTABLE = List.of(ARCANIST, TEMPLAR, WITCH, RITUALIST, MERCENARY);

	private final String id;

	PlayerClass(String id) {
		this.id = id;
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

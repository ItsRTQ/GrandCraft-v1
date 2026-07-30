package com.hrtq.grandcraft.network;

import com.hrtq.grandcraft.GrandCraft;
import com.hrtq.grandcraft.player.PlayerClass;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SelectClassPayload(PlayerClass playerClass) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SelectClassPayload> TYPE =
			new CustomPacketPayload.Type<>(GrandCraft.id("select_class"));
	public static final StreamCodec<ByteBuf, SelectClassPayload> STREAM_CODEC =
			PlayerClass.STREAM_CODEC.map(SelectClassPayload::new, SelectClassPayload::playerClass);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

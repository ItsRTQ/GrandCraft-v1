package com.hrtq.grandcraft.network;

import com.hrtq.grandcraft.GrandCraft;
import com.hrtq.grandcraft.config.GameSettings;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: apply these general settings.
 *
 * <p>Untrusted. Any connected client can send this at any time, so the receiver
 * re-checks permissions — the command's permission requirement guards the command,
 * not the packet.
 */
public record ApplyGameConfigPayload(GameSettings settings) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<ApplyGameConfigPayload> TYPE =
			new CustomPacketPayload.Type<>(GrandCraft.id("apply_game_config"));

	public static final StreamCodec<ByteBuf, ApplyGameConfigPayload> STREAM_CODEC =
			GameSettings.STREAM_CODEC.map(ApplyGameConfigPayload::new, ApplyGameConfigPayload::settings);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

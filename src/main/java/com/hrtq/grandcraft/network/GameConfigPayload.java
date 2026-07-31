package com.hrtq.grandcraft.network;

import com.hrtq.grandcraft.GrandCraft;
import com.hrtq.grandcraft.config.GameSettings;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: these are the general settings now in force.
 *
 * <p>Sent to every player on join and again whenever an admin changes them,
 * because settings like health bars are only meaningful to the client renderer and
 * a client cannot read the server's copy.
 *
 * @param openScreen true when this was asked for by {@code /grandcraft config game}
 *                   and the config screen should open, rather than being a plain
 *                   sync the player did not request.
 */
public record GameConfigPayload(GameSettings settings, boolean openScreen) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<GameConfigPayload> TYPE =
			new CustomPacketPayload.Type<>(GrandCraft.id("game_config"));

	public static final StreamCodec<ByteBuf, GameConfigPayload> STREAM_CODEC = StreamCodec.of(
			(buf, payload) -> {
				GameSettings.STREAM_CODEC.encode(buf, payload.settings());
				buf.writeBoolean(payload.openScreen());
			},
			buf -> new GameConfigPayload(GameSettings.STREAM_CODEC.decode(buf), buf.readBoolean()));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

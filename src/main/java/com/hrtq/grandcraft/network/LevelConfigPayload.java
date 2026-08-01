package com.hrtq.grandcraft.network;

import com.hrtq.grandcraft.GrandCraft;
import com.hrtq.grandcraft.progression.LevelSettings;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: these are the level settings now in force.
 *
 * <p>Sent to every player on join and again whenever an admin changes them, the same
 * double duty {@link GameConfigPayload} and {@link StatConfigPayload} do — the
 * character sheet draws progress towards the next level and cannot work out what that
 * level costs without them.
 *
 * @param openScreen true when this was asked for by {@code /grandcraft config levels}
 *                   and the config screen should open, rather than being a plain sync
 *                   the player did not request.
 */
public record LevelConfigPayload(LevelSettings settings, boolean openScreen) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<LevelConfigPayload> TYPE =
			new CustomPacketPayload.Type<>(GrandCraft.id("level_config"));

	public static final StreamCodec<ByteBuf, LevelConfigPayload> STREAM_CODEC = StreamCodec.of(
			(buf, payload) -> {
				LevelSettings.STREAM_CODEC.encode(buf, payload.settings());
				buf.writeBoolean(payload.openScreen());
			},
			buf -> new LevelConfigPayload(LevelSettings.STREAM_CODEC.decode(buf), buf.readBoolean()));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

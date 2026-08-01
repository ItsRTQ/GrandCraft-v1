package com.hrtq.grandcraft.network;

import com.hrtq.grandcraft.GrandCraft;
import com.hrtq.grandcraft.progression.LevelSettings;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: apply these level settings.
 *
 * <p>Untrusted. Any connected client can send this at any time, so the receiver
 * re-checks permissions — the command's permission requirement guards the command,
 * not the packet — and clamps, so a hand-built packet cannot make every level free.
 */
public record ApplyLevelConfigPayload(LevelSettings settings) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<ApplyLevelConfigPayload> TYPE =
			new CustomPacketPayload.Type<>(GrandCraft.id("apply_level_config"));

	public static final StreamCodec<ByteBuf, ApplyLevelConfigPayload> STREAM_CODEC =
			LevelSettings.STREAM_CODEC.map(ApplyLevelConfigPayload::new, ApplyLevelConfigPayload::settings);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

package com.hrtq.grandcraft.network;

import com.hrtq.grandcraft.GrandCraft;
import com.hrtq.grandcraft.stats.StatSettings;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: apply these stat settings.
 *
 * <p>Untrusted. Any connected client can send this at any time, so the receiver
 * re-checks permissions — the command's permission requirement guards the command,
 * not the packet.
 */
public record ApplyStatConfigPayload(StatSettings settings) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<ApplyStatConfigPayload> TYPE =
			new CustomPacketPayload.Type<>(GrandCraft.id("apply_stat_config"));

	public static final StreamCodec<ByteBuf, ApplyStatConfigPayload> STREAM_CODEC =
			StatSettings.STREAM_CODEC.map(ApplyStatConfigPayload::new, ApplyStatConfigPayload::settings);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

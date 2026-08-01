package com.hrtq.grandcraft.network;

import com.hrtq.grandcraft.GrandCraft;
import com.hrtq.grandcraft.stats.CharacterStat;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: put one of my unspent stat points into this stat.
 *
 * <p>Untrusted, and carries only the choice. Whether a point is available, whether
 * the stat can take it, and what the resulting stat value is are all decided by the
 * server — the client is asking, not telling.
 *
 * <p>{@link #stat()} is null when the name on the wire was not one of the four; the
 * receiver drops the message rather than guessing which stat was meant.
 */
public record SpendStatPointPayload(CharacterStat stat) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SpendStatPointPayload> TYPE =
			new CustomPacketPayload.Type<>(GrandCraft.id("spend_stat_point"));

	public static final StreamCodec<ByteBuf, SpendStatPointPayload> STREAM_CODEC =
			CharacterStat.STREAM_CODEC.map(SpendStatPointPayload::new, SpendStatPointPayload::stat);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

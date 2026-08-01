package com.hrtq.grandcraft.network;

import com.hrtq.grandcraft.GrandCraft;
import com.hrtq.grandcraft.stats.CharacterPool;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: put one of my unspent attribute points into this pool.
 *
 * <p>The counterpart to {@link SpendStatPointPayload}, and untrusted in exactly the
 * same way — it carries the choice and nothing else.
 *
 * <p>{@link #pool()} is null when the name on the wire was not one of the three; the
 * receiver drops the message rather than guessing which pool was meant.
 */
public record SpendPoolPointPayload(CharacterPool pool) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SpendPoolPointPayload> TYPE =
			new CustomPacketPayload.Type<>(GrandCraft.id("spend_pool_point"));

	public static final StreamCodec<ByteBuf, SpendPoolPointPayload> STREAM_CODEC =
			CharacterPool.STREAM_CODEC.map(SpendPoolPointPayload::new, SpendPoolPointPayload::pool);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

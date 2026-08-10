package com.hrtq.grandcraft.network;

import com.hrtq.grandcraft.GrandCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: I am holding the key that ends this, or I have let go.
 *
 * <p>Shaped exactly like {@link GuardPayload} and re-asserted on the same keepalive,
 * for the same reason: a held key whose release packet was lost must not leave the
 * server thinking it is still down. Here the stakes are higher than a guard that will
 * not drop — a lost release would kill the player.
 *
 * <p>The client reports only that a key is held. Whether the player is even in a
 * state where that means anything, and how long the hold has to last, are the
 * server's — see {@code Downed}.
 */
public record GiveUpPayload(boolean held) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<GiveUpPayload> TYPE =
			new CustomPacketPayload.Type<>(GrandCraft.id("give_up"));

	public static final StreamCodec<ByteBuf, GiveUpPayload> STREAM_CODEC = StreamCodec.of(
			(buf, payload) -> buf.writeBoolean(payload.held()),
			buf -> new GiveUpPayload(buf.readBoolean()));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

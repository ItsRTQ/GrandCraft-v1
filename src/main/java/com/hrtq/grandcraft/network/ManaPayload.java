package com.hrtq.grandcraft.network;

import com.hrtq.grandcraft.GrandCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: the state of your mana pool.
 *
 * <p>Same design as {@link StaminaPayload} — a snapshot when the value has moved
 * enough to be worth a packet, and enough alongside it for the client to carry the
 * value forward between messages. {@code regenDelayTicks} is therefore the delay
 * <em>remaining</em> at the moment of sending, not the configured one.
 *
 * <p>Purely cosmetic today. Nothing spends mana, so nothing on the client decides
 * anything from this — which is exactly why it carries no {@code exhausted} flag and
 * no per-action cost. Both are stamina's answers to problems mana does not have yet.
 */
public record ManaPayload(
		float current,
		float max,
		int regenPerSecond,
		int regenDelayTicks) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<ManaPayload> TYPE =
			new CustomPacketPayload.Type<>(GrandCraft.id("mana"));

	public static final StreamCodec<ByteBuf, ManaPayload> STREAM_CODEC = StreamCodec.of(
			(buf, payload) -> {
				buf.writeFloat(payload.current());
				buf.writeFloat(payload.max());
				buf.writeInt(payload.regenPerSecond());
				buf.writeInt(payload.regenDelayTicks());
			},
			// Java evaluates arguments left to right, so this matches the writes above.
			buf -> new ManaPayload(
					buf.readFloat(), buf.readFloat(), buf.readInt(), buf.readInt()));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

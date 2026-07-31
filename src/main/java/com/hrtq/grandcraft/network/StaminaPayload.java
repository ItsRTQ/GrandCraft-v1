package com.hrtq.grandcraft.network;

import com.hrtq.grandcraft.GrandCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: the state of your stamina pool.
 *
 * <p>Sent when the value has moved enough to be worth a packet rather than every
 * tick, following the same design as {@link AttackLockoutPayload}: the client is
 * given enough to carry on by itself and does so between messages.
 *
 * <p>{@code regenDelayTicks} is the delay <em>remaining</em> at the moment of
 * sending, not the actor's configured delay. Together with {@code regenPerSecond}
 * that is exactly what the client needs to extrapolate the bar forward, and it is
 * why the configured value alone would not do.
 *
 * <p>{@code exhausted} is sent rather than derived. The client could compare the
 * value against the recovery threshold itself, but the server decides when an actor
 * may act again and the bar must not disagree with it.
 *
 * <p>{@code jumpCost} is here because the client has to <em>prevent</em> a jump it
 * cannot pay for, not merely draw one. A jump is client-predicted and already
 * reported by the time the server sees it, so a client that guessed at the price
 * would hand out free jumps at low stamina.
 *
 * <p>Otherwise cosmetic: the server enforces every cost it is able to. A client that
 * ignored this would only be denying itself the bar.
 */
public record StaminaPayload(
		float current,
		float max,
		int regenPerSecond,
		int regenDelayTicks,
		int jumpCost,
		boolean exhausted) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<StaminaPayload> TYPE =
			new CustomPacketPayload.Type<>(GrandCraft.id("stamina"));

	public static final StreamCodec<ByteBuf, StaminaPayload> STREAM_CODEC = StreamCodec.of(
			(buf, payload) -> {
				buf.writeFloat(payload.current());
				buf.writeFloat(payload.max());
				buf.writeInt(payload.regenPerSecond());
				buf.writeInt(payload.regenDelayTicks());
				buf.writeInt(payload.jumpCost());
				buf.writeBoolean(payload.exhausted());
			},
			// Java evaluates arguments left to right, so this matches the writes above.
			buf -> new StaminaPayload(
					buf.readFloat(), buf.readFloat(),
					buf.readInt(), buf.readInt(), buf.readInt(), buf.readBoolean()));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

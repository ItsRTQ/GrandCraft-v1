package com.hrtq.grandcraft.network;

import com.hrtq.grandcraft.GrandCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: this entity has entered this combat phase.
 *
 * <p>The animation layer's only input. Sent on transitions rather than streamed,
 * following {@link AttackLockoutPayload}: the client is told how long the phase
 * runs and animates it out against its own clock, which is both smoother than
 * twenty updates a second and cheaper.
 *
 * <p>Sent to everyone <em>tracking</em> the entity, not to the entity's own
 * player. A wind-up is information for the opponent — a telegraph only its owner
 * could see would not be a telegraph.
 *
 * <p>{@code remainingTicks} and {@code totalTicks} are both carried so one packet
 * shape serves two cases. On a transition they are equal. When a viewer starts
 * tracking an actor that is already mid-wind-up they are not, and sending only a
 * duration would restart the animation from the top and lie about when the hit
 * lands.
 *
 * <p>{@code entityId} is a network id, valid only within this connection — which
 * is why the client's store is cleared on disconnect.
 *
 * <p>Purely cosmetic. The server runs the phases whether or not anyone is
 * listening; this only lets them be seen.
 */
public record CombatPhasePayload(
		int entityId,
		int phase,
		int remainingTicks,
		int totalTicks) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<CombatPhasePayload> TYPE =
			new CustomPacketPayload.Type<>(GrandCraft.id("combat_phase"));

	public static final StreamCodec<ByteBuf, CombatPhasePayload> STREAM_CODEC = StreamCodec.of(
			(buf, payload) -> {
				buf.writeInt(payload.entityId());
				buf.writeInt(payload.phase());
				buf.writeInt(payload.remainingTicks());
				buf.writeInt(payload.totalTicks());
			},
			// Java evaluates arguments left to right, so this matches the writes above.
			buf -> new CombatPhasePayload(
					buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt()));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

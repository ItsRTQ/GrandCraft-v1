package com.hrtq.grandcraft.network;

import com.hrtq.grandcraft.GrandCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: your own bleed-out clock, and what is happening to it.
 *
 * <p><strong>Owner only, and that is the split worth understanding.</strong> The
 * <em>pose</em> is broadcast to everyone through {@link CombatPhasePayload}, because
 * an ally who cannot see who is down cannot go and pick them up. These are the
 * numbers behind it, and only the player they belong to has a HUD that draws them.
 *
 * <p>Sent on a throttle rather than every tick, the same bargain
 * {@link StaminaPayload} makes: {@code bleedOutTicks} moves every tick and the client
 * counts it down itself between packets, so the routine send exists to correct the
 * drift — and to report the one thing that cannot be extrapolated, a hit that took a
 * chunk out of the clock at once.
 *
 * <p>The two progress counters are sent <em>with their totals</em> rather than as
 * fractions. Sending a fraction would be the smaller packet, but the HUD draws a bar
 * and a label from the same pair and the totals are the only part of the settings a
 * client has any way of knowing — combat tuning is server-held and reaches a client
 * only when an admin opens the screen. This is {@link StaminaPayload#jumpCost()}'s
 * reason exactly: the figure travels because nothing else would tell the client.
 *
 * <p>Purely a readout. The server owns every decision this describes — a client that
 * ignored this packet would still fall down, still bleed out and still die on time.
 */
public record DownedPayload(
		boolean downed,
		int bleedOutTicks,
		int bleedOutTotalTicks,
		int reviveTicks,
		int reviveTotalTicks,
		int giveUpTicks,
		int giveUpTotalTicks) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<DownedPayload> TYPE =
			new CustomPacketPayload.Type<>(GrandCraft.id("downed"));

	public static final StreamCodec<ByteBuf, DownedPayload> STREAM_CODEC = StreamCodec.of(
			(buf, payload) -> {
				buf.writeBoolean(payload.downed());
				buf.writeInt(payload.bleedOutTicks());
				buf.writeInt(payload.bleedOutTotalTicks());
				buf.writeInt(payload.reviveTicks());
				buf.writeInt(payload.reviveTotalTicks());
				buf.writeInt(payload.giveUpTicks());
				buf.writeInt(payload.giveUpTotalTicks());
			},
			// Java evaluates arguments left to right, so this matches the writes above.
			buf -> new DownedPayload(
					buf.readBoolean(), buf.readInt(), buf.readInt(),
					buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt()));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

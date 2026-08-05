package com.hrtq.grandcraft.network;

import com.hrtq.grandcraft.GrandCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: your Combat Master window is this many ticks long, starting now.
 * Zero closes it.
 *
 * <p>Sent exactly twice per window — on open, and on an early close — and never for
 * expiry. The client was told the length, so it already knows when the window ends; a
 * per-tick countdown packet would be twenty messages a second to say something it could
 * work out itself.
 *
 * <p>That also makes the badge's countdown a <em>deadline</em> rather than an
 * extrapolation, which is the one shape that cannot drift. The rate-based bars in this
 * mod need a staleness test for exactly the reason this does not.
 *
 * <p>The value is the <strong>effective</strong> duration the server actually granted,
 * never the configured one — {@code tuning.md} lesson 13.
 */
public record CombatMasterPayload(int remainingTicks) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<CombatMasterPayload> TYPE =
			new CustomPacketPayload.Type<>(GrandCraft.id("combat_master"));

	public static final StreamCodec<ByteBuf, CombatMasterPayload> STREAM_CODEC =
			ByteBufCodecs.VAR_INT.map(CombatMasterPayload::new, CombatMasterPayload::remainingTicks);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

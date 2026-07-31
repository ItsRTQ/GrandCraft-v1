package com.hrtq.grandcraft.network;

import com.hrtq.grandcraft.GrandCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: you cannot attack for this many ticks.
 *
 * <p>Sent when a lockout begins rather than streamed while it runs — the client
 * counts the window down itself, which is accurate enough over the handful of
 * ticks involved and costs one packet per swing, the same rate the attack packets
 * already flow at.
 *
 * <p>Purely cosmetic: the server enforces the lockout regardless, and this only
 * lets the client draw the attack indicator honestly.
 */
public record AttackLockoutPayload(int ticks) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<AttackLockoutPayload> TYPE =
			new CustomPacketPayload.Type<>(GrandCraft.id("attack_lockout"));

	public static final StreamCodec<ByteBuf, AttackLockoutPayload> STREAM_CODEC =
			ByteBufCodecs.VAR_INT.map(AttackLockoutPayload::new, AttackLockoutPayload::ticks);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

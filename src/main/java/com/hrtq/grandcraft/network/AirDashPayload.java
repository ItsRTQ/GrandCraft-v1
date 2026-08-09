package com.hrtq.grandcraft.network;

import com.hrtq.grandcraft.GrandCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: the player pressed jump in mid-air, travelling this way.
 *
 * <p>Sent rather than derived for the reason {@link DodgePayload} gives — movement input
 * lives on the client, and the server's {@code lastClientInput} only ever carries the
 * sneak flag. Unlike the dodge, a zero vector here is a legitimate answer meaning "no
 * input at all", and the server turns that into the look direction; the client
 * deliberately does <em>not</em> substitute a backstep, which is right for a roll and
 * wrong for a dash.
 *
 * <p>A horizontal direction only, in world space, normalised by the server. <strong>The
 * vertical component is absent and the reason is stronger here than it is for the
 * dodge</strong>: this move already grants lift of its own, so a client-supplied
 * vertical would be a straightforward flight exploit rather than merely a possible one.
 *
 * <p>The server decides everything else — whether the dash is legal, how many are left,
 * what it costs, how far it goes, and whether the direction sent is even the one used
 * (off a wall it is not; the look angle is). A modified client can ask to dash in any
 * direction at any time and will simply be refused.
 */
public record AirDashPayload(float x, float z) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<AirDashPayload> TYPE =
			new CustomPacketPayload.Type<>(GrandCraft.id("air_dash"));

	public static final StreamCodec<ByteBuf, AirDashPayload> STREAM_CODEC = StreamCodec.of(
			(buf, payload) -> {
				buf.writeFloat(payload.x());
				buf.writeFloat(payload.z());
			},
			// Java evaluates arguments left to right, so this matches the writes above.
			buf -> new AirDashPayload(buf.readFloat(), buf.readFloat()));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

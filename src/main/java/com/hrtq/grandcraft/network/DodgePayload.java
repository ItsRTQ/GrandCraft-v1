package com.hrtq.grandcraft.network;

import com.hrtq.grandcraft.GrandCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: the player pressed dodge, in this direction.
 *
 * <p>Carries the direction because the server cannot work it out. A dodge goes
 * where the player is <em>asking</em> to move, and movement input lives on the
 * client — {@code lastClientInput} on the server side only ever carries the sneak
 * flag. Sending it is also what lets a standing player backstep rather than being
 * launched wherever they happen to be looking.
 *
 * <p>A horizontal direction only, in world space, normalised by the server. The
 * vertical component is deliberately absent: a dodge is a roll, not a leap, and
 * accepting a client-supplied vertical would be a free flight exploit.
 *
 * <p>The server decides everything else — whether the dodge is legal, what it
 * costs, how far it goes and how long it protects. A modified client can ask to
 * dodge in any direction at any time and will simply be refused.
 */
public record DodgePayload(float x, float z) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<DodgePayload> TYPE =
			new CustomPacketPayload.Type<>(GrandCraft.id("dodge"));

	public static final StreamCodec<ByteBuf, DodgePayload> STREAM_CODEC = StreamCodec.of(
			(buf, payload) -> {
				buf.writeFloat(payload.x());
				buf.writeFloat(payload.z());
			},
			// Java evaluates arguments left to right, so this matches the writes above.
			buf -> new DodgePayload(buf.readFloat(), buf.readFloat()));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

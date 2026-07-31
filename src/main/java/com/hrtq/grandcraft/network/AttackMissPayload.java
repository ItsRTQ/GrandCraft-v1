package com.hrtq.grandcraft.network;

import com.hrtq.grandcraft.GrandCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: that swing hit nothing.
 *
 * <p>A miss produces no vanilla packet the server could use. The interact packet is
 * only sent when an entity was hit, and the swing packet cannot be told apart from
 * the one mining sends — hooking that would put attack endlag on every pickaxe
 * swing. So the client, which is the only side that knows the swing was aimed at
 * nothing, says so explicitly.
 *
 * <p>Carries no data: the server decides the endlag from its own settings, and
 * ignores the message entirely if the player is not free to attack. A client that
 * withheld it would only be declining its own whiff penalty.
 */
public record AttackMissPayload() implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<AttackMissPayload> TYPE =
			new CustomPacketPayload.Type<>(GrandCraft.id("attack_miss"));

	public static final StreamCodec<ByteBuf, AttackMissPayload> STREAM_CODEC =
			StreamCodec.unit(new AttackMissPayload());

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

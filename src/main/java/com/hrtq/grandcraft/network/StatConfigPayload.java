package com.hrtq.grandcraft.network;

import com.hrtq.grandcraft.GrandCraft;
import com.hrtq.grandcraft.stats.StatSettings;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: these are the stat settings now in force.
 *
 * <p>Sent to every player on join and again whenever an admin changes them, the same
 * double duty {@link GameConfigPayload} does — the character sheet explains what each
 * stat is currently worth, and it cannot do that from the stat value alone.
 *
 * @param openScreen true when this was asked for by {@code /grandcraft config stats}
 *                   and the config screen should open, rather than being a plain
 *                   sync the player did not request.
 */
public record StatConfigPayload(StatSettings settings, boolean openScreen) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<StatConfigPayload> TYPE =
			new CustomPacketPayload.Type<>(GrandCraft.id("stat_config"));

	public static final StreamCodec<ByteBuf, StatConfigPayload> STREAM_CODEC = StreamCodec.of(
			(buf, payload) -> {
				StatSettings.STREAM_CODEC.encode(buf, payload.settings());
				buf.writeBoolean(payload.openScreen());
			},
			buf -> new StatConfigPayload(StatSettings.STREAM_CODEC.decode(buf), buf.readBoolean()));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

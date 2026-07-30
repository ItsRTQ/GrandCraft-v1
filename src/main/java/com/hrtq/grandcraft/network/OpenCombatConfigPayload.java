package com.hrtq.grandcraft.network;

import com.hrtq.grandcraft.GrandCraft;
import com.hrtq.grandcraft.combat.CombatSettings;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: open the combat config screen, seeded with the values
 * currently in force.
 *
 * <p>The server sends the snapshot rather than letting the client assume defaults,
 * so the sliders always open showing the real state.
 */
public record OpenCombatConfigPayload(CombatSettings settings) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<OpenCombatConfigPayload> TYPE =
			new CustomPacketPayload.Type<>(GrandCraft.id("open_combat_config"));
	public static final StreamCodec<ByteBuf, OpenCombatConfigPayload> STREAM_CODEC =
			CombatSettings.STREAM_CODEC.map(OpenCombatConfigPayload::new, OpenCombatConfigPayload::settings);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

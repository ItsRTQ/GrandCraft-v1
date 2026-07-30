package com.hrtq.grandcraft.network;

import com.hrtq.grandcraft.GrandCraft;
import com.hrtq.grandcraft.combat.CombatSettings;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: apply these combat values.
 *
 * <p>Untrusted. Any connected client can send this at any time, so the receiver
 * re-checks permissions and clamps the values — the command's permission
 * requirement guards the command, not the packet.
 */
public record ApplyCombatConfigPayload(CombatSettings settings) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<ApplyCombatConfigPayload> TYPE =
			new CustomPacketPayload.Type<>(GrandCraft.id("apply_combat_config"));
	public static final StreamCodec<ByteBuf, ApplyCombatConfigPayload> STREAM_CODEC =
			CombatSettings.STREAM_CODEC.map(ApplyCombatConfigPayload::new, ApplyCombatConfigPayload::settings);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

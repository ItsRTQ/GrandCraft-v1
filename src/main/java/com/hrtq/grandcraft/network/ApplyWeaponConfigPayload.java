package com.hrtq.grandcraft.network;

import com.hrtq.grandcraft.GrandCraft;
import com.hrtq.grandcraft.combat.WeaponSettings;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: apply edited weapon settings.
 *
 * <p>The receiver re-checks the sender's permission and clamps every value. The
 * command's permission guards the command, not this packet — any connected client
 * can send one at any time.
 */
public record ApplyWeaponConfigPayload(WeaponSettings settings) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<ApplyWeaponConfigPayload> TYPE =
			new CustomPacketPayload.Type<>(GrandCraft.id("apply_weapon_config"));
	public static final StreamCodec<ByteBuf, ApplyWeaponConfigPayload> STREAM_CODEC =
			WeaponSettings.STREAM_CODEC.map(ApplyWeaponConfigPayload::new, ApplyWeaponConfigPayload::settings);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

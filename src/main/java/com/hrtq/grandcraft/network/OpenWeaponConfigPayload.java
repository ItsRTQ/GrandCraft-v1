package com.hrtq.grandcraft.network;

import com.hrtq.grandcraft.GrandCraft;
import com.hrtq.grandcraft.combat.WeaponSettings;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: open the weapon config screen, seeded with the values currently
 * in force.
 *
 * <p>The server sends the snapshot rather than letting the client assume defaults,
 * so the fields always open showing the real state. Weapon settings are otherwise
 * server-held — this packet is the only time they reach a client, and only for the
 * admin who asked.
 */
public record OpenWeaponConfigPayload(WeaponSettings settings) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<OpenWeaponConfigPayload> TYPE =
			new CustomPacketPayload.Type<>(GrandCraft.id("open_weapon_config"));
	public static final StreamCodec<ByteBuf, OpenWeaponConfigPayload> STREAM_CODEC =
			WeaponSettings.STREAM_CODEC.map(OpenWeaponConfigPayload::new, OpenWeaponConfigPayload::settings);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

package com.hrtq.grandcraft.network;

import com.hrtq.grandcraft.GrandCraft;
import com.hrtq.grandcraft.combat.WeaponSettings;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: the weapon settings currently in force, and whether to open the
 * screen with them.
 *
 * <p>One packet doing double duty, shaped exactly like {@link GameConfigPayload}:
 * <strong>every</strong> client is told the values, and only the admin who asked for
 * the screen gets {@code openScreen}.
 *
 * <p>This replaced an open-the-screen-only packet the moment weapon damage started
 * being drawn on a tooltip. The client cannot work out what a weapon will do in its
 * holder's hands without the scaling weights and the global down-scale, and a tooltip
 * that guesses at them would state a number the server disagrees with — which is worse
 * than no tooltip, because it would be believed.
 */
public record WeaponConfigPayload(WeaponSettings settings, boolean openScreen)
		implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<WeaponConfigPayload> TYPE =
			new CustomPacketPayload.Type<>(GrandCraft.id("weapon_config"));

	public static final StreamCodec<ByteBuf, WeaponConfigPayload> STREAM_CODEC = StreamCodec.of(
			(buf, payload) -> {
				WeaponSettings.STREAM_CODEC.encode(buf, payload.settings());
				ByteBufCodecs.BOOL.encode(buf, payload.openScreen());
			},
			buf -> new WeaponConfigPayload(
					WeaponSettings.STREAM_CODEC.decode(buf), ByteBufCodecs.BOOL.decode(buf)));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

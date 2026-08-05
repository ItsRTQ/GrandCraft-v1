package com.hrtq.grandcraft.network;

import com.hrtq.grandcraft.GrandCraft;
import com.hrtq.grandcraft.skill.SkillSettings;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: apply these skill settings.
 *
 * <p>Untrusted. Any connected client can send this at any time, so the receiver
 * re-checks permissions — the command's permission requirement guards the command, not
 * the packet — and clamps, so a hand-built packet cannot grant a thousandfold blow.
 */
public record ApplySkillConfigPayload(SkillSettings settings) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<ApplySkillConfigPayload> TYPE =
			new CustomPacketPayload.Type<>(GrandCraft.id("apply_skill_config"));

	public static final StreamCodec<ByteBuf, ApplySkillConfigPayload> STREAM_CODEC =
			SkillSettings.STREAM_CODEC.map(
					ApplySkillConfigPayload::new, ApplySkillConfigPayload::settings);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

package com.hrtq.grandcraft.network;

import com.hrtq.grandcraft.GrandCraft;
import com.hrtq.grandcraft.skill.SkillSettings;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: here are the skill settings, open the editor.
 *
 * <p>Shaped like {@link OpenCombatConfigPayload} rather than like
 * {@link LevelConfigPayload}, and for the same reason those two differ: the skill
 * settings are <strong>server-held</strong>. Nothing on the client reads them — the
 * Combat Master badge is told how many ticks remain, not how many it started with — so
 * they are sent only to whoever asked for the screen, and carry no "is this a sync or a
 * request" flag because they are never a sync.
 */
public record OpenSkillConfigPayload(SkillSettings settings) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<OpenSkillConfigPayload> TYPE =
			new CustomPacketPayload.Type<>(GrandCraft.id("open_skill_config"));

	public static final StreamCodec<ByteBuf, OpenSkillConfigPayload> STREAM_CODEC =
			SkillSettings.STREAM_CODEC.map(
					OpenSkillConfigPayload::new, OpenSkillConfigPayload::settings);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

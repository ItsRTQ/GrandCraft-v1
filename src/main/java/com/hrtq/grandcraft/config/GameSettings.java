package com.hrtq.grandcraft.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * Mod-wide settings that are not specific to combat.
 *
 * <p>This is the home for anything general enough that filing it under combat
 * would be wrong. It is server-authoritative like the combat settings, but unlike
 * them it is also pushed to every client, because some of it — health bars — is
 * only meaningful to the renderer.
 *
 * <p>Fields are optional in the codec so a file written by an older build keeps
 * loading as settings are added.
 */
public record GameSettings(boolean healthBars, boolean combatAnimations) {

	public static final GameSettings DEFAULT = new GameSettings(true, true);

	public static final Codec<GameSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.BOOL.optionalFieldOf("health_bars", DEFAULT.healthBars())
					.forGetter(GameSettings::healthBars),
			Codec.BOOL.optionalFieldOf("combat_animations", DEFAULT.combatAnimations())
					.forGetter(GameSettings::combatAnimations)
	).apply(instance, GameSettings::new));

	public static final StreamCodec<ByteBuf, GameSettings> STREAM_CODEC = StreamCodec.of(
			(buf, settings) -> {
				buf.writeBoolean(settings.healthBars());
				buf.writeBoolean(settings.combatAnimations());
			},
			// Java evaluates arguments left to right, so this matches the writes above.
			buf -> new GameSettings(buf.readBoolean(), buf.readBoolean()));
}

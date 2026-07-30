package com.hrtq.grandcraft.network;

import com.hrtq.grandcraft.GrandCraft;
import com.hrtq.grandcraft.player.GrandCraftAttachments;
import com.hrtq.grandcraft.player.PlayerClass;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

public final class GrandCraftNetworking {
	private GrandCraftNetworking() {
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(SelectClassPayload.TYPE, SelectClassPayload.STREAM_CODEC);

		ServerPlayNetworking.registerGlobalReceiver(SelectClassPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			PlayerClass chosen = payload.playerClass();

			if (chosen == PlayerClass.PEASANT) {
				return;
			}

			PlayerClass current = player.getAttachedOrElse(GrandCraftAttachments.PLAYER_CLASS, PlayerClass.PEASANT);

			if (current != PlayerClass.PEASANT) {
				GrandCraft.LOGGER.warn("{} tried to select class {} but is already {}",
						player.getGameProfile().name(), chosen.getSerializedName(), current.getSerializedName());
				return;
			}

			player.setAttached(GrandCraftAttachments.PLAYER_CLASS, chosen);
			GrandCraft.LOGGER.info("{} selected class {}", player.getGameProfile().name(), chosen.getSerializedName());

			// Low pitch also slows the sample down, giving a deep, drawn-out blast.
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.GHAST_SHOOT, SoundSource.PLAYERS, 1.0F, 0.5F);

			player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
			player.connection.send(new ClientboundSetSubtitleTextPacket(
					Component.translatable("screen.grandcraft.class_announcement")));
			player.connection.send(new ClientboundSetTitleTextPacket(chosen.displayName()));
		});
	}
}

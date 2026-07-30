package com.hrtq.grandcraft.network;

import com.hrtq.grandcraft.GrandCraft;
import com.hrtq.grandcraft.combat.CombatConfigFile;
import com.hrtq.grandcraft.combat.CombatTuning;
import com.hrtq.grandcraft.player.GrandCraftAttachments;
import com.hrtq.grandcraft.player.PlayerClass;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
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
		PayloadTypeRegistry.serverboundPlay()
				.register(ApplyCombatConfigPayload.TYPE, ApplyCombatConfigPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay()
				.register(OpenCombatConfigPayload.TYPE, OpenCombatConfigPayload.STREAM_CODEC);

		registerClassSelection();
		registerCombatConfig();
	}

	/** Sends the values currently in force so the player's config screen can open. */
	public static void sendCombatConfig(ServerPlayer player) {
		ServerPlayNetworking.send(player, new OpenCombatConfigPayload(CombatTuning.current()));
	}

	private static void registerClassSelection() {
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

	private static void registerCombatConfig() {
		ServerPlayNetworking.registerGlobalReceiver(ApplyCombatConfigPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();

			// The permission on /grandcraft config guards the command, not this
			// packet. Any connected client can send one at any time, so the check
			// has to happen here or combat tuning would be world-writable.
			if (!Commands.LEVEL_GAMEMASTERS.check(player.permissions())) {
				GrandCraft.LOGGER.warn("{} tried to change combat config without permission",
						player.getGameProfile().name());
				return;
			}

			// set() clamps, so a hand-built packet cannot push a phase duration out
			// of range and throw inside AttackProfile later.
			CombatTuning.set(payload.settings());
			CombatConfigFile.save(CombatTuning.current());

			GrandCraft.LOGGER.info("{} updated combat tuning", player.getGameProfile().name());
			player.sendSystemMessage(Component.translatable("commands.grandcraft.config.saved"));
		});
	}
}

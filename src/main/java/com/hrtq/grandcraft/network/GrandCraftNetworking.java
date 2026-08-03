package com.hrtq.grandcraft.network;

import com.hrtq.grandcraft.GrandCraft;
import com.hrtq.grandcraft.combat.CombatConfigFile;
import com.hrtq.grandcraft.combat.CombatTuning;
import com.hrtq.grandcraft.combat.WeaponConfigFile;
import com.hrtq.grandcraft.combat.WeaponTuning;
import com.hrtq.grandcraft.config.GameConfigFile;
import com.hrtq.grandcraft.config.GameTuning;
import com.hrtq.grandcraft.player.GrandCraftAttachments;
import com.hrtq.grandcraft.player.PlayerClass;
import com.hrtq.grandcraft.progression.EssenceProgress;
import com.hrtq.grandcraft.progression.LevelConfigFile;
import com.hrtq.grandcraft.progression.LevelTuning;
import com.hrtq.grandcraft.stats.CharacterPool;
import com.hrtq.grandcraft.stats.CharacterStat;
import com.hrtq.grandcraft.stats.PlayerStats;
import com.hrtq.grandcraft.stats.StatConfigFile;
import com.hrtq.grandcraft.stats.StatConstants;
import com.hrtq.grandcraft.stats.StatTuning;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
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
		PayloadTypeRegistry.serverboundPlay()
				.register(ApplyGameConfigPayload.TYPE, ApplyGameConfigPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay()
				.register(GameConfigPayload.TYPE, GameConfigPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay()
				.register(AttackLockoutPayload.TYPE, AttackLockoutPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay()
				.register(AttackMissPayload.TYPE, AttackMissPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay()
				.register(StaminaPayload.TYPE, StaminaPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay()
				.register(CombatPhasePayload.TYPE, CombatPhasePayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay()
				.register(DodgePayload.TYPE, DodgePayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay()
				.register(GuardPayload.TYPE, GuardPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay()
				.register(ApplyStatConfigPayload.TYPE, ApplyStatConfigPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay()
				.register(StatConfigPayload.TYPE, StatConfigPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay()
				.register(ManaPayload.TYPE, ManaPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay()
				.register(ApplyLevelConfigPayload.TYPE, ApplyLevelConfigPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay()
				.register(LevelConfigPayload.TYPE, LevelConfigPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay()
				.register(SpendStatPointPayload.TYPE, SpendStatPointPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay()
				.register(SpendPoolPointPayload.TYPE, SpendPoolPointPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay()
				.register(ApplyWeaponConfigPayload.TYPE, ApplyWeaponConfigPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay()
				.register(WeaponConfigPayload.TYPE, WeaponConfigPayload.STREAM_CODEC);

		registerClassSelection();
		registerStatSpending();
		registerPoolSpending();
		registerCombatConfig();
		registerGameConfig();
		registerStatConfig();
		registerLevelConfig();
		registerWeaponConfig();

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer player = handler.getPlayer();

			// Stats are derived from the class table rather than stored, so this is
			// where an edit to that table reaches an existing character.
			PlayerStats.applyBaselines(player);

			// Clients need these before they render anything, and they have no way to
			// ask for them: the general settings drive the renderer, and the stat
			// settings are how the character sheet explains what a stat is worth.
			ServerPlayNetworking.send(player, new GameConfigPayload(GameTuning.current(), false));
			ServerPlayNetworking.send(player, new StatConfigPayload(StatTuning.current(), false));

			// The sheet draws progress towards the next level, which means it needs the
			// cost curve as well as the player's own progress.
			ServerPlayNetworking.send(player, new LevelConfigPayload(LevelTuning.current(), false));

			// Weapon settings joined this list when damage started being drawn on a
			// tooltip: the scaling weights and the global down-scale are half of what
			// decides the number a player reads before they swing.
			ServerPlayNetworking.send(player, new WeaponConfigPayload(WeaponTuning.current(), false));
		});
	}

	/** Sends the values currently in force so the player's config screen can open. */
	public static void sendCombatConfig(ServerPlayer player) {
		ServerPlayNetworking.send(player, new OpenCombatConfigPayload(CombatTuning.current()));
	}

	public static void sendGameConfig(ServerPlayer player) {
		ServerPlayNetworking.send(player, new GameConfigPayload(GameTuning.current(), true));
	}

	public static void sendStatConfig(ServerPlayer player) {
		ServerPlayNetworking.send(player, new StatConfigPayload(StatTuning.current(), true));
	}

	public static void sendLevelConfig(ServerPlayer player) {
		ServerPlayNetworking.send(player, new LevelConfigPayload(LevelTuning.current(), true));
	}

	public static void sendWeaponConfig(ServerPlayer player) {
		ServerPlayNetworking.send(player, new WeaponConfigPayload(WeaponTuning.current(), true));
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

			// The class is what the stats are derived from, so they are rewritten here
			// rather than waiting for the next login to notice.
			PlayerStats.applyBaselines(player);

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

	/**
	 * Commits one of a player's unspent stat points.
	 *
	 * <p>Every condition is re-checked here. The client only shows the buttons when it
	 * believes there are points, but that belief is a synced copy and a modified client
	 * need not hold it at all — so having a point, and the stat having room for it, are
	 * both decided on this side.
	 *
	 * <p>Deliberately not gated on having chosen a class. A peasant earns Essence like
	 * anyone else, and refusing the spend would bank points they could never use while
	 * still showing them a growing pile.
	 */
	private static void registerStatSpending() {
		ServerPlayNetworking.registerGlobalReceiver(SpendStatPointPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			CharacterStat stat = payload.stat();

			// Null means the name on the wire was not one of the four. Dropped rather
			// than guessed: spending a point into the wrong stat is irreversible.
			if (stat == null) {
				GrandCraft.LOGGER.warn("{} sent an unknown stat to spend into",
						player.getGameProfile().name());
				return;
			}

			EssenceProgress progress = PlayerStats.progressOf(player);

			if (progress.statPoints() <= 0) {
				GrandCraft.LOGGER.warn("{} tried to spend a stat point they do not have",
						player.getGameProfile().name());
				return;
			}

			// A RangedAttribute clamps silently, so a point spent past the ceiling
			// would simply disappear with nothing to tell the player why.
			if (PlayerStats.valueAfterSpend(player, stat) > StatConstants.MAX) {
				return;
			}

			player.setAttached(GrandCraftAttachments.ESSENCE_PROGRESS, progress.spendStatPoint(stat));

			// Rewritten here rather than on the next join, so the armour, health and
			// stamina that hang off the stat move the moment the point lands.
			PlayerStats.applyBaselines(player);
		});
	}

	/**
	 * Commits one of a player's unspent attribute points into a pool.
	 *
	 * <p>Nothing is written to the pools here. The point lands in the record, and
	 * {@code CombatController.syncStatEffects} notices on its next tick and moves the
	 * ceilings — which is the same pass that already owns every other attribute this
	 * mod applies, so there is never a second source of truth about a player's maximum.
	 * The cost is a tick of latency on a purchase, which is invisible.
	 */
	private static void registerPoolSpending() {
		ServerPlayNetworking.registerGlobalReceiver(SpendPoolPointPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			CharacterPool pool = payload.pool();

			if (pool == null) {
				GrandCraft.LOGGER.warn("{} sent an unknown pool to spend into",
						player.getGameProfile().name());
				return;
			}

			EssenceProgress progress = PlayerStats.progressOf(player);

			if (progress.poolPoints() <= 0) {
				GrandCraft.LOGGER.warn("{} tried to spend an attribute point they do not have",
						player.getGameProfile().name());
				return;
			}

			player.setAttached(GrandCraftAttachments.ESSENCE_PROGRESS, progress.spendPoolPoint(pool));
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

	private static void registerWeaponConfig() {
		ServerPlayNetworking.registerGlobalReceiver(ApplyWeaponConfigPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();

			// As with the others: the command's permission guards the command, not
			// this packet, which any connected client can send at any time.
			if (!Commands.LEVEL_GAMEMASTERS.check(player.permissions())) {
				GrandCraft.LOGGER.warn("{} tried to change weapon settings without permission",
						player.getGameProfile().name());
				return;
			}

			// set() clamps, so a hand-built packet cannot push a phase duration out of
			// range and throw inside AttackProfile later, nor send a zero-tick hit
			// window that could never connect.
			WeaponTuning.set(payload.settings());
			WeaponConfigFile.save(WeaponTuning.current());

			// Every client draws its own weapon tooltips from its own copy, so all of
			// them have to be told, not just the admin who made the change. Without this
			// an edit here would leave every other player's tooltip quietly lying.
			MinecraftServer server = player.level().getServer();

			if (server != null) {
				WeaponConfigPayload update = new WeaponConfigPayload(WeaponTuning.current(), false);

				for (ServerPlayer online : server.getPlayerList().getPlayers()) {
					ServerPlayNetworking.send(online, update);
				}
			}

			GrandCraft.LOGGER.info("{} updated weapon tuning", player.getGameProfile().name());
			player.sendSystemMessage(Component.translatable("commands.grandcraft.config.saved"));
		});
	}

	private static void registerGameConfig() {
		ServerPlayNetworking.registerGlobalReceiver(ApplyGameConfigPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();

			// As with combat: the command's permission guards the command, not this
			// packet, which any connected client can send at any time.
			if (!Commands.LEVEL_GAMEMASTERS.check(player.permissions())) {
				GrandCraft.LOGGER.warn("{} tried to change game settings without permission",
						player.getGameProfile().name());
				return;
			}

			GameTuning.set(payload.settings());
			GameConfigFile.save(GameTuning.current());

			// Every client renders from its own copy, so all of them have to be told,
			// not just the admin who made the change.
			MinecraftServer server = player.level().getServer();

			if (server != null) {
				GameConfigPayload update = new GameConfigPayload(GameTuning.current(), false);

				for (ServerPlayer online : server.getPlayerList().getPlayers()) {
					ServerPlayNetworking.send(online, update);
				}
			}

			GrandCraft.LOGGER.info("{} updated game settings", player.getGameProfile().name());
			player.sendSystemMessage(Component.translatable("commands.grandcraft.config.saved"));
		});
	}

	private static void registerStatConfig() {
		ServerPlayNetworking.registerGlobalReceiver(ApplyStatConfigPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();

			// As with the other two: the command's permission guards the command, not
			// this packet, which any connected client can send at any time.
			if (!Commands.LEVEL_GAMEMASTERS.check(player.permissions())) {
				GrandCraft.LOGGER.warn("{} tried to change stat settings without permission",
						player.getGameProfile().name());
				return;
			}

			// set() clamps, so a hand-built packet cannot turn a stamina cost into a
			// refund by way of an absurd per-point figure.
			StatTuning.set(payload.settings());
			StatConfigFile.save(StatTuning.current());

			// Every client's character sheet describes these effects, so all of them
			// have to be told, not just the admin who made the change.
			MinecraftServer server = player.level().getServer();

			if (server != null) {
				StatConfigPayload update = new StatConfigPayload(StatTuning.current(), false);

				for (ServerPlayer online : server.getPlayerList().getPlayers()) {
					ServerPlayNetworking.send(online, update);
				}
			}

			GrandCraft.LOGGER.info("{} updated stat settings", player.getGameProfile().name());
			player.sendSystemMessage(Component.translatable("commands.grandcraft.config.saved"));
		});
	}

	private static void registerLevelConfig() {
		ServerPlayNetworking.registerGlobalReceiver(ApplyLevelConfigPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();

			// As with the other three: the command's permission guards the command, not
			// this packet, which any connected client can send at any time.
			if (!Commands.LEVEL_GAMEMASTERS.check(player.permissions())) {
				GrandCraft.LOGGER.warn("{} tried to change level settings without permission",
						player.getGameProfile().name());
				return;
			}

			// set() clamps, so a hand-built packet cannot drive a level's cost to zero
			// and hang the level-up loop that spends it.
			LevelTuning.set(payload.settings());
			LevelConfigFile.save(LevelTuning.current());

			// Every client's character sheet draws the cost curve, so all of them have
			// to be told, not just the admin who made the change.
			MinecraftServer server = player.level().getServer();

			if (server != null) {
				LevelConfigPayload update = new LevelConfigPayload(LevelTuning.current(), false);

				for (ServerPlayer online : server.getPlayerList().getPlayers()) {
					ServerPlayNetworking.send(online, update);
				}
			}

			GrandCraft.LOGGER.info("{} updated level settings", player.getGameProfile().name());
			player.sendSystemMessage(Component.translatable("commands.grandcraft.config.saved"));
		});
	}
}

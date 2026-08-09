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
import com.hrtq.grandcraft.skill.SkillConfigFile;
import com.hrtq.grandcraft.skill.SkillLoadouts;
import com.hrtq.grandcraft.skill.SkillNode;
import com.hrtq.grandcraft.skill.SkillTuning;
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
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
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
				.register(AirDashPayload.TYPE, AirDashPayload.STREAM_CODEC);
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
		PayloadTypeRegistry.serverboundPlay()
				.register(ToggleSkillPayload.TYPE, ToggleSkillPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay()
				.register(UseSkillPayload.TYPE, UseSkillPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay()
				.register(ApplySkillConfigPayload.TYPE, ApplySkillConfigPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay()
				.register(OpenSkillConfigPayload.TYPE, OpenSkillConfigPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay()
				.register(CombatMasterPayload.TYPE, CombatMasterPayload.STREAM_CODEC);

		registerClassSelection();
		registerStatSpending();
		registerPoolSpending();
		registerSkillEquipping();
		registerSkillUse();
		registerCombatConfig();
		registerGameConfig();
		registerStatConfig();
		registerLevelConfig();
		registerWeaponConfig();
		registerSkillConfig();

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

	/**
	 * Sent only to the admin who asked, unlike the level and stat ones.
	 *
	 * <p>The skill settings are server-held: nothing on a client reads them, because the
	 * Combat Master badge is told how many ticks remain rather than how long a window
	 * is. Same shape as the combat config for the same reason.
	 */
	public static void sendSkillConfig(ServerPlayer player) {
		ServerPlayNetworking.send(player, new OpenSkillConfigPayload(SkillTuning.current()));
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
	 * Equips or unequips the node a player clicked on the character sheet.
	 *
	 * <p>Every rule lives in {@code SkillLoadouts.toggle} — this only carries the answer
	 * back. A refusal is told to the player rather than dropped silently: unlike a stat
	 * point, the click has a visible target that simply would not change, and "the sheet
	 * ignores me" is the report that would follow.
	 *
	 * <p>Above the action bar rather than in chat, because it is a response to something
	 * the player is looking at and does not belong in a log they scroll back through.
	 */
	private static void registerSkillEquipping() {
		ServerPlayNetworking.registerGlobalReceiver(ToggleSkillPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			SkillLoadouts.Refusal refusal = SkillLoadouts.toggle(player, payload.path());

			if (refusal == null) {
				return;
			}

			if (refusal == SkillLoadouts.Refusal.NO_SUCH_NODE) {
				// Not reachable from the sheet, which only ever offers this character's own
				// nodes — so it means a hand-built packet, and is worth a line in the log
				// rather than a message to whoever sent it.
				GrandCraft.LOGGER.warn("{} tried to equip an unknown skill node: {}",
						player.getGameProfile().name(), payload.path());
				return;
			}

			actionBar(player, Component.translatable(refusal == SkillLoadouts.Refusal.LOCKED
					? "screen.grandcraft.sheet.equip.locked"
					: "screen.grandcraft.sheet.equip.full"));
		});
	}

	/**
	 * Fires whatever is on one of the four ability keys.
	 *
	 * <p><strong>Nothing is bound to a node yet</strong>, so this proves the loop and
	 * says so: key down, packet, the server resolving its own record of what is equipped,
	 * and something the player can perceive. When abilities exist, this method is where
	 * the name is turned into an effect, and everything either side of it is already
	 * right.
	 *
	 * <p>The slot is re-checked against the loadout rather than trusted, and the node is
	 * re-checked as unlocked — an ability equipped before an admin raised its gate must
	 * stop working, not keep firing because it was equipped when the rule was looser.
	 */
	private static void registerSkillUse() {
		ServerPlayNetworking.registerGlobalReceiver(UseSkillPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();

			if (!payload.isValidSlot()) {
				GrandCraft.LOGGER.warn("{} sent an out-of-range skill slot: {}",
						player.getGameProfile().name(), payload.slot());
				return;
			}

			SkillNode node = SkillLoadouts.equipped(player, payload.slot());

			// An empty key is silent. It is the normal state of three of the four for most
			// of a character's life, and a refusal noise on every stray keypress would be
			// the loudest thing in the game.
			if (node == null || !SkillLoadouts.isUnlocked(player, node)) {
				return;
			}

			// The placeholder, and deliberately a cheap one: a sound so the press is felt
			// and a line naming what fired. No cooldown — a cooldown is a number, and
			// there is nothing here yet to tune one against.
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6F, 1.4F);

			actionBar(player, Component.translatable(
					"screen.grandcraft.sheet.skill_fired", payload.slot() + 1));
		});
	}

	/**
	 * A line above the hotbar, gone in a couple of seconds.
	 *
	 * <p>{@code Player.displayClientMessage} is gone in 26.2 — there is no
	 * {@code (Component, boolean)} shortcut any more, and {@code sendSystemMessage}
	 * goes to chat, which is the wrong place for a response to something the player is
	 * looking at. The packet is what is left, and it is the same route
	 * {@code EssenceAwards} already takes to put a level-up on screen.
	 */
	private static void actionBar(ServerPlayer player, Component text) {
		player.connection.send(new ClientboundSetActionBarTextPacket(text));
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

	/**
	 * Applies edited skill settings.
	 *
	 * <p>Nothing is broadcast afterwards, which is what makes this shorter than the
	 * level receiver: no client draws these numbers, so there is nobody to tell. A
	 * window already running keeps the length it was granted with — it was resolved
	 * when it opened — and the next one uses the new figures.
	 */
	private static void registerSkillConfig() {
		ServerPlayNetworking.registerGlobalReceiver(ApplySkillConfigPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();

			// As with the other four: the command's permission guards the command, not
			// this packet, which any connected client can send at any time.
			if (!Commands.LEVEL_GAMEMASTERS.check(player.permissions())) {
				GrandCraft.LOGGER.warn("{} tried to change skill settings without permission",
						player.getGameProfile().name());
				return;
			}

			// set() clamps, so a hand-built packet cannot grant a thousandfold blow or a
			// window that never closes.
			SkillTuning.set(payload.settings());
			SkillConfigFile.save(SkillTuning.current());
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

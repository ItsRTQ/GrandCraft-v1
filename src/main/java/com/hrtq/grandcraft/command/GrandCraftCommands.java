package com.hrtq.grandcraft.command;

import com.hrtq.grandcraft.entity.GrandCraftEntities;
import com.hrtq.grandcraft.network.GrandCraftNetworking;
import com.hrtq.grandcraft.player.GrandCraftAttachments;
import com.hrtq.grandcraft.player.PlayerClass;
import com.hrtq.grandcraft.progression.EssenceAwards;
import com.hrtq.grandcraft.stats.PlayerStats;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

public final class GrandCraftCommands {
	private GrandCraftCommands() {
	}

	/**
	 * Completes against the mob names GrandCraft registers, rather than every entity
	 * in the game — the point of a separate command is that it only offers ours.
	 */
	private static final SuggestionProvider<CommandSourceStack> SUMMONABLE_MOBS =
			(context, builder) -> SharedSuggestionProvider.suggest(
					GrandCraftEntities.summonableNames(), builder);

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(Commands.literal("grandcraft")
						.then(Commands.literal("summon")
								.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
								.then(Commands.argument("mob", StringArgumentType.word())
										.suggests(SUMMONABLE_MOBS)
										.executes(context -> summon(context.getSource(),
												StringArgumentType.getString(context, "mob")))))
						.then(Commands.literal("reclass")
								.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
								.then(Commands.argument("player", EntityArgument.player())
										.executes(context -> {
											ServerPlayer target = EntityArgument.getPlayer(context, "player");
											target.setAttached(GrandCraftAttachments.PLAYER_CLASS, PlayerClass.PEASANT);

											// A reclass unmakes the character, so their progression goes
											// with it: level, banked Essence, unspent points and every
											// point already committed. Leaving the spent points behind
											// would hand a fresh peasant the stats of the character they
											// used to be, bought with a class they no longer have.
											EssenceAwards.reset(target);

											// Stats come from the class and the spent points, so this has
											// to run after both have been cleared — one pass, covering
											// both halves of what just changed.
											PlayerStats.applyBaselines(target);
											context.getSource().sendSuccess(() -> Component.translatable(
													"commands.grandcraft.reclass.success", target.getDisplayName()), true);
											return 1;
										})))
						// A category rather than a command, so later config screens are
						// siblings of "combat" instead of needing their own top-level verb.
						.then(Commands.literal("config")
								.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
								// "game" first: it holds the settings that apply mod-wide, of
								// which combat is one area among several to come.
								.then(Commands.literal("game")
										.executes(context -> {
											ServerPlayer player = context.getSource().getPlayerOrException();
											GrandCraftNetworking.sendGameConfig(player);
											return 1;
										}))
								.then(Commands.literal("combat")
										.executes(context -> {
											// Needs a real player: the screen opens on their client, so a
											// console or command block invocation has nowhere to send it.
											ServerPlayer player = context.getSource().getPlayerOrException();
											GrandCraftNetworking.sendCombatConfig(player);
											return 1;
										}))
								.then(Commands.literal("stats")
										.executes(context -> {
											ServerPlayer player = context.getSource().getPlayerOrException();
											GrandCraftNetworking.sendStatConfig(player);
											return 1;
										}))
								.then(Commands.literal("levels")
										.executes(context -> {
											ServerPlayer player = context.getSource().getPlayerOrException();
											GrandCraftNetworking.sendLevelConfig(player);
											return 1;
										})))));
	}

	/**
	 * Spawns one of the mod's mobs where the command was run.
	 *
	 * <p>Deliberately narrower than vanilla's {@code /summon}: no position, no NBT.
	 * This exists so GrandCraft's own mobs can be reached by name while they have no
	 * spawn eggs and no natural spawning, not to reimplement a command that already
	 * works.
	 */
	private static int summon(CommandSourceStack source, String name) throws CommandSyntaxException {
		EntityType<?> type = GrandCraftEntities.summonable(name);

		if (type == null) {
			// A hard failure rather than a silent no-op: a typo that quietly spawns
			// nothing is indistinguishable from a mob that failed to render.
			throw UNKNOWN_MOB.create(name);
		}

		ServerLevel level = source.getLevel();
		Vec3 position = source.getPosition();
		Entity spawned = type.spawn(level, BlockPos.containing(position), EntitySpawnReason.COMMAND);

		if (spawned == null) {
			throw SUMMON_FAILED.create(name);
		}

		source.sendSuccess(() -> Component.translatable(
				"commands.grandcraft.summon.success", spawned.getDisplayName()), true);
		return 1;
	}

	private static final DynamicCommandExceptionType UNKNOWN_MOB = new DynamicCommandExceptionType(
			name -> Component.translatable("commands.grandcraft.summon.unknown", name));

	private static final DynamicCommandExceptionType SUMMON_FAILED = new DynamicCommandExceptionType(
			name -> Component.translatable("commands.grandcraft.summon.failed", name));
}

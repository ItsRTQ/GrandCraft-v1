package com.hrtq.grandcraft.command;

import com.hrtq.grandcraft.network.GrandCraftNetworking;
import com.hrtq.grandcraft.player.GrandCraftAttachments;
import com.hrtq.grandcraft.player.PlayerClass;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class GrandCraftCommands {
	private GrandCraftCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(Commands.literal("grandcraft")
						.then(Commands.literal("reclass")
								.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
								.then(Commands.argument("player", EntityArgument.player())
										.executes(context -> {
											ServerPlayer target = EntityArgument.getPlayer(context, "player");
											target.setAttached(GrandCraftAttachments.PLAYER_CLASS, PlayerClass.PEASANT);
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
										})))));
	}
}

package com.hrtq.grandcraft;

import com.hrtq.grandcraft.combat.CombatConfigFile;
import com.hrtq.grandcraft.combat.GrandCraftCombat;
import com.hrtq.grandcraft.command.GrandCraftCommands;
import com.hrtq.grandcraft.network.GrandCraftNetworking;
import com.hrtq.grandcraft.player.GrandCraftAttachments;
import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GrandCraft implements ModInitializer {
	public static final String MOD_ID = "grandcraft";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		LOGGER.info("Hello Fabric world!");

		GrandCraftAttachments.register();
		GrandCraftNetworking.register();
		GrandCraftCommands.register();
		GrandCraftCombat.register();

		// After registration so the combat system is in place before values land.
		CombatConfigFile.load();
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}

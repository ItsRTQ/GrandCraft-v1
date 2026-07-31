package com.hrtq.grandcraft.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.hrtq.grandcraft.GrandCraft;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Reads and writes the general settings file, kept separate from the combat one so
 * the two can be edited and reasoned about independently.
 *
 * <p>Every failure is non-fatal and logged: a missing, unreadable or malformed file
 * falls back to {@link GameSettings#DEFAULT} rather than stopping the mod loading.
 */
public final class GameConfigFile {
	private static final String FILE_NAME = "grandcraft-game.json";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private GameConfigFile() {
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
	}

	public static void load() {
		Path path = path();

		if (!Files.exists(path)) {
			GameTuning.set(GameSettings.DEFAULT);
			return;
		}

		try {
			JsonElement json = JsonParser.parseString(Files.readString(path));
			GameTuning.set(GameSettings.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
			GrandCraft.LOGGER.info("Loaded game settings from {}", path);
		} catch (Exception exception) {
			GrandCraft.LOGGER.error("Could not read {}; using default game settings", path, exception);
			GameTuning.set(GameSettings.DEFAULT);
		}
	}

	public static void save(GameSettings settings) {
		Path path = path();

		try {
			JsonElement json = GameSettings.CODEC.encodeStart(JsonOps.INSTANCE, settings).getOrThrow();

			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(json));
		} catch (IOException | RuntimeException exception) {
			GrandCraft.LOGGER.error("Could not write game settings to {}", path, exception);
		}
	}
}

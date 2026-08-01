package com.hrtq.grandcraft.progression;

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
 * Reads and writes the level settings file, kept separate from the combat, game and
 * stat ones so the four can be edited and reasoned about independently.
 *
 * <p>Every failure is non-fatal and logged: a missing, unreadable or malformed file
 * falls back to {@link LevelSettings#DEFAULT} rather than stopping the mod loading.
 */
public final class LevelConfigFile {
	private static final String FILE_NAME = "grandcraft-levels.json";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private LevelConfigFile() {
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
	}

	public static void load() {
		Path path = path();

		if (!Files.exists(path)) {
			LevelTuning.set(LevelSettings.DEFAULT);
			return;
		}

		try {
			JsonElement json = JsonParser.parseString(Files.readString(path));
			LevelTuning.set(LevelSettings.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
			GrandCraft.LOGGER.info("Loaded level settings from {}", path);
		} catch (Exception exception) {
			GrandCraft.LOGGER.error("Could not read {}; using default level settings", path, exception);
			LevelTuning.set(LevelSettings.DEFAULT);
		}
	}

	public static void save(LevelSettings settings) {
		Path path = path();

		try {
			JsonElement json = LevelSettings.CODEC.encodeStart(JsonOps.INSTANCE, settings).getOrThrow();

			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(json));
		} catch (IOException | RuntimeException exception) {
			GrandCraft.LOGGER.error("Could not write level settings to {}", path, exception);
		}
	}
}

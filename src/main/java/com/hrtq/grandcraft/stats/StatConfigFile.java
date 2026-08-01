package com.hrtq.grandcraft.stats;

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
 * Reads and writes the stat settings file, kept separate from the combat and game
 * ones so the three can be edited and reasoned about independently.
 *
 * <p>Every failure is non-fatal and logged: a missing, unreadable or malformed file
 * falls back to {@link StatSettings#DEFAULT} rather than stopping the mod loading.
 */
public final class StatConfigFile {
	private static final String FILE_NAME = "grandcraft-stats.json";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private StatConfigFile() {
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
	}

	public static void load() {
		Path path = path();

		if (!Files.exists(path)) {
			StatTuning.set(StatSettings.DEFAULT);
			return;
		}

		try {
			JsonElement json = JsonParser.parseString(Files.readString(path));
			StatTuning.set(StatSettings.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
			GrandCraft.LOGGER.info("Loaded stat settings from {}", path);
		} catch (Exception exception) {
			GrandCraft.LOGGER.error("Could not read {}; using default stat settings", path, exception);
			StatTuning.set(StatSettings.DEFAULT);
		}
	}

	public static void save(StatSettings settings) {
		Path path = path();

		try {
			JsonElement json = StatSettings.CODEC.encodeStart(JsonOps.INSTANCE, settings).getOrThrow();

			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(json));
		} catch (IOException | RuntimeException exception) {
			GrandCraft.LOGGER.error("Could not write stat settings to {}", path, exception);
		}
	}
}

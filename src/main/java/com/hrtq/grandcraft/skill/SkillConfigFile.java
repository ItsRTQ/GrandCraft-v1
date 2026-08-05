package com.hrtq.grandcraft.skill;

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
 * Reads and writes the skill settings file, kept separate from the combat, game, stat
 * and level ones so the five can be edited and reasoned about independently.
 *
 * <p>Every failure is non-fatal and logged: a missing, unreadable or malformed file
 * falls back to {@link SkillSettings#DEFAULT} rather than stopping the mod loading.
 */
public final class SkillConfigFile {
	private static final String FILE_NAME = "grandcraft-skills.json";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private SkillConfigFile() {
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
	}

	public static void load() {
		Path path = path();

		if (!Files.exists(path)) {
			SkillTuning.set(SkillSettings.DEFAULT);
			return;
		}

		try {
			JsonElement json = JsonParser.parseString(Files.readString(path));
			SkillTuning.set(SkillSettings.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
			GrandCraft.LOGGER.info("Loaded skill settings from {}", path);
		} catch (Exception exception) {
			GrandCraft.LOGGER.error("Could not read {}; using default skill settings", path, exception);
			SkillTuning.set(SkillSettings.DEFAULT);
		}
	}

	public static void save(SkillSettings settings) {
		Path path = path();

		try {
			JsonElement json = SkillSettings.CODEC.encodeStart(JsonOps.INSTANCE, settings).getOrThrow();

			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(json));
		} catch (IOException | RuntimeException exception) {
			GrandCraft.LOGGER.error("Could not write skill settings to {}", path, exception);
		}
	}
}

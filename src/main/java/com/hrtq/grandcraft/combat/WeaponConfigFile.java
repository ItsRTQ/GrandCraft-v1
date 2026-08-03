package com.hrtq.grandcraft.combat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hrtq.grandcraft.GrandCraft;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Optional;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Reads and writes the weapon tuning file so values edited in game survive a restart.
 *
 * <p>Kept separate from the combat, game, stat and level files so the five can be
 * edited and reasoned about independently. Every failure is non-fatal and logged: a
 * missing, unreadable or malformed file falls back to {@link WeaponSettings#DEFAULT}
 * rather than stopping the mod from loading.
 */
public final class WeaponConfigFile {
	private static final String FILE_NAME = "grandcraft-weapons.json";

	/** The one top-level key that is not a {@link WeaponCategory} id. */
	private static final String RULES_KEY = "rules";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private WeaponConfigFile() {
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
	}

	/** Applies the stored settings to {@link WeaponTuning}, or the defaults if unavailable. */
	public static void load() {
		Path path = path();

		if (!Files.exists(path)) {
			WeaponTuning.set(WeaponSettings.DEFAULT);
			return;
		}

		try {
			JsonObject root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();

			// Clamped by set(), so an out-of-range hand edit is corrected rather than
			// allowed to throw later inside AttackProfile.
			WeaponTuning.set(decode(root));
			GrandCraft.LOGGER.info("Loaded weapon tuning from {}", path);
		} catch (Exception exception) {
			GrandCraft.LOGGER.error("Could not read {}; using default weapon tuning", path, exception);
			WeaponTuning.set(WeaponSettings.DEFAULT);
		}
	}

	/** Writes the given settings. Failure is logged and otherwise ignored. */
	public static void save(WeaponSettings settings) {
		Path path = path();

		try {
			JsonObject root = new JsonObject();

			for (WeaponCategory category : WeaponCategory.values()) {
				root.add(category.id(), category.settingsCodec()
						.encodeStart(JsonOps.INSTANCE, settings.forCategory(category)).getOrThrow());
			}

			// Not a category, so it cannot collide with one: every category key is a
			// WeaponCategory id and none of them is "rules".
			root.add(RULES_KEY, WeaponRules.codec(WeaponRules.DEFAULT)
					.encodeStart(JsonOps.INSTANCE, settings.rules()).getOrThrow());

			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(root));
		} catch (IOException | RuntimeException exception) {
			GrandCraft.LOGGER.error("Could not write weapon tuning to {}", path, exception);
		}
	}

	/**
	 * One object per category, keyed by {@link WeaponCategory#id()}.
	 *
	 * <p>Each category is decoded independently against its own codec, so one that is
	 * missing or unreadable falls back to its own defaults instead of discarding
	 * everyone else's values with it. A file written before a category existed
	 * therefore keeps working, and so does one written before a field was added.
	 */
	private static WeaponSettings decode(JsonObject root) {
		EnumMap<WeaponCategory, CategorySettings> byCategory = new EnumMap<>(WeaponCategory.class);

		for (WeaponCategory category : WeaponCategory.values()) {
			JsonElement element = root.get(category.id());

			if (element == null) {
				byCategory.put(category, category.defaults());
				continue;
			}

			Optional<CategorySettings> parsed =
					category.settingsCodec().parse(JsonOps.INSTANCE, element).result();

			if (parsed.isEmpty()) {
				GrandCraft.LOGGER.warn(
						"Could not read weapon tuning for {}; using its defaults", category.id());
			}

			byCategory.put(category, parsed.orElseGet(category::defaults));
		}

		return new WeaponSettings(byCategory, decodeRules(root.get(RULES_KEY)));
	}

	/** Decoded independently of the categories, for the same reason they are of each other. */
	private static WeaponRules decodeRules(JsonElement element) {
		if (element == null) {
			return WeaponRules.DEFAULT;
		}

		Optional<WeaponRules> parsed = WeaponRules.codec(WeaponRules.DEFAULT)
				.parse(JsonOps.INSTANCE, element).result();

		if (parsed.isEmpty()) {
			GrandCraft.LOGGER.warn("Could not read shared weapon rules; using defaults");
		}

		return parsed.orElse(WeaponRules.DEFAULT);
	}
}

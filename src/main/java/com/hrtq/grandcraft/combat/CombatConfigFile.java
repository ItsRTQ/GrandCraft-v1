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
 * Reads and writes the combat tuning file so values edited in game survive a restart.
 *
 * <p>Lives in the Fabric config directory as {@code grandcraft-combat.json}, and is
 * plain readable JSON so it can also be hand-edited or diffed.
 *
 * <p>Every failure is non-fatal and logged: a missing, unreadable or malformed file
 * falls back to {@link CombatSettings#DEFAULT} rather than stopping the mod from
 * loading. A file that survives being partly written would otherwise be a very
 * annoying way to break a world.
 */
public final class CombatConfigFile {
	private static final String FILE_NAME = "grandcraft-combat.json";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private CombatConfigFile() {
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
	}

	/** Applies the stored settings to {@link CombatTuning}, or the defaults if unavailable. */
	public static void load() {
		Path path = path();

		if (!Files.exists(path)) {
			CombatTuning.set(CombatSettings.DEFAULT);
			return;
		}

		try {
			JsonObject root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();

			// Clamped by set(), so an out-of-range hand edit is corrected rather
			// than allowed to throw later inside AttackProfile.
			CombatTuning.set(decode(root));
			GrandCraft.LOGGER.info("Loaded combat tuning from {}", path);
		} catch (Exception exception) {
			GrandCraft.LOGGER.error("Could not read {}; using default combat tuning", path, exception);
			CombatTuning.set(CombatSettings.DEFAULT);
		}
	}

	/** Writes the given settings. Failure is logged and otherwise ignored. */
	public static void save(CombatSettings settings) {
		Path path = path();

		try {
			JsonObject root = new JsonObject();

			for (CombatActor actor : CombatActor.values()) {
				root.add(actor.id(), actor.settingsCodec()
						.encodeStart(JsonOps.INSTANCE, settings.forActor(actor)).getOrThrow());
			}

			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(root));
		} catch (IOException | RuntimeException exception) {
			GrandCraft.LOGGER.error("Could not write combat tuning to {}", path, exception);
		}
	}

	/**
	 * One object per actor, keyed by {@link CombatActor#id()}.
	 *
	 * <p>Each actor is decoded independently against its own codec, so an actor that
	 * is missing or unreadable falls back to that actor's defaults instead of
	 * discarding everyone else's values with it. A file written before a mob existed
	 * therefore keeps working, and so does one written before a field was added.
	 */
	private static CombatSettings decode(JsonObject root) {
		EnumMap<CombatActor, ActorSettings> byActor = new EnumMap<>(CombatActor.class);

		for (CombatActor actor : CombatActor.values()) {
			JsonElement element = root.get(actor.id());

			if (element == null) {
				byActor.put(actor, actor.defaults());
				continue;
			}

			Optional<ActorSettings> parsed =
					actor.settingsCodec().parse(JsonOps.INSTANCE, element).result();

			if (parsed.isEmpty()) {
				GrandCraft.LOGGER.warn("Could not read combat tuning for {}; using its defaults", actor.id());
			}

			byActor.put(actor, parsed.orElseGet(actor::defaults));
		}

		return new CombatSettings(byActor);
	}
}

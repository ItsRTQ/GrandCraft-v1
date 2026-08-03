package com.hrtq.grandcraft.client.animation;

import com.hrtq.grandcraft.GrandCraft;
import java.io.BufferedReader;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Every authored clip for actors on the vanilla rig, reloaded with the resource packs.
 *
 * <p>Deliberately <em>not</em> under {@code assets/grandcraft/geckolib/}. GeckoLib
 * scans that folder and caches everything in it under its own naming scheme, and
 * these clips are none of its business — they target {@code PlayerModel}, which
 * GeckoLib does not draw. Keeping them in a plain {@code animations/} folder keeps
 * the two pipelines from ever meeting.
 *
 * <p>A file that fails to parse is logged and dropped rather than rethrown. A broken
 * clip should cost the actor its pose, not cost the player a resource reload that
 * refuses to finish.
 */
public final class GrandCraftAnimations {
	/** The player's four directional dodges, as exported from Blockbench. */
	public static final Identifier PLAYER_DODGE = GrandCraft.id("animations/player_dodge.json");

	private static Map<String, BedrockClip> clips = Map.of();

	private GrandCraftAnimations() {
	}

	/**
	 * Hooks the loader onto the client resource reload.
	 *
	 * <p>Safe to call from {@code ClientModInitializer}: the listener registry is only
	 * resolved later, at {@code CLIENT_STARTED}.
	 */
	public static void register() {
		ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(
				new SimpleSynchronousResourceReloadListener() {
					@Override
					public Identifier getFabricId() {
						return GrandCraft.id("animations");
					}

					@Override
					public void onResourceManagerReload(ResourceManager manager) {
						load(manager);
					}
				});
	}

	/**
	 * One clip by name, or {@link BedrockClip#EMPTY} if the pack does not have it.
	 *
	 * <p>Names are keyed as {@code <file path>#<clip name>} so two files can each hold
	 * a clip the animator called {@code right} without one silently winning.
	 */
	public static BedrockClip clip(Identifier file, String name) {
		return clips.getOrDefault(file + "#" + name, BedrockClip.EMPTY);
	}

	private static void load(ResourceManager manager) {
		Map<String, BedrockClip> loaded = new HashMap<>();

		read(manager, PLAYER_DODGE, loaded);

		clips = Map.copyOf(loaded);
	}

	private static void read(ResourceManager manager, Identifier file,
			Map<String, BedrockClip> into) {
		Resource resource = manager.getResource(file).orElse(null);

		if (resource == null) {
			GrandCraft.LOGGER.warn("No animation file at {}; actors using it will not be posed",
					file);
			return;
		}

		try (BufferedReader reader = resource.openAsReader()) {
			for (Map.Entry<String, BedrockClip> entry
					: BedrockClipLoader.parse(reader).entrySet()) {
				into.put(file + "#" + entry.getKey(), entry.getValue());
			}
		} catch (Exception failure) {
			GrandCraft.LOGGER.error("Could not read animation file {}", file, failure);
		}
	}
}

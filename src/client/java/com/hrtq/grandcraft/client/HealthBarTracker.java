package com.hrtq.grandcraft.client;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Notices when a visible entity loses health and remembers it long enough to draw
 * a bar.
 *
 * <p>Entirely client side and needs no packet of its own: health is already synced
 * for every entity the client can see, so a drop is detectable by simply comparing
 * against the previous tick.
 *
 * <p>The tracking map is rebuilt from the live entity list each tick rather than
 * pruned, which is what keeps it from growing without bound as entities die or
 * chunks unload.
 */
public final class HealthBarTracker {
	/** How long a bar stays up after the last hit. */
	private static final long SHOW_MILLIS = 2000L;

	private static final int SEGMENTS = 10;
	private static final String FILLED = "█";
	private static final String EMPTY = "░";

	private record Tracked(float health, long hiddenAtMillis) {
	}

	private static Map<Integer, Tracked> tracked = new HashMap<>();

	private HealthBarTracker() {
	}

	/** Called once per client tick with the level currently being played. */
	public static void tick(ClientLevel level, long nowMillis) {
		Map<Integer, Tracked> next = new HashMap<>();

		for (Entity entity : level.entitiesForRendering()) {
			if (!(entity instanceof LivingEntity living)) {
				continue;
			}

			Tracked previous = tracked.get(entity.getId());
			float health = living.getHealth();

			// First sighting only records a baseline. Showing a bar here would flash
			// one on every entity that came into view already wounded.
			long hiddenAt = previous == null ? 0L : previous.hiddenAtMillis();

			if (previous != null && health < previous.health()) {
				hiddenAt = nowMillis + SHOW_MILLIS;
			}

			next.put(entity.getId(), new Tracked(health, hiddenAt));
		}

		tracked = next;
	}

	public static boolean isShowing(LivingEntity entity, long nowMillis) {
		Tracked entry = tracked.get(entity.getId());
		return entry != null && nowMillis < entry.hiddenAtMillis();
	}

	/**
	 * The bar itself, as text.
	 *
	 * <p>Deliberately a {@link Component} rather than custom geometry: it is handed
	 * to the entity's name tag slot, which already handles billboarding, depth,
	 * scaling and distance fade. A textured bar would mean reimplementing all of
	 * that against 26.2's render-state pipeline for a cosmetic gain.
	 */
	public static Component bar(LivingEntity entity) {
		float max = entity.getMaxHealth();
		float fraction = max <= 0.0F ? 0.0F : Mth.clamp(entity.getHealth() / max, 0.0F, 1.0F);

		// Round down so a sliver of health never reads as a full segment, but keep
		// one segment while the entity is alive at all.
		int filled = (int) (fraction * SEGMENTS);

		if (filled == 0 && entity.getHealth() > 0.0F) {
			filled = 1;
		}

		ChatFormatting colour = fraction > 0.5F
				? ChatFormatting.GREEN
				: fraction > 0.25F ? ChatFormatting.YELLOW : ChatFormatting.RED;

		MutableComponent full = Component.literal(FILLED.repeat(filled)).withStyle(colour);

		if (filled >= SEGMENTS) {
			return full;
		}

		return full.append(Component.literal(EMPTY.repeat(SEGMENTS - filled))
				.withStyle(ChatFormatting.DARK_GRAY));
	}

	/** Dropped on disconnect so a new world does not inherit the old one's entity ids. */
	public static void clear() {
		tracked = new HashMap<>();
	}
}

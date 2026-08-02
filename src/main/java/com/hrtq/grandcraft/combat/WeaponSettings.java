package com.hrtq.grandcraft.combat;

import io.netty.buffer.ByteBuf;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.network.codec.StreamCodec;

/**
 * The tunable values for every {@link WeaponCategory}, as one immutable snapshot.
 *
 * <p>The weapon counterpart of {@code CombatSettings}, and deliberately the same
 * shape: immutable and swapped atomically, so a change can be detected by identity
 * rather than needing an invalidation callback.
 *
 * <p>The map is always total — the constructor fills in any category the caller left
 * out with that category's own defaults — so {@link #forCategory} never returns null
 * and a newly added category works before anyone has configured it.
 */
public record WeaponSettings(Map<WeaponCategory, CategorySettings> byCategory) {

	public WeaponSettings {
		EnumMap<WeaponCategory, CategorySettings> complete = new EnumMap<>(WeaponCategory.class);

		for (WeaponCategory category : WeaponCategory.values()) {
			CategorySettings settings = byCategory == null ? null : byCategory.get(category);
			complete.put(category, settings == null ? category.defaults() : settings);
		}

		byCategory = Collections.unmodifiableMap(complete);
	}

	/** Every category at its shipped starting values. Prototype tuning, not balance. */
	public static final WeaponSettings DEFAULT = new WeaponSettings(Map.of());

	// Iterates the enum rather than writing keys, so both sides read the same order
	// from the same jar. Adding a category extends the packet automatically.
	public static final StreamCodec<ByteBuf, WeaponSettings> STREAM_CODEC = StreamCodec.of(
			(buf, settings) -> {
				for (WeaponCategory category : WeaponCategory.values()) {
					CategorySettings.STREAM_CODEC.encode(buf, settings.forCategory(category));
				}
			},
			buf -> {
				EnumMap<WeaponCategory, CategorySettings> decoded =
						new EnumMap<>(WeaponCategory.class);

				for (WeaponCategory category : WeaponCategory.values()) {
					decoded.put(category, CategorySettings.STREAM_CODEC.decode(buf));
				}

				return new WeaponSettings(decoded);
			});

	public CategorySettings forCategory(WeaponCategory category) {
		return this.byCategory.get(category);
	}

	/** A copy with one category replaced. */
	public WeaponSettings with(WeaponCategory category, CategorySettings settings) {
		EnumMap<WeaponCategory, CategorySettings> copy = new EnumMap<>(this.byCategory);
		copy.put(category, settings);
		return new WeaponSettings(copy);
	}

	/**
	 * A copy with every category's values forced inside their bounds. Always applied
	 * to anything arriving over the network or read off disk.
	 */
	public WeaponSettings clamped() {
		EnumMap<WeaponCategory, CategorySettings> clamped = new EnumMap<>(WeaponCategory.class);

		for (WeaponCategory category : WeaponCategory.values()) {
			clamped.put(category, forCategory(category).clamped());
		}

		return new WeaponSettings(clamped);
	}
}

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
public record WeaponSettings(Map<WeaponCategory, CategorySettings> byCategory, WeaponRules rules) {

	public WeaponSettings {
		EnumMap<WeaponCategory, CategorySettings> complete = new EnumMap<>(WeaponCategory.class);

		for (WeaponCategory category : WeaponCategory.values()) {
			CategorySettings settings = byCategory == null ? null : byCategory.get(category);
			complete.put(category, settings == null ? category.defaults() : settings);
		}

		byCategory = Collections.unmodifiableMap(complete);

		// Same totalising bargain as the map above: a file or packet written before the
		// rules existed still produces a usable snapshot rather than a null field that
		// every reader would have to guard.
		if (rules == null) {
			rules = WeaponRules.DEFAULT;
		}
	}

	/** Categories only, for the callers that have no opinion about the shared rules. */
	public WeaponSettings(Map<WeaponCategory, CategorySettings> byCategory) {
		this(byCategory, WeaponRules.DEFAULT);
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

				WeaponRules.STREAM_CODEC.encode(buf, settings.rules());
			},
			buf -> {
				EnumMap<WeaponCategory, CategorySettings> decoded =
						new EnumMap<>(WeaponCategory.class);

				for (WeaponCategory category : WeaponCategory.values()) {
					decoded.put(category, CategorySettings.STREAM_CODEC.decode(buf));
				}

				return new WeaponSettings(decoded, WeaponRules.STREAM_CODEC.decode(buf));
			});

	public CategorySettings forCategory(WeaponCategory category) {
		return this.byCategory.get(category);
	}

	/** A copy with one category replaced. */
	public WeaponSettings with(WeaponCategory category, CategorySettings settings) {
		EnumMap<WeaponCategory, CategorySettings> copy = new EnumMap<>(this.byCategory);
		copy.put(category, settings);
		return new WeaponSettings(copy, this.rules);
	}

	/** A copy with the shared rules replaced. */
	public WeaponSettings with(WeaponRules replacement) {
		return new WeaponSettings(this.byCategory, replacement);
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

		return new WeaponSettings(clamped, this.rules.clamped());
	}
}

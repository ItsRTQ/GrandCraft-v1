package com.hrtq.grandcraft.combat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.RandomSource;

/**
 * An inclusive band a stat is rolled from when an actor first spawns, which is
 * what lets a mob type produce a spread of difficulties rather than clones.
 *
 * <p>A collapsed range ({@code min == max}) is a fixed value, which is how actors
 * that do not roll — the player — express their stats without needing a second
 * schema.
 */
public record StatRange(double min, double max) {

	public StatRange {
		// Tolerated rather than rejected: the config screen has two independent
		// sliders, so dragging min past max is an ordinary thing for a user to do
		// and should mean the obvious thing rather than throw.
		if (max < min) {
			double swap = min;
			min = max;
			max = swap;
		}
	}

	/** A fixed value, for actors that do not roll. */
	public static StatRange of(double value) {
		return new StatRange(value, value);
	}

	public double roll(RandomSource random) {
		if (this.min >= this.max) {
			return this.min;
		}

		return this.min + random.nextDouble() * (this.max - this.min);
	}

	public StatRange clamped(double lowest, double highest) {
		return new StatRange(clamp(this.min, lowest, highest), clamp(this.max, lowest, highest));
	}

	/** Field fallbacks come from the owning actor's defaults, as elsewhere. */
	public static Codec<StatRange> codec(StatRange fallback) {
		return RecordCodecBuilder.create(instance -> instance.group(
				Codec.DOUBLE.optionalFieldOf("min", fallback.min()).forGetter(StatRange::min),
				Codec.DOUBLE.optionalFieldOf("max", fallback.max()).forGetter(StatRange::max)
		).apply(instance, StatRange::new));
	}

	public static final StreamCodec<ByteBuf, StatRange> STREAM_CODEC = StreamCodec.of(
			(buf, range) -> {
				buf.writeDouble(range.min());
				buf.writeDouble(range.max());
			},
			buf -> new StatRange(buf.readDouble(), buf.readDouble()));

	private static double clamp(double value, double lowest, double highest) {
		// Rejects NaN too, which would otherwise survive a min/max pair.
		if (!Double.isFinite(value)) {
			return lowest;
		}

		return Math.max(lowest, Math.min(value, highest));
	}
}

package com.hrtq.grandcraft.combat;

import io.netty.buffer.ByteBuf;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.network.codec.StreamCodec;

/**
 * The tunable combat values for every {@link CombatActor}, as one immutable
 * snapshot.
 *
 * <p>Immutable and swapped atomically, which is what lets {@link CombatProfiles}
 * detect a change by identity instead of needing an invalidation callback.
 *
 * <p>The map is always total: the constructor fills in any actor the caller left
 * out with that actor's defaults, so {@link #forActor} never returns null and a
 * newly added mob works before anyone has configured it.
 */
public record CombatSettings(Map<CombatActor, ActorSettings> byActor) {

	public CombatSettings {
		EnumMap<CombatActor, ActorSettings> complete = new EnumMap<>(CombatActor.class);

		for (CombatActor actor : CombatActor.values()) {
			ActorSettings settings = byActor == null ? null : byActor.get(actor);
			complete.put(actor, settings == null ? actor.defaults() : settings);
		}

		byActor = Collections.unmodifiableMap(complete);
	}

	/**
	 * Every actor at its shipped starting values. Prototype tuning, not balance.
	 *
	 * <p>The 30 tick stagger reset is deliberate: vanilla invulnerability floors
	 * re-hits near 10 ticks and a sword swings every 12-13, so dropping this much
	 * below 15 makes the strong / weak / none sequence stop forming.
	 */
	public static final CombatSettings DEFAULT = new CombatSettings(Map.of());

	// Iterates the enum rather than writing keys, so both sides read the same order
	// from the same jar. Adding an actor extends the packet automatically.
	public static final StreamCodec<ByteBuf, CombatSettings> STREAM_CODEC = StreamCodec.of(
			(buf, settings) -> {
				for (CombatActor actor : CombatActor.values()) {
					ActorSettings.STREAM_CODEC.encode(buf, settings.forActor(actor));
				}
			},
			buf -> {
				EnumMap<CombatActor, ActorSettings> decoded = new EnumMap<>(CombatActor.class);

				for (CombatActor actor : CombatActor.values()) {
					decoded.put(actor, ActorSettings.STREAM_CODEC.decode(buf));
				}

				return new CombatSettings(decoded);
			});

	public ActorSettings forActor(CombatActor actor) {
		return this.byActor.get(actor);
	}

	/** A copy with one actor replaced. */
	public CombatSettings with(CombatActor actor, ActorSettings settings) {
		EnumMap<CombatActor, ActorSettings> copy = new EnumMap<>(this.byActor);
		copy.put(actor, settings);
		return new CombatSettings(copy);
	}

	/**
	 * A copy with every actor's values forced inside their bounds. Always applied to
	 * anything arriving over the network or read off disk.
	 */
	public CombatSettings clamped() {
		EnumMap<CombatActor, ActorSettings> clamped = new EnumMap<>(CombatActor.class);

		for (CombatActor actor : CombatActor.values()) {
			clamped.put(actor, forActor(actor).clamped());
		}

		return new CombatSettings(clamped);
	}
}

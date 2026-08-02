package com.hrtq.grandcraft.stats;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * One character's mana as it is saved.
 *
 * <p>Deliberately holds only what cannot be derived. The ceiling is <em>not</em>
 * here: it comes from the settings plus the pool points on {@code EssenceProgress},
 * both of which are already persisted, so storing it too would be a second source of
 * truth that could disagree with the first after a reclass or a config change.
 *
 * <p>The regen delay is stored because it is genuinely transient state that a spend
 * created — logging out mid-delay and back in with it cleared would be a small free
 * refill, which is the whole class of thing this record exists to stop.
 */
public record ManaState(float current, int regenDelayTicks) {

	public static final Codec<ManaState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.FLOAT.optionalFieldOf("current", 0.0F).forGetter(ManaState::current),
			Codec.INT.optionalFieldOf("regen_delay_ticks", 0).forGetter(ManaState::regenDelayTicks)
	).apply(instance, ManaState::new));
}

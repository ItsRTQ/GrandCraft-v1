package com.hrtq.grandcraft.combat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * One actor's stamina budget: how big the pool is, how fast it comes back, and
 * what each action costs.
 *
 * <p>Stamina is the resource that makes every option cost something. An actor that
 * runs it down has to stop and recover, which is a readable opening rather than a
 * difficulty number — and it applies to mobs exactly as it does to the player.
 *
 * <p>All six values are whole numbers so the config fields stay integer-typed and
 * no locale-dependent decimal separator is introduced. Rates are per second and
 * divided down to per tick where they are spent, so the numbers here read the way
 * someone balancing them would say them out loud.
 */
public record StaminaSettings(
		int maxStamina,
		int regenPerSecond,
		int regenDelayTicks,
		int attackCost,
		int sprintCostPerSecond,
		int jumpCost) {

	/**
	 * Bounds shared by the config fields and the server-side clamp. The pool ceiling
	 * is generous because stamina is a pure GrandCraft quantity — unlike health or
	 * armour there is no vanilla attribute underneath it to clip the value silently.
	 */
	public static final int MAX_POOL = 1000;
	public static final int MAX_RATE = 200;
	public static final int MAX_DELAY_TICKS = 100;
	public static final int MAX_COST = 1000;

	/**
	 * Whether stamina actually applies. A pool of zero is the config-level off
	 * switch: an actor that has {@link CombatVerb#STAMINA} at compile time can still
	 * be returned to unlimited actions by an admin without a rebuild.
	 *
	 * <p>Checked through {@link CombatProfile#usesStamina()}, which combines it with
	 * the verb so no caller has to remember both halves.
	 */
	public boolean enabled() {
		return this.maxStamina > 0;
	}

	/** Rates are configured per second and spent per tick. */
	public float regenPerTick() {
		return this.regenPerSecond / (float) TICKS_PER_SECOND;
	}

	public float sprintCostPerTick() {
		return this.sprintCostPerSecond / (float) TICKS_PER_SECOND;
	}

	private static final int TICKS_PER_SECOND = 20;

	public static Codec<StaminaSettings> codec(StaminaSettings fallback) {
		return RecordCodecBuilder.create(instance -> instance.group(
				Codec.INT.optionalFieldOf("max_stamina", fallback.maxStamina())
						.forGetter(StaminaSettings::maxStamina),
				Codec.INT.optionalFieldOf("regen_per_second", fallback.regenPerSecond())
						.forGetter(StaminaSettings::regenPerSecond),
				Codec.INT.optionalFieldOf("regen_delay_ticks", fallback.regenDelayTicks())
						.forGetter(StaminaSettings::regenDelayTicks),
				Codec.INT.optionalFieldOf("attack_cost", fallback.attackCost())
						.forGetter(StaminaSettings::attackCost),
				Codec.INT.optionalFieldOf("sprint_cost_per_second", fallback.sprintCostPerSecond())
						.forGetter(StaminaSettings::sprintCostPerSecond),
				Codec.INT.optionalFieldOf("jump_cost", fallback.jumpCost())
						.forGetter(StaminaSettings::jumpCost)
		).apply(instance, StaminaSettings::new));
	}

	public static final StreamCodec<ByteBuf, StaminaSettings> STREAM_CODEC = StreamCodec.of(
			(buf, settings) -> {
				buf.writeInt(settings.maxStamina());
				buf.writeInt(settings.regenPerSecond());
				buf.writeInt(settings.regenDelayTicks());
				buf.writeInt(settings.attackCost());
				buf.writeInt(settings.sprintCostPerSecond());
				buf.writeInt(settings.jumpCost());
			},
			// Java evaluates arguments left to right, so the read order is well defined
			// and matches the writes above.
			buf -> new StaminaSettings(
					buf.readInt(), buf.readInt(), buf.readInt(),
					buf.readInt(), buf.readInt(), buf.readInt()));

	/** A copy with every value forced inside its bounds. */
	public StaminaSettings clamped() {
		return new StaminaSettings(
				clamp(this.maxStamina, 0, MAX_POOL),
				clamp(this.regenPerSecond, 0, MAX_RATE),
				clamp(this.regenDelayTicks, 0, MAX_DELAY_TICKS),
				clamp(this.attackCost, 0, MAX_COST),
				clamp(this.sprintCostPerSecond, 0, MAX_RATE),
				clamp(this.jumpCost, 0, MAX_COST));
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(value, max));
	}
}

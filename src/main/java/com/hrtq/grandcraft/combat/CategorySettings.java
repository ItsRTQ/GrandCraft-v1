package com.hrtq.grandcraft.combat;

import com.hrtq.grandcraft.stats.StatWeights;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * How GrandCraft treats one class of weapon.
 *
 * <p>The per-category half of {@link WeaponSettings}, exactly as
 * {@code ActorSettings} is the per-actor half of {@code CombatSettings}.
 *
 * <h2>What is deliberately absent</h2>
 * Damage, attack speed, reach and durability are <em>not</em> here. Those are
 * properties of an individual weapon and live on the item as vanilla data
 * components, where enchantments, smithing and vanilla's own tooltip can all reach
 * them. What is here is the ruleset's judgement about a class of weapon — a
 * judgement that applies identically to the mod's own claymore, to a vanilla iron
 * sword, and to a sword from a mod this one has never heard of.
 *
 * <h2>Why the scaling weights are here and the requirement is not</h2>
 * {@link #weights} passes that same test and is the reason it sits here rather than on
 * the item: "heavy weapons are swung with Strength" is a statement about the whole
 * class, and it has to hold for a greatsword from a mod nobody has patched. A
 * <em>requirement</em> does not pass it — a claymore demanding 14 Strength is a fact
 * about that claymore — so it lives on the item as a component and is derived from the
 * weapon's damage for anything that carries none.
 *
 * <p>The Arcane damage-per-Arcane-point figure is also absent, and belongs in
 * {@code StatSettings} beside the other per-stat rates: it prices a stat point, not
 * a weapon. Keeping them apart is what stops two config screens becoming two places
 * to look for the same kind of number.
 *
 * <h2>Startup and the hit window drive the player's swing</h2>
 * {@link #startupTicks} and {@link #activeTicks} were stored, persisted and clamped for
 * weeks while <em>nothing read them</em> — the player had no attack pose, and a wind-up
 * nobody can see is indistinguishable from lag, so its swing went straight to endlag
 * through a since-deleted shortcut on the controller. Holding them here anyway is what
 * made the slice that finally used them (2026-08-05, when the animator delivered the
 * pose) a substitution rather than a schema change: no config file migrated, no codec
 * moved, and every world that had been tuning these numbers blind kept them.
 *
 * <p>They are on {@code /grandcraft config weapons} now, having been hidden for exactly
 * as long as they did nothing.
 */
public record CategorySettings(
		int startupTicks,
		int activeTicks,
		int recoveryTicks,
		int staminaCost,
		StatWeights weights,
		ArcaneSettings arcane) {

	/** Bounds shared by the config fields and the server-side clamp. */
	public static final int MAX_PHASE_TICKS = 40;
	public static final int MIN_ACTIVE_TICKS = 1;
	public static final int MAX_COST = 1000;

	public static Codec<CategorySettings> codec(CategorySettings fallback) {
		return RecordCodecBuilder.create(instance -> instance.group(
				Codec.INT.optionalFieldOf("startup_ticks", fallback.startupTicks())
						.forGetter(CategorySettings::startupTicks),
				Codec.INT.optionalFieldOf("active_ticks", fallback.activeTicks())
						.forGetter(CategorySettings::activeTicks),
				Codec.INT.optionalFieldOf("recovery_ticks", fallback.recoveryTicks())
						.forGetter(CategorySettings::recoveryTicks),
				Codec.INT.optionalFieldOf("stamina_cost", fallback.staminaCost())
						.forGetter(CategorySettings::staminaCost),
				StatWeights.codec(fallback.weights()).optionalFieldOf("weights", fallback.weights())
						.forGetter(CategorySettings::weights),
				ArcaneSettings.codec(fallback.arcane()).optionalFieldOf("arcane", fallback.arcane())
						.forGetter(CategorySettings::arcane)
		).apply(instance, CategorySettings::new));
	}

	public static final StreamCodec<ByteBuf, CategorySettings> STREAM_CODEC = StreamCodec.of(
			(buf, settings) -> {
				buf.writeInt(settings.startupTicks());
				buf.writeInt(settings.activeTicks());
				buf.writeInt(settings.recoveryTicks());
				buf.writeInt(settings.staminaCost());
				StatWeights.STREAM_CODEC.encode(buf, settings.weights());
				ArcaneSettings.STREAM_CODEC.encode(buf, settings.arcane());
			},
			// Java evaluates arguments left to right, so the read order is well defined
			// and matches the writes above.
			buf -> new CategorySettings(
					buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(),
					StatWeights.STREAM_CODEC.decode(buf),
					ArcaneSettings.STREAM_CODEC.decode(buf)));

	/**
	 * A copy with every value forced inside its bounds.
	 *
	 * <p>The floor of one on the hit window is not a preference: a zero-tick window
	 * can never connect, and {@code AttackProfile} throws rather than accept it.
	 */
	public CategorySettings clamped() {
		return new CategorySettings(
				clamp(this.startupTicks, 0, MAX_PHASE_TICKS),
				clamp(this.activeTicks, MIN_ACTIVE_TICKS, MAX_PHASE_TICKS),
				clamp(this.recoveryTicks, 0, MAX_PHASE_TICKS),
				clamp(this.staminaCost, 0, MAX_COST),
				this.weights().clamped(),
				this.arcane().clamped());
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(value, max));
	}
}

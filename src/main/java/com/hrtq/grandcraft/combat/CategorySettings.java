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
 * <h2>The hit window is an absolute; the two ends of the swing are modifiers</h2>
 * {@link #activeTicks} is a length: how long this class of weapon can connect for, which
 * only means anything against that weapon's own reach. The wind-up and the endlag are
 * <strong>not</strong> lengths here — since 2026-08-07 they are the actor's globals, one
 * rhythm the player can learn — so what this record holds is a <em>signed offset</em>
 * from them: {@link #startupModifier} and {@link #recoveryModifier}. A dagger quicker
 * than the rhythm, a greatsword slower, and a category that says nothing leaves it alone.
 *
 * <p>They meet the global in {@code Weapons.startupFor} / {@code recoveryFor} and nowhere
 * else, and are clamped <em>there</em> rather than here: a modifier of -20 is a legal
 * thing to store, and only the sum has to be a legal phase length.
 *
 * <h2>Why the keys were renamed rather than reinterpreted (2026-08-07)</h2>
 * These two fields spent weeks as absolute tick counts that nothing read, and worlds were
 * tuning them blind — {@code run/config/grandcraft-weapons.json} still held
 * {@code heavy: recovery_ticks 40}. Read as a modifier that is a 43-tick claymore endlag:
 * a tuning failure that would present as "blocking after a swing is broken", which is
 * precisely the shape of bug {@code tuning.md} exists to record. Renaming the JSON keys
 * to {@code startup_modifier} / {@code endlag_modifier} makes the stale absolutes fall
 * out of every existing config by construction — every field here is
 * {@code optionalFieldOf}, so an unrecognised key is simply ignored and the new default
 * applies. No migration code, and no chance of a number changing meaning underneath a
 * world that was tuning it.
 */
public record CategorySettings(
		int startupModifier,
		int activeTicks,
		int recoveryModifier,
		int staminaCost,
		StatWeights weights,
		ArcaneSettings arcane) {

	/** Bounds shared by the config fields and the server-side clamp. */
	public static final int MAX_PHASE_TICKS = 40;
	public static final int MIN_ACTIVE_TICKS = 1;
	public static final int MAX_COST = 1000;

	/**
	 * How far a category may pull the actor's wind-up or endlag, in either direction.
	 *
	 * <p>Symmetric around zero because the point of a modifier is that it goes both ways.
	 * The bound is the phase maximum, so a modifier can always reach any legal phase
	 * length from any global — and can never express more than one.
	 */
	public static final int MAX_MODIFIER_TICKS = MAX_PHASE_TICKS;

	public static Codec<CategorySettings> codec(CategorySettings fallback) {
		return RecordCodecBuilder.create(instance -> instance.group(
				Codec.INT.optionalFieldOf("startup_modifier", fallback.startupModifier())
						.forGetter(CategorySettings::startupModifier),
				Codec.INT.optionalFieldOf("active_ticks", fallback.activeTicks())
						.forGetter(CategorySettings::activeTicks),
				Codec.INT.optionalFieldOf("endlag_modifier", fallback.recoveryModifier())
						.forGetter(CategorySettings::recoveryModifier),
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
				buf.writeInt(settings.startupModifier());
				buf.writeInt(settings.activeTicks());
				buf.writeInt(settings.recoveryModifier());
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
	 *
	 * <p><strong>The two modifiers clamp symmetrically and are allowed to be
	 * negative</strong> — that is the whole point of them. What has to end up a legal
	 * phase length is the global plus the modifier, and that sum is clamped where the
	 * two meet, in {@code Weapons.startupFor} / {@code recoveryFor}.
	 */
	public CategorySettings clamped() {
		return new CategorySettings(
				clamp(this.startupModifier, -MAX_MODIFIER_TICKS, MAX_MODIFIER_TICKS),
				clamp(this.activeTicks, MIN_ACTIVE_TICKS, MAX_PHASE_TICKS),
				clamp(this.recoveryModifier, -MAX_MODIFIER_TICKS, MAX_MODIFIER_TICKS),
				clamp(this.staminaCost, 0, MAX_COST),
				this.weights().clamped(),
				this.arcane().clamped());
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(value, max));
	}
}

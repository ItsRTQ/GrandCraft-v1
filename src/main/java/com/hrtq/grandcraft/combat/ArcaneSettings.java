package com.hrtq.grandcraft.combat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * What an arcane implement throws when it has no spell to cast.
 *
 * <p>A nested group inside {@link CategorySettings}, exactly as
 * {@link StaminaSettings} is nested inside {@code ActorSettings}: it is only
 * meaningful for one category, and the config screen shows these rows only on that
 * category's tab. Every other category carries a zeroed copy, so the settings tell
 * the truth about them rather than hiding a field that exists.
 *
 * <p>Whole numbers throughout, or hundredths where a fraction is needed — the same
 * rule the rest of the config follows, so the fields stay integer-typed and no
 * locale-dependent decimal separator is introduced.
 *
 * <p>The gust is deliberately not a kill button. It is the attack a caster has when
 * they have nothing else, and it being modest is precisely why real spells will be
 * worth learning.
 *
 * <h2>The fields</h2>
 * {@code manaCost} is whole mana points. {@code baseDamage} is hundredths of a
 * damage point before the Arcane multiplier, so 200 is 2.00. {@code projectileSpeed}
 * and {@code knockback} are both hundredths of a block per tick.
 *
 * <p>{@code cooldownTicks} is load-bearing at both ends. It must stay comfortably
 * above the four-tick repeat that a held right-click produces on an item the guard
 * does not claim, or the implement machine-guns; and it must stay below the
 * implement's own melee cooldown, or casting is strictly worse than hitting someone
 * with the stick.
 *
 * <p>{@code rangeTicks} is not cosmetic either. The gust has no gravity, so one that
 * hits nothing never lands and never despawns on its own — without this it is an
 * entity leak of one per missed cast, which presents as lag rather than as a leak.
 */
public record ArcaneSettings(
		int manaCost,
		int baseDamage,
		int projectileSpeed,
		int knockback,
		int cooldownTicks,
		int rangeTicks) {

	/** Bounds shared by the config fields and the server-side clamp. */
	public static final int MAX_COST = 1000;
	public static final int MAX_DAMAGE = 10000;
	public static final int MAX_SPEED = 1000;
	public static final int MAX_KNOCKBACK = 500;
	public static final int MAX_COOLDOWN_TICKS = 200;
	public static final int MAX_RANGE_TICKS = 200;

	/** Everything off. What a category that does not cast carries. */
	public static final ArcaneSettings NONE = new ArcaneSettings(0, 0, 0, 0, 0, 0);

	/**
	 * Whether casting actually applies. A base damage of zero is the config-level off
	 * switch, matching the way a zero pool switches stamina off.
	 */
	public boolean enabled() {
		return this.baseDamage > 0;
	}

	/** Hundredths are stored; damage is dealt in whole points. */
	public float damage() {
		return this.baseDamage / 100.0F;
	}

	public float speed() {
		return this.projectileSpeed / 100.0F;
	}

	public float knockbackStrength() {
		return this.knockback / 100.0F;
	}

	public static Codec<ArcaneSettings> codec(ArcaneSettings fallback) {
		return RecordCodecBuilder.create(instance -> instance.group(
				Codec.INT.optionalFieldOf("mana_cost", fallback.manaCost())
						.forGetter(ArcaneSettings::manaCost),
				Codec.INT.optionalFieldOf("base_damage", fallback.baseDamage())
						.forGetter(ArcaneSettings::baseDamage),
				Codec.INT.optionalFieldOf("projectile_speed", fallback.projectileSpeed())
						.forGetter(ArcaneSettings::projectileSpeed),
				Codec.INT.optionalFieldOf("knockback", fallback.knockback())
						.forGetter(ArcaneSettings::knockback),
				Codec.INT.optionalFieldOf("cooldown_ticks", fallback.cooldownTicks())
						.forGetter(ArcaneSettings::cooldownTicks),
				Codec.INT.optionalFieldOf("range_ticks", fallback.rangeTicks())
						.forGetter(ArcaneSettings::rangeTicks)
		).apply(instance, ArcaneSettings::new));
	}

	public static final StreamCodec<ByteBuf, ArcaneSettings> STREAM_CODEC = StreamCodec.of(
			(buf, settings) -> {
				buf.writeInt(settings.manaCost());
				buf.writeInt(settings.baseDamage());
				buf.writeInt(settings.projectileSpeed());
				buf.writeInt(settings.knockback());
				buf.writeInt(settings.cooldownTicks());
				buf.writeInt(settings.rangeTicks());
			},
			// Java evaluates arguments left to right, so the read order is well defined
			// and matches the writes above.
			buf -> new ArcaneSettings(
					buf.readInt(), buf.readInt(), buf.readInt(),
					buf.readInt(), buf.readInt(), buf.readInt()));

	/** A copy with every value forced inside its bounds. */
	public ArcaneSettings clamped() {
		return new ArcaneSettings(
				clamp(this.manaCost, 0, MAX_COST),
				clamp(this.baseDamage, 0, MAX_DAMAGE),
				clamp(this.projectileSpeed, 0, MAX_SPEED),
				clamp(this.knockback, 0, MAX_KNOCKBACK),
				clamp(this.cooldownTicks, 0, MAX_COOLDOWN_TICKS),
				clamp(this.rangeTicks, 0, MAX_RANGE_TICKS));
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(value, max));
	}
}

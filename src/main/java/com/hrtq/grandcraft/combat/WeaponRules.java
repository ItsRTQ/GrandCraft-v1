package com.hrtq.grandcraft.combat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * The two numbers that apply to every weapon regardless of what kind it is.
 *
 * <p>Nested inside {@link WeaponSettings} the way {@link ArcaneSettings} nests inside
 * {@link CategorySettings}, and for the mirror-image reason: those values belong to one
 * category, these belong to none of them.
 *
 * <h2>Why the down-scale is a setting and not a constant</h2>
 * {@link #weaponBasePercent} is the single lever that decides how much of a fight is
 * the weapon and how much is the character. Turned up, gear matters and a diamond sword
 * is an event; turned down, a level is worth more than anything you can find in a
 * chest. That is a whole-game judgement rather than a balance tweak, and it is the
 * first thing anyone will want to move after playing for an hour — so it must be one
 * edit rather than one build.
 *
 * <p>It is expressed as "how much of the weapon survives" rather than as a flat
 * multiplier on the finished number, because a player's bare hands contribute one
 * damage that is <em>theirs</em>, not the weapon's. Halving a weapon should not halve
 * a punch. At 50 a diamond sword's 7 becomes 4 and an empty hand stays at 1.
 */
public record WeaponRules(int weaponBasePercent, int failedDamagePercent) {

	/** Bounds shared by the config fields and the server-side clamp. */
	public static final int MAX_BASE_PERCENT = 200;
	public static final int MAX_FAILED_PERCENT = 2000;

	/**
	 * A weapon keeps half its vanilla damage, and failing its requirement leaves you
	 * dealing one.
	 *
	 * <p>One rather than zero, and this is not a rounding-off. A hit that deals nothing
	 * sets no invulnerability frames, plays no hurt sound and produces no reaction, so
	 * it is indistinguishable from a swing that never registered — the mod reads as
	 * broken rather than as strict. One damage still connects, still staggers nothing,
	 * still costs stamina, and communicates "this weapon is not yours" in the only
	 * language a health bar speaks.
	 */
	public static final WeaponRules DEFAULT = new WeaponRules(50, 100);

	/** How much of a weapon's own damage survives. 1.0 keeps all of it. */
	public float baseScale() {
		return this.weaponBasePercent / 100.0F;
	}

	/** What a swing deals when the holder does not meet the weapon's requirement. */
	public float failedDamage() {
		return this.failedDamagePercent / 100.0F;
	}

	public static Codec<WeaponRules> codec(WeaponRules fallback) {
		return RecordCodecBuilder.create(instance -> instance.group(
				Codec.INT.optionalFieldOf("weapon_base_percent", fallback.weaponBasePercent())
						.forGetter(WeaponRules::weaponBasePercent),
				Codec.INT.optionalFieldOf("failed_damage_percent", fallback.failedDamagePercent())
						.forGetter(WeaponRules::failedDamagePercent)
		).apply(instance, WeaponRules::new));
	}

	public static final StreamCodec<ByteBuf, WeaponRules> STREAM_CODEC = StreamCodec.of(
			(buf, rules) -> {
				buf.writeInt(rules.weaponBasePercent());
				buf.writeInt(rules.failedDamagePercent());
			},
			buf -> new WeaponRules(buf.readInt(), buf.readInt()));

	/** A copy with every value forced inside its bounds. */
	public WeaponRules clamped() {
		return new WeaponRules(
				clamp(this.weaponBasePercent, 0, MAX_BASE_PERCENT),
				clamp(this.failedDamagePercent, 0, MAX_FAILED_PERCENT));
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(value, max));
	}
}

package com.hrtq.grandcraft.stats;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.LivingEntity;

/**
 * The minimum a character must have before a weapon works at all.
 *
 * <p>The cliff in a system that is otherwise a gradient. Scaling alone already makes a
 * mismatched weapon weak — a Sorcerer blends almost no Strength out of a claymore — but
 * weak is a number nobody reads. A requirement turns the same fact into an event: the
 * weapon states a price, the character either pays it or does not, and the failure is
 * legible on the tooltip before the first swing rather than inferred from a health bar.
 *
 * <p>Below it, damage is a flat pittance rather than zero. A hit that deals nothing at
 * all reads as a broken mod — no reaction, no invulnerability frames, no feedback — and
 * would be reported as one. See {@code WeaponRules.failedDamage}.
 *
 * <h2>This is the item's half of the contract</h2>
 * A requirement is a fact about a specific weapon, so it lives on the item as a data
 * component and is authored per weapon. The <em>weights</em> it is paired with are a
 * judgement about a whole class of weapon and live in the category config. Which stat
 * is demanded is not configured at all — it is derived from the weights, so a weapon
 * can never gate on a stat it does not actually scale off.
 */
public record WeaponRequirement(CharacterStat stat, int value) {

	/** Bound shared by the fallback derivation and any hand-authored value. */
	public static final int MAX_VALUE = 1000;

	/** Demands nothing. What bare hands and every unclaimed item resolve to. */
	public static final WeaponRequirement NONE = new WeaponRequirement(CharacterStat.STRENGTH, 0);

	public WeaponRequirement {
		// Throws rather than substituting a stat. The only way a null arrives here is a
		// decode of a name this build does not know, and silently gating a weapon on
		// Strength when it was authored against Arcane is a fault that would present as
		// a balance complaint months later.
		if (stat == null) {
			throw new IllegalArgumentException("A weapon requirement needs a stat");
		}

		value = Math.max(0, Math.min(value, MAX_VALUE));
	}

	/** Whether this character may use the weapon at full effect. */
	public boolean isMetBy(LivingEntity entity) {
		return this.value <= 0 || StatEffects.statOf(entity, this.stat.attribute()) >= this.value;
	}

	/** Whether there is anything to meet. A zero requirement is shown to nobody. */
	public boolean exists() {
		return this.value > 0;
	}

	/** "14 Strength", for the tooltip. */
	public MutableComponent describe() {
		return Component.literal(Integer.toString(this.value))
				.append(" ")
				.append(this.stat.displayName());
	}

	/**
	 * Fails loudly on an unknown stat name rather than defaulting to one.
	 *
	 * <p>The same choice {@code CharacterStat.STREAM_CODEC} makes, for a sharper reason:
	 * this decodes off a saved item stack, and quietly resolving rubbish to Strength
	 * would hand a player a weapon whose gate silently moved to a different stat than
	 * the one it was authored with.
	 */
	public static final Codec<WeaponRequirement> CODEC =
			RecordCodecBuilder.create(instance -> instance.group(
					CharacterStat.CODEC.fieldOf("stat").forGetter(WeaponRequirement::stat),
					Codec.INT.fieldOf("value").forGetter(WeaponRequirement::value)
			).apply(instance, WeaponRequirement::new));

	public static final StreamCodec<ByteBuf, WeaponRequirement> STREAM_CODEC = StreamCodec.of(
			(buf, requirement) -> {
				CharacterStat.STREAM_CODEC.encode(buf, requirement.stat());
				ByteBufCodecs.VAR_INT.encode(buf, requirement.value());
			},
			buf -> new WeaponRequirement(
					CharacterStat.STREAM_CODEC.decode(buf), ByteBufCodecs.VAR_INT.decode(buf)));
}

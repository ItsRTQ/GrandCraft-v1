package com.hrtq.grandcraft.skill;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * What the skill-line abilities are worth.
 *
 * <p>One record for every ability's numbers, not one per ability. Sixty abilities with
 * a settings class each would be sixty files of plumbing; the fields here are named
 * after the ability that reads them and that is enough.
 *
 * <p>Shipped in the same slice as the first ability rather than after it, which is
 * {@code tuning.md}'s first working-method rule and one this project already paid for:
 * most of the block slice was numbers round-tripping through the agent one build at a
 * time. A +50% damage figure is exactly that sort of number.
 *
 * <p>Every value is a whole number, so the config fields stay integer-typed and no
 * locale-dependent decimal separator is introduced — the same rule the combat, stat and
 * level settings already follow. Percentages are whole percent: 150 means one and a
 * half times.
 *
 * <p><strong>Server-held</strong>, like the combat settings and unlike the stat and
 * level ones. Nothing on the client needs these: the badge above the ability bar is
 * told how many ticks are left, not how many it started with.
 */
public record SkillSettings(
		int combatMasterDamagePercent,
		int combatMasterSpeedPercent,
		int combatMasterWindowTicks) {

	/**
	 * The user's figures for Combat Master: a blow that hits half again as hard, a
	 * speed nudge worth about a third of a Speed I potion, and a five-second window.
	 *
	 * <p>The damage bonus is deliberately the loud half. It has to be worth noticing on
	 * a light weapon as well as a claymore, and it is paid for by landing a block first
	 * — which is a real decision under pressure rather than something that accrues.
	 */
	public static final SkillSettings DEFAULT = new SkillSettings(50, 10, 100);

	/** Bounds shared by the config fields and the server-side clamp. */
	public static final int MAX_DAMAGE_PERCENT = 1000;
	public static final int MAX_SPEED_PERCENT = 500;
	public static final int MAX_WINDOW_TICKS = 1200;

	/**
	 * The multiplier an empowered hit applies, as a factor.
	 *
	 * <p>Shipped as "extra percent" rather than as a total so that zero is the feature's
	 * off switch and reads as one — the same convention every other bonus in this mod
	 * uses. 50 becomes 1.5.
	 */
	public float combatMasterDamageScale() {
		return 1.0F + this.combatMasterDamagePercent / 100.0F;
	}

	/** The movement bonus as a fraction, for the attribute modifier. 10 becomes 0.10. */
	public double combatMasterSpeedFraction() {
		return this.combatMasterSpeedPercent / 100.0;
	}

	/** Whether Combat Master is switched on at all. A window of zero turns it off. */
	public boolean combatMasterEnabled() {
		return this.combatMasterWindowTicks > 0;
	}

	public static final Codec<SkillSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			// Optional like every other settings record, so a file written by an older
			// build keeps loading as abilities are added.
			Codec.INT.optionalFieldOf("combat_master_damage_percent", DEFAULT.combatMasterDamagePercent())
					.forGetter(SkillSettings::combatMasterDamagePercent),
			Codec.INT.optionalFieldOf("combat_master_speed_percent", DEFAULT.combatMasterSpeedPercent())
					.forGetter(SkillSettings::combatMasterSpeedPercent),
			Codec.INT.optionalFieldOf("combat_master_window_ticks", DEFAULT.combatMasterWindowTicks())
					.forGetter(SkillSettings::combatMasterWindowTicks)
	).apply(instance, SkillSettings::new));

	public static final StreamCodec<ByteBuf, SkillSettings> STREAM_CODEC = StreamCodec.of(
			(buf, settings) -> {
				buf.writeInt(settings.combatMasterDamagePercent());
				buf.writeInt(settings.combatMasterSpeedPercent());
				buf.writeInt(settings.combatMasterWindowTicks());
			},
			// Java evaluates arguments left to right, so this matches the writes above.
			buf -> new SkillSettings(buf.readInt(), buf.readInt(), buf.readInt()));

	/**
	 * A copy with every value forced inside its bounds. Always applied to anything
	 * arriving over the network or read off disk, so a hand-built packet cannot grant a
	 * thousandfold hit or a window that never closes.
	 */
	public SkillSettings clamped() {
		return new SkillSettings(
				clamp(this.combatMasterDamagePercent, 0, MAX_DAMAGE_PERCENT),
				clamp(this.combatMasterSpeedPercent, 0, MAX_SPEED_PERCENT),
				clamp(this.combatMasterWindowTicks, 0, MAX_WINDOW_TICKS));
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(value, max));
	}
}

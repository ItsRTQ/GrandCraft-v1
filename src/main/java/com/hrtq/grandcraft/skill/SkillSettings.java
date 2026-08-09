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
		int combatMasterWindowTicks,
		int acrobatDashSpeed,
		int acrobatDashLift,
		int acrobatDashCost,
		int acrobatWallHooks,
		int acrobatHangDrainPerSecond,
		int acrobatHangDamagePercent,
		int acrobatHitStaminaCost,
		int acrobatGroundResetTicks,
		int acrobatHookCooldownTicks) {

	/**
	 * The user's figures for Combat Master: a blow that hits half again as hard, a
	 * speed nudge worth about a third of a Speed I potion, and a five-second window.
	 *
	 * <p>The damage bonus is deliberately the loud half. It has to be worth noticing on
	 * a light weapon as well as a claymore, and it is paid for by landing a block first
	 * — which is a real decision under pressure rather than something that accrues.
	 *
	 * <p>Acrobat's nine follow. Two of them are the user's own numbers (two wall hooks,
	 * a quarter off the damage) and the rest are derived — see the accessors below for
	 * the arithmetic behind the dash figures, which is the part that is easy to get
	 * wrong by eye.
	 */
	public static final SkillSettings DEFAULT =
			new SkillSettings(50, 10, 100, 60, 25, 12, 2, 4, 25, 25, 20, 4);

	/** Bounds shared by the config fields and the server-side clamp. */
	public static final int MAX_DAMAGE_PERCENT = 1000;
	public static final int MAX_SPEED_PERCENT = 500;
	public static final int MAX_WINDOW_TICKS = 1200;

	/**
	 * Past roughly three blocks a tick an actor outruns its own collision checks and
	 * starts cutting corners — {@link com.hrtq.grandcraft.combat.DodgeSettings}'s
	 * reason for the same ceiling, and it applies harder in the air.
	 */
	public static final int MAX_DASH_SPEED = 300;
	public static final int MAX_DASH_LIFT = 100;
	public static final int MAX_STAMINA_COST = 1000;
	public static final int MAX_WALL_HOOKS = 10;
	public static final int MAX_DRAIN_PER_SECOND = 200;
	public static final int MAX_ACROBAT_TICKS = 200;

	/**
	 * A reduction is a whole percent <em>off</em>, so it cannot exceed 100.
	 *
	 * <p>Deliberately not {@link #MAX_DAMAGE_PERCENT}. A 500% reduction would make
	 * {@link #acrobatHangDamageScale()} negative, and a negative multiplier on a swing
	 * reads in game as "the Outlaw cannot hit anything while on a wall" — a bug report
	 * about the mechanic rather than about the slider that caused it.
	 */
	public static final int MAX_REDUCTION_PERCENT = 100;

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

	/**
	 * Whether Acrobat is switched on at all. A dash that goes nowhere is not a dash,
	 * so the speed is the off switch — and switching it off takes the wall hooks with
	 * it, since a hook is only ever reached by dashing into a wall.
	 */
	public boolean acrobatEnabled() {
		return this.acrobatDashSpeed > 0;
	}

	/** Whether walls can be caught at all. Zero hooks leaves the dash on its own. */
	public boolean acrobatHooksEnabled() {
		return this.acrobatWallHooks > 0;
	}

	/**
	 * The dash's horizontal impulse, in blocks per tick.
	 *
	 * <p>Air drag is 0.91 a tick, so an impulse {@code s} carries
	 * {@code s * (1 - 0.91^t) / 0.09} blocks over {@code t} airborne ticks. A dash from
	 * around a jump's apex has roughly twelve of them, which makes the useful rule of
	 * thumb <strong>distance ≈ 7.5 × speed</strong>: the default 0.60 covers about 4.5
	 * blocks, half again a sprint-jump.
	 *
	 * <p><strong>Do not reach for the dodge's figure here.</strong> A dodge is 0.95 and
	 * still only travels about two blocks, because it happens on the ground against
	 * 0.546 friction. The same number in the air is over seven. The two are not
	 * comparable and this is the likeliest mis-tune in the whole passive.
	 */
	public double acrobatDashSpeedPerTick() {
		return this.acrobatDashSpeed / 100.0;
	}

	/**
	 * The dash's upward impulse, in blocks per tick — "half a block of height".
	 *
	 * <p>Minecraft's vertical recurrence is {@code v' = (v - 0.08) * 0.98}, which
	 * integrates to {@code height(N) = -3.92N + 50(v0 + 3.92)(1 - 0.98^N)}. That model
	 * puts vanilla's 0.42 jump at 1.272 blocks against its accepted 1.2522 — within
	 * 1.6%, the gap being where the move sits relative to the gravity step inside a
	 * tick. Solving it for half a block: 0.24 gives 0.47, <strong>0.25 gives
	 * 0.506</strong>, 0.26 gives 0.545.
	 *
	 * <p>This is <em>set</em> rather than added, and that is load-bearing in two
	 * directions. Adding would stack across a chained dash into flight; and setting is
	 * what lets a dash cancel a fall, which is most of what the move reads as. The cost
	 * is that dashing while already rising from a jump <em>reduces</em> the climb —
	 * correct, because a double jump should not go higher, but it looks like a bug if
	 * you are not expecting it.
	 */
	public double acrobatDashLiftPerTick() {
		return this.acrobatDashLift / 100.0;
	}

	/** The hang drain as a per-tick figure, the shape {@code StaminaPool} spends in. */
	public float acrobatHangDrainPerTick() {
		return this.acrobatHangDrainPerSecond / 20.0F;
	}

	/**
	 * What a swing is multiplied by while hanging. 25 becomes 0.75.
	 *
	 * <p>Stated as a reduction rather than a scale so that zero is the off switch and
	 * reads as one, the same convention {@link #combatMasterDamageScale()} uses from
	 * the other direction. Safe from going negative only because {@link #clamped()}
	 * bounds the percent at {@link #MAX_REDUCTION_PERCENT}.
	 */
	public float acrobatHangDamageScale() {
		return 1.0F - this.acrobatHangDamagePercent / 100.0F;
	}

	public static final Codec<SkillSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			// Optional like every other settings record, so a file written by an older
			// build keeps loading as abilities are added.
			Codec.INT.optionalFieldOf("combat_master_damage_percent", DEFAULT.combatMasterDamagePercent())
					.forGetter(SkillSettings::combatMasterDamagePercent),
			Codec.INT.optionalFieldOf("combat_master_speed_percent", DEFAULT.combatMasterSpeedPercent())
					.forGetter(SkillSettings::combatMasterSpeedPercent),
			Codec.INT.optionalFieldOf("combat_master_window_ticks", DEFAULT.combatMasterWindowTicks())
					.forGetter(SkillSettings::combatMasterWindowTicks),
			Codec.INT.optionalFieldOf("acrobat_dash_speed", DEFAULT.acrobatDashSpeed())
					.forGetter(SkillSettings::acrobatDashSpeed),
			Codec.INT.optionalFieldOf("acrobat_dash_lift", DEFAULT.acrobatDashLift())
					.forGetter(SkillSettings::acrobatDashLift),
			Codec.INT.optionalFieldOf("acrobat_dash_cost", DEFAULT.acrobatDashCost())
					.forGetter(SkillSettings::acrobatDashCost),
			Codec.INT.optionalFieldOf("acrobat_wall_hooks", DEFAULT.acrobatWallHooks())
					.forGetter(SkillSettings::acrobatWallHooks),
			Codec.INT.optionalFieldOf("acrobat_hang_drain_per_second", DEFAULT.acrobatHangDrainPerSecond())
					.forGetter(SkillSettings::acrobatHangDrainPerSecond),
			Codec.INT.optionalFieldOf("acrobat_hang_damage_percent", DEFAULT.acrobatHangDamagePercent())
					.forGetter(SkillSettings::acrobatHangDamagePercent),
			Codec.INT.optionalFieldOf("acrobat_hit_stamina_cost", DEFAULT.acrobatHitStaminaCost())
					.forGetter(SkillSettings::acrobatHitStaminaCost),
			Codec.INT.optionalFieldOf("acrobat_ground_reset_ticks", DEFAULT.acrobatGroundResetTicks())
					.forGetter(SkillSettings::acrobatGroundResetTicks),
			Codec.INT.optionalFieldOf("acrobat_hook_cooldown_ticks", DEFAULT.acrobatHookCooldownTicks())
					.forGetter(SkillSettings::acrobatHookCooldownTicks)
	).apply(instance, SkillSettings::new));

	public static final StreamCodec<ByteBuf, SkillSettings> STREAM_CODEC = StreamCodec.of(
			(buf, settings) -> {
				buf.writeInt(settings.combatMasterDamagePercent());
				buf.writeInt(settings.combatMasterSpeedPercent());
				buf.writeInt(settings.combatMasterWindowTicks());
				buf.writeInt(settings.acrobatDashSpeed());
				buf.writeInt(settings.acrobatDashLift());
				buf.writeInt(settings.acrobatDashCost());
				buf.writeInt(settings.acrobatWallHooks());
				buf.writeInt(settings.acrobatHangDrainPerSecond());
				buf.writeInt(settings.acrobatHangDamagePercent());
				buf.writeInt(settings.acrobatHitStaminaCost());
				buf.writeInt(settings.acrobatGroundResetTicks());
				buf.writeInt(settings.acrobatHookCooldownTicks());
			},
			// Java evaluates arguments left to right, so this matches the writes above.
			// Twelve positional ints with no field names between them: if a field is ever
			// added anywhere but the end, this is the line that silently starts lying.
			buf -> new SkillSettings(buf.readInt(), buf.readInt(), buf.readInt(),
					buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(),
					buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(),
					buf.readInt()));

	/**
	 * A copy with every value forced inside its bounds. Always applied to anything
	 * arriving over the network or read off disk, so a hand-built packet cannot grant a
	 * thousandfold hit or a window that never closes.
	 */
	public SkillSettings clamped() {
		return new SkillSettings(
				clamp(this.combatMasterDamagePercent, 0, MAX_DAMAGE_PERCENT),
				clamp(this.combatMasterSpeedPercent, 0, MAX_SPEED_PERCENT),
				clamp(this.combatMasterWindowTicks, 0, MAX_WINDOW_TICKS),
				clamp(this.acrobatDashSpeed, 0, MAX_DASH_SPEED),
				clamp(this.acrobatDashLift, 0, MAX_DASH_LIFT),
				clamp(this.acrobatDashCost, 0, MAX_STAMINA_COST),
				clamp(this.acrobatWallHooks, 0, MAX_WALL_HOOKS),
				clamp(this.acrobatHangDrainPerSecond, 0, MAX_DRAIN_PER_SECOND),
				// Not MAX_DAMAGE_PERCENT — see MAX_REDUCTION_PERCENT.
				clamp(this.acrobatHangDamagePercent, 0, MAX_REDUCTION_PERCENT),
				clamp(this.acrobatHitStaminaCost, 0, MAX_STAMINA_COST),
				clamp(this.acrobatGroundResetTicks, 0, MAX_ACROBAT_TICKS),
				clamp(this.acrobatHookCooldownTicks, 0, MAX_ACROBAT_TICKS));
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(value, max));
	}
}

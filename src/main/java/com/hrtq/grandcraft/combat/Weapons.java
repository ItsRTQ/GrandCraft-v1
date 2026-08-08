package com.hrtq.grandcraft.combat;

import net.minecraft.world.item.ItemStack;

/**
 * Turns a held item into the attack it produces.
 *
 * <p>The one place the weapon layer and the combat state machine meet.
 * {@link WeaponCategory} says what kind of thing is being held, {@link WeaponTuning}
 * says what that kind costs, and this puts the two together into the
 * {@link WeaponProfile} the controller latches for the swing.
 */
public final class Weapons {
	private Weapons() {
	}

	/**
	 * The attack the given stack produces for an actor that fights with weapons.
	 *
	 * @param stack what is in the actor's main hand; may be empty
	 * @param startupTicks the actor's own wind-up, from its {@link ActorSettings} — see
	 *                     {@link #startupFor}
	 * @param recoveryTicks the actor's own endlag, from the same place — see
	 *                      {@link #recoveryFor}
	 */
	public static WeaponProfile profileFor(ItemStack stack, int startupTicks, int recoveryTicks) {
		WeaponCategory category = WeaponCategory.of(stack);
		CategorySettings values = WeaponTuning.current().forCategory(category);

		// The hit window and the cost are the weapon's outright. The two ends of the swing
		// — what it commits you to before and after — are the actor's globals, which the
		// category may bend by a signed number of ticks but never replace.
		return new WeaponProfile(category,
				new AttackProfile(startupFor(startupTicks, category, values),
						values.activeTicks(),
						recoveryFor(recoveryTicks, category, values)),
				values.staminaCost());
	}

	/**
	 * How long this actor's wind-up runs with that weapon in hand.
	 *
	 * <p><strong>This is the one place the global and the weapon meet.</strong> The
	 * wind-up is the actor's own, from {@code /grandcraft config combat} (user,
	 * 2026-08-07): one number paces every swing, which is what makes a telegraph
	 * learnable at all — the four categories were each drifting on their own before it.
	 * What a category may do is <em>bend</em> that rhythm, not replace it.
	 *
	 * <h2>The modifier is signed, and that is the requirement</h2>
	 *
	 * <p>Weapons "may lower it or make it bigger" (user, 2026-08-07), so a category's
	 * number is an offset rather than a total — a dagger quicker than the rhythm, a
	 * greatsword slower, and a category with nothing to say leaves the global alone by
	 * storing zero. Only {@link WeaponCategory#HEAVY} says anything today (+5, to carry
	 * the greatsword's telegraph), which means every other weapon in the game is
	 * provably unaffected by this method existing.
	 *
	 * <h2>Why the clamp is here and not at the config screen</h2>
	 *
	 * <p>{@link AttackProfile} throws on a negative phase, and it is constructed <em>on
	 * the swing</em>. A modifier is stored signed and clamped only against its own
	 * bounds, so nothing before this point can guarantee the sum is legal: a global of 2
	 * with a modifier of -5 is two perfectly valid numbers. Clamping the sum here is what
	 * turns that into a 0 tick wind-up instead of a crashed attack.
	 */
	private static int startupFor(int startupTicks, WeaponCategory category,
			CategorySettings values) {
		return clampPhase(startupTicks + values.startupModifier());
	}

	/**
	 * How long this actor is locked after the swing with that weapon in hand.
	 *
	 * <p>The sibling of {@link #startupFor} in every respect, and deliberately a separate
	 * method rather than a shared one: <strong>the two ends of a swing are not tuned
	 * together.</strong> A weapon that telegraphs slowly need not also be slow to leave,
	 * and a modifier that moved both from one number could not express a dagger — quick to
	 * start and quick to leave — against a claymore that is slow at both. Read
	 * {@code startupFor} for the full argument; everything in it applies here, including
	 * that the modifier must be able to lower this as well as raise it (user, 2026-08-07)
	 * and that it must clamp, because {@link AttackProfile} refuses a negative endlag on
	 * the swing rather than at the config screen.
	 *
	 * <p>Endlag carries more weight per tick than the wind-up does: it is the whole cost
	 * of a whiff, and it is what makes spacing a decision rather than a formality
	 * ({@code product-goal.md}). A modifier here is the strongest single lever a weapon
	 * will have.
	 *
	 * <p><strong>Every category stores zero today</strong>, so endlag is the global for
	 * everything. That is deliberate rather than unfinished: the pre-global config had
	 * 40 ticks of claymore recovery, and nobody ever judged that by eye. It is a
	 * judgement to make once a heavy swing can be watched, not one to inherit.
	 */
	private static int recoveryFor(int recoveryTicks, WeaponCategory category,
			CategorySettings values) {
		return clampPhase(recoveryTicks + values.recoveryModifier());
	}

	/**
	 * A phase length the {@link AttackProfile} constructor will accept.
	 *
	 * <p>Shared by both seams because "what is a legal phase" is one rule, not two — the
	 * asymmetry between wind-up and endlag is in what they are worth, not in what the
	 * engine will take.
	 */
	private static int clampPhase(int ticks) {
		return Math.clamp(ticks, 0, CategorySettings.MAX_PHASE_TICKS);
	}
}

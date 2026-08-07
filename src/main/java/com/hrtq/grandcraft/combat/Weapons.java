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

		// The hit window and the cost are the weapon's. The two ends of the swing — what
		// it commits you to before and after — are the actor's globals.
		return new WeaponProfile(category,
				new AttackProfile(startupFor(startupTicks, category, values),
						values.activeTicks(),
						recoveryFor(recoveryTicks, category, values)),
				values.staminaCost());
	}

	/**
	 * How long this actor's wind-up runs with that weapon in hand.
	 *
	 * <p><strong>This is the one place the global and the weapon meet, and it is a seam
	 * rather than an expression</strong> — it exists to be the only edit the day weapons
	 * start modifying the wind-up.
	 *
	 * <h2>Today: the global, whatever is held</h2>
	 *
	 * <p>The wind-up is the actor's own, from {@code /grandcraft config combat} (user,
	 * 2026-08-07). One number paces every swing the player makes, which is what makes it
	 * tunable at all: a telegraph is only readable if the reader learns one rhythm, and
	 * the four categories were each drifting on their own before this.
	 *
	 * <p><strong>The per-category Startup on {@code /grandcraft config weapons} is
	 * therefore not read</strong>, and its row is hidden rather than left to be tuned —
	 * the stored value is untouched and waits for the modifier below. Two screens showing
	 * the same number is how a tuning change gets reported as a broken mechanic;
	 * {@code CombatConfigScreen} carries the same rule the other way round for endlag.
	 *
	 * <h2>Next: a weapon that can lower it or raise it</h2>
	 *
	 * <p>The stated intent (user, 2026-08-07: weapons "may lower it or make it bigger") is
	 * that the category's number becomes <em>signed</em> against the global rather than a
	 * total — a dagger quicker than the rhythm, a greatsword slower — so whatever lands
	 * here has to be able to go both ways from the global, and the result still has to be
	 * a legal phase length. {@code category} and {@code values} are already in hand for
	 * exactly that; nothing else in the swing path needs to know it happened.
	 *
	 * <p>Whichever form it takes, clamp it here: {@link AttackProfile} throws on a
	 * negative wind-up, and this runs on the swing rather than at the config screen, so a
	 * modifier that could reach below zero would crash the attack rather than the edit.
	 */
	private static int startupFor(int startupTicks, WeaponCategory category,
			CategorySettings values) {
		return startupTicks;
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
	 */
	private static int recoveryFor(int recoveryTicks, WeaponCategory category,
			CategorySettings values) {
		return recoveryTicks;
	}
}

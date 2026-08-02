package com.hrtq.grandcraft.combat;

/**
 * What the thing in an actor's hand does to its attack.
 *
 * <p>Resolved once per swing and threaded through, never cached against a stack.
 * The right scope for "which weapon is this swing" is the swing itself, and
 * {@code CombatController} already latches exactly that: it stores the
 * {@link AttackProfile} when the attack begins and every later phase reads the latch
 * rather than the item, so a weapon swapped mid-swing cannot change the swing that
 * is already in flight.
 *
 * <p>Shaped after {@code StaminaScaling}: a small value object built at the point of
 * use, holding the answer rather than the question.
 */
public record WeaponProfile(WeaponCategory category, AttackProfile attack, float attackCost) {

	/**
	 * For an actor whose attack is not governed by what it holds.
	 *
	 * <p>Hands back the actor's own configured timing and cost unchanged, which is
	 * byte-for-byte the behaviour that existed before weapons did. This is the mob
	 * path, and having it here rather than as a null check at every call site is what
	 * keeps {@code CombatController} free of "if there is no weapon" branches.
	 */
	public static WeaponProfile fromActor(CombatProfile profile) {
		return new WeaponProfile(WeaponCategory.UNARMED,
				profile.melee(), profile.stamina().attackCost());
	}
}

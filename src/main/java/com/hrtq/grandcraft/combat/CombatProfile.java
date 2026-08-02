package com.hrtq.grandcraft.combat;

/**
 * The set of combat capabilities an actor opts into, resolved from its current
 * {@link ActorSettings}.
 *
 * <p>The stat bands are the ranges an individual rolls from on spawn; what it
 * actually rolled lives in {@link RolledStats} on the entity.
 */
public record CombatProfile(
		CombatActor actor,
		AttackProfile melee,
		StaggerProfile stagger,
		StaminaSettings stamina,
		DodgeSettings dodge,
		BlockSettings block,
		StatRange health,
		StatRange damage,
		StatRange speed,
		StatRange defence) {

	public CombatProfile {
		if (actor == null || melee == null || stagger == null || stamina == null
				|| dodge == null || block == null) {
			throw new IllegalArgumentException(
					"A combat profile needs an actor, melee, stagger, stamina, dodge and block profile");
		}
	}

	/**
	 * Whether this actor pays stamina to act, combining the compile-time opt-in with
	 * the runtime config switch.
	 *
	 * <p>Both halves matter and callers should not have to remember that: the verb
	 * says the actor takes part at all, and a configured pool of zero is how an admin
	 * turns it back off without a rebuild.
	 */
	public boolean usesStamina() {
		return this.actor.has(CombatVerb.STAMINA) && this.stamina.enabled();
	}

	/**
	 * Whether this actor can dodge, combining the compile-time opt-in with the
	 * runtime config switch — the same two-part rule as {@link #usesStamina()}.
	 */
	public boolean usesDodge() {
		return this.actor.has(CombatVerb.DODGE) && this.dodge.enabled();
	}

	/**
	 * Whether this actor can guard, combining the compile-time opt-in with the
	 * runtime config switch — the same two-part rule as {@link #usesStamina()}.
	 */
	public boolean usesBlock() {
		return this.actor.has(CombatVerb.BLOCK) && this.block.enabled();
	}

	/**
	 * Whether this actor's attack timing and cost come from what it is holding.
	 *
	 * <p>No runtime half to combine with, unlike the three above: an actor holding
	 * nothing the weapon tags claim already resolves to
	 * {@link WeaponCategory#UNARMED}, whose defaults are this profile's own values, so
	 * there is nothing a config switch would need to turn off.
	 */
	public boolean usesWeapons() {
		return this.actor.has(CombatVerb.WEAPONS);
	}
}

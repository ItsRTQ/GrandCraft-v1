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
		StatRange health,
		StatRange damage,
		StatRange speed,
		StatRange defence) {

	public CombatProfile {
		if (actor == null || melee == null || stagger == null || stamina == null || dodge == null) {
			throw new IllegalArgumentException(
					"A combat profile needs an actor, melee, stagger, stamina and dodge profile");
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
}

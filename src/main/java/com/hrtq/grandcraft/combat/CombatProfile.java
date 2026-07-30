package com.hrtq.grandcraft.combat;

/**
 * The set of combat capabilities an actor opts into.
 *
 * <p>Phase 1 carries only a melee attack profile. Later capabilities (stamina,
 * dodge, block) become additional components here, so an actor can support some
 * without being required to support all of them.
 */
public record CombatProfile(AttackProfile melee) {
	public CombatProfile {
		if (melee == null) {
			throw new IllegalArgumentException("A combat profile needs a melee attack profile");
		}
	}
}

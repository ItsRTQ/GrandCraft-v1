package com.hrtq.grandcraft.combat;

/**
 * The combat states shared by every GrandCraft combatant.
 *
 * <p>NEUTRAL is the baseline: combat is built around returning here often rather
 * than maintaining long attack chains.
 *
 * <p>STAGGERED is a hit reaction, not a stun. It is only ever entered from
 * NEUTRAL or ATTACK_STARTUP — see {@link CombatController#applyStagger}.
 */
public enum CombatState {
	NEUTRAL,
	ATTACK_STARTUP,
	ATTACK_ACTIVE,
	ATTACK_RECOVERY,
	STAGGERED
}

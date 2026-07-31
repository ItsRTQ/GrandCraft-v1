package com.hrtq.grandcraft.combat;

/**
 * The combat capabilities an actor can opt into.
 *
 * <p>Replaces what used to be a pair of {@code boolean} constructor arguments on
 * {@link CombatActor}. The verbs are independent of one another: an actor can take
 * part in stamina without its melee being ours to phase, which is what lets a
 * brain-driven or ranged mob join GrandCraft combat before a hook for its attack
 * path exists. Anything an actor does not list keeps vanilla behaviour.
 *
 * <p>Adding a verb here is only half the work — something has to read it. The
 * places that do are deliberately few, so a new verb is easy to trace.
 */
public enum CombatVerb {
	/**
	 * This actor's melee runs through {@code MeleeAttackGoal}, which is what puts its
	 * attack phases, its cadence and its reach under our control.
	 *
	 * <p>Absent for the player, whose swing is client-initiated on vanilla timing, so
	 * a startup value would have nothing to delay. Also absent for any mob whose real
	 * attack runs through the brain or a ranged goal — without this, an injection on
	 * the melee goal such a mob merely happens to own would half-govern it.
	 */
	PHASED_MELEE,

	/**
	 * This actor rolls its stats from a band on first spawn, which is what makes a
	 * group of the same mob vary in difficulty.
	 *
	 * <p>Absent for the player: randomising their own health each respawn would read
	 * as broken. An actor without it reads the low end of each range as a fixed value.
	 */
	RANDOM_STATS,

	/**
	 * This actor spends stamina to act, and stalls when it runs out.
	 *
	 * <p>Symmetric by design. A mob that runs low on stamina has to stop attacking,
	 * which is a readable opening the player can punish — the same constraint the
	 * player is under, rather than a difficulty number.
	 */
	STAMINA,

	/**
	 * This actor can dodge: a committed roll with invulnerability at the front and a
	 * vulnerable, action-locked tail.
	 *
	 * <p>The answer to a telegraph. It is only worth anything because wind-ups became
	 * readable — a dodge against an invisible attack is a guess, which is why this
	 * waited for the animation layer.
	 *
	 * <p>Only the player has it today, but nothing in the implementation is
	 * player-specific: the phases live in the shared state machine and the roll is a
	 * whole-body rotation on the shared rig. A mob gains it by gaining the verb and
	 * something to decide <em>when</em> — an AI goal, which is the missing half.
	 */
	DODGE,

	/**
	 * This actor can guard: a held stance that absorbs what comes at its front, paid
	 * for out of the stamina pool in proportion to the damage stopped.
	 *
	 * <p>The other answer to a telegraph, and deliberately the opposite shape to
	 * {@link #DODGE}. A dodge protects instantly and leaves a vulnerable tail; a guard
	 * is vulnerable while it comes up and safe once it is there. That asymmetry is the
	 * decision — commit early and cheaply, or commit late and expensively — and it is
	 * why two defensive verbs are worth more than one good one.
	 *
	 * <p>Like {@link #DODGE}, only the player has it today and nothing in the
	 * implementation is player-specific. A mob gains it by gaining the verb and an AI
	 * goal to decide when to raise it.
	 */
	BLOCK
}

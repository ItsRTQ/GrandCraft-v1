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
	BLOCK,

	/**
	 * This actor does not die when its health reaches zero: it falls prone with a
	 * bleed-out clock, and either an ally, its own decision, or that clock ends it.
	 *
	 * <p>The verb is what makes the death cancellable at all — {@code ALLOW_DEATH} is
	 * fired for every living thing on the server, and an actor without this one falls
	 * straight through to vanilla's death. That is deliberate for mobs: a downed zombie
	 * that nothing in the game can revive is a corpse that has to be killed twice.
	 *
	 * <p>Only the player has it, and unlike {@link #DODGE} and {@link #BLOCK} that is
	 * not merely because no mob has an AI goal for it yet. Reviving is a <em>player</em>
	 * verb — the state exists so that a party can answer a death, and a mob with no ally
	 * able to reach it would only ever meet the timer.
	 */
	DOWNED,

	/**
	 * This actor cannot be flinched out of a wind-up: a hit landing during
	 * {@code ATTACK_STARTUP} does damage and nothing else.
	 *
	 * <p><strong>Only the wind-up.</strong> Every other state staggers normally, so an
	 * actor with poise is not armoured — it is <em>committed</em>. Once it has decided
	 * to swing, the swing happens; interrupting it is no longer an option and the
	 * answer becomes getting out of the way. That is the whole point: a slow attacker
	 * whose telegraph can be cancelled by any chip of damage never actually attacks,
	 * and the longer and more readable the wind-up, the more certainly true that is.
	 * The cobble golem's is 24 ticks, which at close range is several free swings.
	 *
	 * <p>The hit is <strong>not registered with the stagger tracker</strong> either,
	 * so hits absorbed this way do not eat into the actor's consecutive-hit budget and
	 * leave it un-staggerable afterwards. Poise costs the attacker nothing and buys the
	 * defender nothing beyond finishing the swing it started.
	 *
	 * <p>Read by exactly one place — {@code CombatController.applyStagger}.
	 */
	POISE,

	/**
	 * This actor's attack is governed by what it is holding: its endlag and stamina
	 * cost come from the held item's {@link WeaponCategory} rather than from its own
	 * {@link ActorSettings}.
	 *
	 * <p><strong>Player only, and that is load-bearing.</strong> This verb is the
	 * single gate between "the player's swing follows the weapon in its hand" and
	 * "every mob in the game silently inherits the medium category's timing". An
	 * actor without it is handed its own configured values unchanged, so the mob path
	 * is byte-for-byte what it was before weapons existed — if a zombie's cadence ever
	 * changes after a weapon change, this verb has leaked onto a mob entry.
	 *
	 * <p>Mob weapons are a later question and deliberately not this one. Vanilla mobs
	 * do spawn holding swords, and letting an item drive their cadence would retune
	 * every armed mob in the game as a side effect of a change aimed at the player.
	 *
	 * <p>Unlike stamina, dodge and block there is no runtime "off" half to pair with
	 * this, because {@link WeaponCategory#UNARMED} is already the neutral case: an
	 * actor holding nothing the tags claim gets values that match what it had before.
	 */
	WEAPONS
}

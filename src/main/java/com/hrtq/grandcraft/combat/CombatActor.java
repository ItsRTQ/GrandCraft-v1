package com.hrtq.grandcraft.combat;

import com.hrtq.grandcraft.entity.CobbleGolemEntity;
import com.hrtq.grandcraft.entity.DemonSkeletonEntity;
import com.hrtq.grandcraft.entity.ZombieHumanEntity;
import com.mojang.serialization.Codec;
import java.util.EnumSet;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;

/**
 * The kinds of actor that opt into GrandCraft combat, and the only place that
 * names a concrete entity type.
 *
 * <p>Adding a mob is one entry here: an id, the entity class it matches, the
 * {@link CombatVerb}s it takes part in, and its starting values. Everything
 * downstream — the config file, the network packet, and the tab in the config
 * screen — is driven off {@code values()}, so nothing else needs touching.
 *
 * <p><strong>Declaration order is match precedence.</strong> {@link #forEntity}
 * returns the first entry whose class matches, so a subclass that deserves its own
 * entry must be declared before its supertype.
 */
public enum CombatActor {
	/**
	 * The player has every verb. It had no wind-up for a long time, and the reason was
	 * never the state machine — the client played swing and crit visuals at click time
	 * and there was no animation layer to hide a server-side delay behind. Both halves
	 * of that were answered on 2026-08-05: the animator's four attack clips give the
	 * wind-up something to show, and {@code MultiPlayerGameMode}'s local hit prediction
	 * is suppressed so the visuals wait for the blow.
	 *
	 * <p><strong>{@code PHASED_MELEE} on the player means something different from what
	 * it means on a mob.</strong> A mob's melee goal polls {@code canDealDamage} every
	 * tick; the player is driven by a click, so {@code PlayerAttack} is what carries the
	 * swing from that click to its active frame.
	 */
	PLAYER("player", Player.class,
			EnumSet.of(CombatVerb.STAMINA, CombatVerb.DODGE, CombatVerb.BLOCK,
					CombatVerb.WEAPONS, CombatVerb.PHASED_MELEE, CombatVerb.DOWNED),
			new ActorSettings(
					// THE PLAYER'S GLOBAL WIND-UP (user, 2026-08-07). One number paces
					// every swing whatever is held, so the telegraph teaches one rhythm
					// rather than four; a weapon will modify it later rather than replace
					// it, and Weapons.startupFor is where that lands. Five is MEDIUM's
					// old figure — the category documented as "the baseline everything
					// else is judged against", and the one the confirmed sword clip was
					// timed against — so the swing that has been tested does not move.
					// It was 0 while nothing read it.
					5, CombatConstants.DEFAULT_ACTIVE_TICKS, 3, 4,
					StatRange.of(1.0),
					StatRange.of(1.0),
					StatRange.of(1.0),
					StatRange.of(0.0),
					// Roughly eight swings from full, and a sustained rate a little under
					// a sword's vanilla cadence, so spacing costs something without the
					// player ever standing there unable to swing at a normal pace.
					// Sprint and jump are deliberately cheap. Nothing regenerates while
					// stamina is being spent, so the sprint number is really "seconds
					// of continuous sprint from full" — at 3 a second that is about
					// half a minute before needing to catch a breath, which reads as a
					// limit rather than a leash. Minecraft is a travel game as well as
					// a combat one, and a figure tuned only against fights makes
					// crossing the map miserable.
					new StaminaSettings(100, 15, 10, 12, 3, 4),
					// Seven ticks of invulnerability against a zombie whose whole swing
					// is startup 5 + active 2: enough that committing on the wind-up
					// beats it outright, and short enough that dodging on reflex the
					// moment you see anything leaves the six tick tail exposed. The
					// cost is deliberately above a swing's, so trading attacks for
					// dodges is a real budget decision rather than a free extra option.
					new DodgeSettings(7, 6, 95, 18),
					// Three ticks to come up against a zombie's five tick wind-up: reading
					// the telegraph early is enough, reacting to the hit itself is not.
					//
					// The absorb cost is tuned against a whole fight rather than against a
					// single swing. Costing what a swing costs was the first attempt and
					// was badly wrong: a swing is an occasional choice, while blocking is
					// continuous under pressure, so the pool funded about seven hits and
					// the guard then collapsed on a six second timer forever. At 2.50 a
					// point of damage a zombie swing costs 7.5, which against one attacker
					// is a slow net loss — twenty seconds of pure blocking — and against
					// two is a rout. Fighting back is meant to be the way out, not
					// outlasting them from behind the guard.
					//
					// A shield pays six tenths of the absorb, which is the whole reason to
					// carry one now that vanilla's own blocking is gone.
					//
					// The arc is 135 rather than vanilla's 90 because 90 puts the boundary
					// exactly where a mob shoving into your side ends up. Entity push
					// settles a zombie at roughly a hitbox width away, and from there its
					// angle hovers around ninety degrees and crosses back and forth with
					// every step — so the same attack blocked or landed at close to random.
					// Widening it to 120 fixed most of that; 135 puts the boundary well
					// clear of where a crowding attacker sits, and still leaves a ninety
					// degree cone behind the actor that no guard covers, which is the rule
					// the arc exists for.
					new BlockSettings(3, 3, 10, 250, 2, 135, 40, 60),
					// A minute on the clock, 7% of normal speed, and 20 ticks off the clock
					// per point of damage — all three the user's figures (2026-08-09).
					//
					// THE 20 IS AN IDENTITY, NOT A RATE. At twenty ticks it is exactly one
					// second per point of damage, which makes the clock a second health
					// bar that happens to be denominated in seconds: a four damage zombie
					// swing costs four seconds, a forty damage hit costs forty. The user's
					// own framing — "the seconds the player has left are the health he has
					// left". Anything other than 20 breaks that reading, so move it only
					// on purpose.
					//
					// Three seconds to revive is long enough that it cannot be done in the
					// middle of a fight without someone else holding the line, which is
					// the whole reason the verb is worth having in a party. Getting up on
					// 30% health keeps a revive from being a full reset: the ally who
					// picks you up has bought you a retreat, not another life.
					new DownedSettings(1200, 7, 20, 60, 30, 30, 30))),

	/**
	 * The GeckoLib-drawn zombie-human.
	 *
	 * <p>It holds {@link CombatVerb#PHASED_MELEE} only because
	 * {@link CombatPhaseAnimations} exists: the animation layer poses the vanilla rig,
	 * which a GeckoLib model never reaches, so until that bridge was built this mob
	 * would have wound up for a quarter of a second showing nothing. Phased melee is
	 * only honest when the wind-up can be seen.
	 *
	 * <p>Values match ZOMBIE so it reads as a reskin rather than a new opponent; what
	 * is being tested here is the rendering and animation path, not the tuning.
	 */
	ZOMBIE_HUMAN("zombie_human", ZombieHumanEntity.class,
			EnumSet.of(CombatVerb.PHASED_MELEE, CombatVerb.RANDOM_STATS),
			new ActorSettings(
					// A wide window to match the long wind-up. The animation is a
					// deliberate, readable raise, and a two tick window meant the target
					// had always stepped out of reach by the time it opened — the mob
					// telegraphed, committed, and whiffed, forever.
					10, 6, 10, 4,
					new StatRange(0.8, 1.4),
					new StatRange(0.8, 1.4),
					new StatRange(0.9, 1.2),
					new StatRange(0.0, 6.0),
					// Mobs have no stamina, deliberately. A pool gave them a second,
					// invisible way to fail: one that whiffed a few swings ran itself
					// out and then stood doing nothing, which is indistinguishable from
					// a broken attack and is how it got reported. A player can see their
					// own bar and read the pause; nobody can see a mob's. A mob's pacing
					// is its phase timings, and that is enough. Zeroed rather than only
					// un-verbed so the config screen tells the truth, as dodge and block
					// already do.
					new StaminaSettings(0, 0, 0, 0, 0, 0),
					new DodgeSettings(0, 0, 0, 0),
					new BlockSettings(0, 0, 0, 0, 0, 0, 0, 0),
					// Zeroed rather than only un-verbed, exactly as dodge and block are, so
					// the config screen tells the truth about a mob that cannot be revived.
					new DownedSettings(0, 0, 0, 0, 0, 0, 0))),

	/**
	 * The GeckoLib-drawn cobble golem, and the mod's first deliberately slow opponent.
	 *
	 * <p>Holds {@link CombatVerb#PHASED_MELEE} on the same terms as the two mobs below:
	 * {@link CombatPhaseAnimations} can show the wind-up, so committing to one is
	 * honest. This is the first mob whose delivery contained a <strong>separate</strong>
	 * wind-up clip and a return-to-rest, so it is also the first with every one of
	 * startup, active and recovery animated — see {@code CobbleGolemEntity}.
	 *
	 * <p><strong>Slow, huge and heavy, all three deliberately</strong> (user, 2026-08-20:
	 * *"make the attack stronger with more radius… but make it slower"*). Twenty-four
	 * ticks of telegraph is nearly five times a zombie's, and with fourteen of endlag
	 * behind it the cycle is <strong>58 ticks — 2.9 seconds</strong>, against a zombie's
	 * 0.85. Nothing else in the table is remotely this slow, and nothing else hits for
	 * twelve in a six and a half block circle. The two are the same decision: the
	 * opening after a golem's swing is the whole counterplay, and it has to be long
	 * enough to be worth taking a risk for.
	 *
	 * <p><strong>Lesson 7 is bought off by the radius rather than by the window.</strong>
	 * The lesson is written against an effective reach of about 1.4 blocks, where a
	 * target walking backwards leaves the danger zone during a long wind-up. This mob
	 * commits at <em>six and a half</em> and hits every direction at once, so escaping a
	 * 24 tick telegraph means covering six blocks in 1.2 seconds — which a sprinting
	 * player can just do and a walking one cannot. That is the intended shape: the slam
	 * is escapable, but only by committing to running, and the fourteen tick window
	 * means a half-hearted step back does not save anyone.
	 *
	 * <p>The hit window has a second job here that it has nowhere else: the shockwave
	 * ring is drawn across it, so widening or narrowing it changes how many rings the
	 * effect gets. Fourteen gives five.
	 *
	 * <p><strong>The startup and the wind-up clip's hold have to move together</strong> —
	 * see {@code CobbleGolemEntity}. Raising this number alone spreads a ten tick arm
	 * raise over the whole phase and turns the telegraph back into slow motion.
	 *
	 * <p><strong>{@link CombatVerb#POISE} is what makes the 24 tick wind-up survivable
	 * for the golem</strong> (user, 2026-08-20), and it is the first entry in the table
	 * to hold it. A telegraph this long is otherwise several free swings for anyone
	 * standing next to it, every one of which cancels the wind-up, so the slam would
	 * simply never happen against a player who kept attacking. The golem now commits:
	 * hit it mid-raise and it takes the damage and swings anyway. Every other state
	 * staggers it normally, so the endlag is still the opening.
	 *
	 * <p>Rolled ranges are narrower than the zombie's on health and damage and much
	 * narrower on speed: a golem is meant to be a known quantity, and one that
	 * occasionally rolls fast would undo the reading the long telegraph buys. Defence
	 * is where the spread went instead — 4 to 12 armour points, well above anything
	 * else in the table, because a pile of cobblestone that armour does nothing for is
	 * a strange pile of cobblestone.
	 *
	 * <p>Dormancy is <strong>not</strong> configured here, and deliberately: the detect
	 * range, the rise and the twenty second search are the mob's own behaviour rather
	 * than combat tuning, and they live as named constants on
	 * {@code CobbleGolemEntity}. Nothing on this tab can turn a golem's ambush off.
	 */
	COBBLE_GOLEM("cobble_golem", CobbleGolemEntity.class,
			EnumSet.of(CombatVerb.PHASED_MELEE, CombatVerb.RANDOM_STATS, CombatVerb.POISE),
			new ActorSettings(
					24, 14, 20, 6,
					new StatRange(0.9, 1.2),
					new StatRange(0.9, 1.2),
					new StatRange(0.95, 1.05),
					new StatRange(4.0, 12.0),
					// Mobs have no stamina, deliberately — a pool gives them a second,
					// invisible way to fail. Zeroed rather than only un-verbed, here as
					// below, so the config screen tells the truth about what this mob can
					// and cannot do.
					new StaminaSettings(0, 0, 0, 0, 0, 0),
					new DodgeSettings(0, 0, 0, 0),
					new BlockSettings(0, 0, 0, 0, 0, 0, 0, 0),
					new DownedSettings(0, 0, 0, 0, 0, 0, 0))),

	/**
	 * The GeckoLib-drawn demonic skeleton.
	 *
	 * <p>Holds {@link CombatVerb#PHASED_MELEE} on the same terms as
	 * {@link #ZOMBIE_HUMAN}: {@link CombatPhaseAnimations} can show the wind-up, so
	 * committing to one is honest. Its telegraph is the first half of the animator's
	 * {@code attack} clip, split out and held — see {@code DemonSkeletonEntity}.
	 *
	 * <p><strong>Every number here is the zombie-human's, deliberately.</strong> What
	 * this slice proves is that the mob draws, animates and joins the combat system;
	 * making a demonic skeleton feel unlike a zombie is the behaviour slice that follows,
	 * and it happens on this tab rather than in this file.
	 *
	 * <p>It extends {@code Monster} rather than {@code Skeleton}, so no entry above or
	 * below can claim it and the declaration order is free. A {@code Skeleton} subclass
	 * would also have been a bow user, which never reaches the melee hook at all.
	 */
	DEMON_SKELETON("demon_skeleton", DemonSkeletonEntity.class,
			EnumSet.of(CombatVerb.PHASED_MELEE, CombatVerb.RANDOM_STATS),
			new ActorSettings(
					// A wide window to match the long wind-up, for the reason recorded on
					// ZOMBIE_HUMAN: a two tick window against a deliberate, readable raise
					// means the target has always stepped out of reach by the time it
					// opens, and the mob telegraphs, commits and whiffs forever.
					10, 6, 10, 4,
					new StatRange(0.8, 1.4),
					new StatRange(0.8, 1.4),
					new StatRange(0.9, 1.2),
					new StatRange(0.0, 6.0),
					// Mobs have no stamina, deliberately — a pool gives them a second,
					// invisible way to fail. Zeroed rather than only un-verbed, here and
					// below, so the config screen tells the truth about what this mob can
					// and cannot do.
					new StaminaSettings(0, 0, 0, 0, 0, 0),
					new DodgeSettings(0, 0, 0, 0),
					new BlockSettings(0, 0, 0, 0, 0, 0, 0, 0),
					new DownedSettings(0, 0, 0, 0, 0, 0, 0))),

	/**
	 * Covers the whole zombie family — husk, drowned, zombie villager — since they
	 * share Zombie's melee behaviour.
	 */
	ZOMBIE("zombie", Zombie.class,
			EnumSet.of(CombatVerb.PHASED_MELEE, CombatVerb.RANDOM_STATS),
			new ActorSettings(
					5, CombatConstants.DEFAULT_ACTIVE_TICKS, 10, 4,
					new StatRange(0.8, 1.4),
					new StatRange(0.8, 1.4),
					new StatRange(0.9, 1.2),
					new StatRange(0.0, 6.0),
					// Mobs have no stamina, deliberately. A pool gave them a second,
					// invisible way to fail: one that whiffed a few swings ran itself
					// out and then stood doing nothing, which is indistinguishable from
					// a broken attack and is how it got reported. A player can see their
					// own bar and read the pause; nobody can see a mob's. A mob's pacing
					// is its phase timings, and that is enough. Zeroed rather than only
					// un-verbed so the config screen tells the truth, as dodge and block
					// already do.
					new StaminaSettings(0, 0, 0, 0, 0, 0),
					// No dodge: the zombie lacks the verb, and would also need a goal
					// to decide when to use one. Zeroed rather than left at the
					// player's numbers so the config screen shows the truth.
					new DodgeSettings(0, 0, 0, 0),
					// No guard either, and zeroed for the same reason: the arc is the off
					// switch, so nothing the zombie does can ever fall inside it.
					new BlockSettings(0, 0, 0, 0, 0, 0, 0, 0),
					new DownedSettings(0, 0, 0, 0, 0, 0, 0)));

	private final String id;
	private final Class<? extends LivingEntity> type;
	private final Set<CombatVerb> verbs;
	private final ActorSettings defaults;
	private final Codec<ActorSettings> settingsCodec;

	CombatActor(String id, Class<? extends LivingEntity> type, Set<CombatVerb> verbs,
			ActorSettings defaults) {
		this.id = id;
		this.type = type;

		// Copied rather than stored, so the caller's set cannot later change what an
		// actor is capable of. EnumSet.copyOf needs a non-empty source to infer the
		// element type, hence the explicit empty case.
		this.verbs = verbs.isEmpty() ? EnumSet.noneOf(CombatVerb.class) : EnumSet.copyOf(verbs);
		this.defaults = defaults;
		this.settingsCodec = ActorSettings.codec(defaults);
	}

	/**
	 * @return the actor this entity is tuned as, or null when it should use vanilla
	 *         combat entirely.
	 */
	public static CombatActor forEntity(LivingEntity entity) {
		for (CombatActor actor : values()) {
			if (actor.type.isInstance(entity)) {
				return actor;
			}
		}

		return null;
	}

	public String id() {
		return this.id;
	}

	/** Whether this actor takes part in the given combat capability. */
	public boolean has(CombatVerb verb) {
		return this.verbs.contains(verb);
	}

	/** @see CombatVerb#PHASED_MELEE */
	public boolean usesMeleeGoal() {
		return has(CombatVerb.PHASED_MELEE);
	}

	/** @see CombatVerb#RANDOM_STATS */
	public boolean usesRandomStats() {
		return has(CombatVerb.RANDOM_STATS);
	}

	/** @see CombatVerb#DODGE */
	public boolean usesDodge() {
		return has(CombatVerb.DODGE);
	}

	/** @see CombatVerb#BLOCK */
	public boolean usesBlock() {
		return has(CombatVerb.BLOCK);
	}

	/** @see CombatVerb#DOWNED */
	public boolean usesDowned() {
		return has(CombatVerb.DOWNED);
	}

	public ActorSettings defaults() {
		return this.defaults;
	}

	/** This actor's settings codec, whose field fallbacks are its own defaults. */
	public Codec<ActorSettings> settingsCodec() {
		return this.settingsCodec;
	}

	public Component displayName() {
		return Component.translatable("combat.grandcraft.actor." + this.id);
	}
}

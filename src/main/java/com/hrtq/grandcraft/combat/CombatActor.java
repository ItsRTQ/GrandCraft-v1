package com.hrtq.grandcraft.combat;

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
	 * The player gets stagger, stamina and an attack lockout, but no wind-up: the
	 * client plays swing and crit visuals at click time and there is no animation
	 * layer to hide a server-side delay behind.
	 */
	PLAYER("player", Player.class,
			EnumSet.of(CombatVerb.STAMINA, CombatVerb.DODGE, CombatVerb.BLOCK),
			new ActorSettings(
					0, 3, 4,
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
					new BlockSettings(3, 3, 10, 250, 2, 135, 40, 60))),

	/**
	 * Covers the whole zombie family — husk, drowned, zombie villager — since they
	 * share Zombie's melee behaviour.
	 */
	ZOMBIE("zombie", Zombie.class,
			EnumSet.of(CombatVerb.PHASED_MELEE, CombatVerb.RANDOM_STATS, CombatVerb.STAMINA),
			new ActorSettings(
					5, 10, 4,
					new StatRange(0.8, 1.4),
					new StatRange(0.8, 1.4),
					new StatRange(0.9, 1.2),
					new StatRange(0.0, 6.0),
					// About six swings before it has to break off. That pause is the
					// point: it is an opening the player can read and punish, which is
					// what a mob needs to be threatening rather than merely high-damage.
					// Sprint and jump cost nothing, so pathfinding cannot exhaust a mob
					// that never chose to spend.
					new StaminaSettings(100, 12, 12, 15, 0, 0),
					// No dodge: the zombie lacks the verb, and would also need a goal
					// to decide when to use one. Zeroed rather than left at the
					// player's numbers so the config screen shows the truth.
					new DodgeSettings(0, 0, 0, 0),
					// No guard either, and zeroed for the same reason: the arc is the off
					// switch, so nothing the zombie does can ever fall inside it.
					new BlockSettings(0, 0, 0, 0, 0, 0, 0, 0)));

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

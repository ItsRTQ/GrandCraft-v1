package com.hrtq.grandcraft.combat;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.world.entity.LivingEntity;

/**
 * Resolves an entity to the live combat profile for its {@link CombatActor}.
 *
 * <p>The combat engine is generic over {@link LivingEntity}; which entities take
 * part is decided entirely by {@link CombatActor}, so widening coverage means
 * adding an enum entry, not writing new combat code. Anything without an actor
 * keeps vanilla behaviour untouched.
 *
 * <p>Profiles are cached and rebuilt when {@link CombatTuning} hands back a
 * different settings object, so editing values in game takes effect on the next
 * attack without any invalidation call. Server-thread only, like the rest of
 * combat.
 */
public final class CombatProfiles {
	private static final Map<CombatActor, CombatProfile> PROFILES = new EnumMap<>(CombatActor.class);

	private static CombatSettings builtFrom;

	private CombatProfiles() {
	}

	/**
	 * @return the profile for this entity, or null when it should use vanilla combat.
	 */
	public static CombatProfile forEntity(LivingEntity entity) {
		CombatActor actor = CombatActor.forEntity(entity);

		if (actor == null) {
			return null;
		}

		rebuildIfStale();
		return PROFILES.get(actor);
	}

	public static boolean isCombatant(LivingEntity entity) {
		return CombatActor.forEntity(entity) != null;
	}

	private static void rebuildIfStale() {
		CombatSettings settings = CombatTuning.current();

		// Identity check: settings are immutable and swapped wholesale.
		if (settings == builtFrom) {
			return;
		}

		builtFrom = settings;

		for (CombatActor actor : CombatActor.values()) {
			ActorSettings values = settings.forActor(actor);

			// An actor whose melee does not run through MeleeAttackGoal still stores a
			// startup value, but nothing reads it — its damage lands on vanilla
			// timing and recovery serves purely as an attack lockout. Forcing zero
			// here keeps the runtime honest with the config screen, which hides that
			// slider for the same reason.
			//
			// An actor that fights with weapons is the exception, and since 2026-08-07 the
			// reason the value exists: it is that actor's GLOBAL wind-up, the one number
			// pacing every swing whatever is in its hand. It reaches the swing through
			// CombatController.weaponProfile rather than through this profile's melee
			// timings, but it is stored and clamped here like any other phase length.
			int startup = actor.usesMeleeGoal() || actor.has(CombatVerb.WEAPONS)
					? values.startupTicks()
					: 0;
			AttackProfile melee =
					new AttackProfile(startup, values.activeTicks(), values.recoveryTicks());

			// An actor that does not roll reads each band as a fixed value, so the
			// same profile shape serves both without a second schema.
			// An actor without the dodge or block verb keeps whatever is configured but
			// never reaches it, exactly as with stamina — usesDodge() and usesBlock()
			// each combine the two halves.
			PROFILES.put(actor, new CombatProfile(actor, melee, StaggerProfile.from(values),
					values.stamina(), values.dodge(), values.block(), values.downed(),
					values.health(), values.damage(), values.speed(), values.defence()));
		}
	}
}

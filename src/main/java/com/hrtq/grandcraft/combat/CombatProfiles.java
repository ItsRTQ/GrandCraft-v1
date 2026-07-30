package com.hrtq.grandcraft.combat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;

/**
 * Decides which actors opt into GrandCraft combat.
 *
 * <p>This is the only place that names a concrete entity type. The combat engine
 * itself is generic over {@link LivingEntity}; widening coverage means adding a
 * branch here, not writing new combat code. Anything without a profile keeps
 * vanilla behaviour untouched.
 *
 * <p>Profiles are cached and rebuilt when {@link CombatTuning} hands back a
 * different settings object, so editing values in game takes effect on the next
 * attack without any invalidation call. Server-thread only, like the rest of
 * combat.
 */
public final class CombatProfiles {
	private static CombatSettings builtFrom;
	private static CombatProfile zombie;
	private static CombatProfile player;

	private CombatProfiles() {
	}

	/**
	 * @return the profile for this entity, or null when it should use vanilla combat.
	 */
	public static CombatProfile forEntity(LivingEntity entity) {
		// Covers the zombie family (husk, drowned, zombie villager) since they
		// share Zombie's melee behaviour.
		if (entity instanceof Zombie) {
			rebuildIfStale();
			return zombie;
		}

		if (entity instanceof Player) {
			rebuildIfStale();
			return player;
		}

		return null;
	}

	public static boolean isCombatant(LivingEntity entity) {
		return forEntity(entity) != null;
	}

	private static void rebuildIfStale() {
		CombatSettings settings = CombatTuning.current();

		// Identity check: settings are immutable and swapped wholesale.
		if (settings == builtFrom) {
			return;
		}

		builtFrom = settings;
		zombie = new CombatProfile(new AttackProfile(
				settings.zombieStartupTicks(),
				settings.zombieActiveTicks(),
				settings.zombieRecoveryTicks()));

		// Phase 1 does not delay player damage, so the player has no startup or
		// meaningful active window — recovery is used purely as an attack lockout.
		player = new CombatProfile(new AttackProfile(0, 1, settings.playerRecoveryTicks()));
	}
}

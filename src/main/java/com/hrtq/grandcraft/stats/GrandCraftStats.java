package com.hrtq.grandcraft.stats;

import com.hrtq.grandcraft.combat.GrandCraftCombat;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;

/**
 * Wires the stat system into the game.
 *
 * <p>Most of the stat call sites are already somewhere meaningful — the join hook
 * lives with the settings broadcast, and a class change writes stats where it writes
 * the class. This holds the one listener that has nowhere else to be.
 */
public final class GrandCraftStats {
	private GrandCraftStats() {
	}

	public static void register() {
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			// The class AND the spent points are read off the OLD player, deliberately.
			// Fabric copies copyOnDeath attachments from its own listener on this same
			// event, so the new player may not have either of them yet — and reading
			// them too early does not just skip the update, it overwrites good base
			// values with a fresh peasant's. For the class that means a reduced
			// character until the next login; for spent points it would mean deleting
			// everything the player has earned.
			// See PlayerStats.applyBaselines(ServerPlayer, PlayerClass, StatBlock).
			PlayerStats.applyBaselines(newPlayer,
					PlayerStats.classOf(oldPlayer), PlayerStats.spentOf(oldPlayer));

			// The new player is a new entity with no modifiers on it. Forcing the pass
			// now rather than waiting for its first tick is what lets the health top-up
			// below read a maximum that already includes Constitution.
			GrandCraftCombat.controllerOf(newPlayer).refreshModifiers(newPlayer);

			if (!alive) {
				// On a real death restoreFrom sets health to the maximum — but it does so
				// before any of our modifiers exist on the new entity, so a high
				// Constitution character would respawn on vanilla's 20 while their real
				// maximum is higher, i.e. already wounded. Same fix, same reason, as the
				// top-up a freshly rolled mob gets at spawn.
				newPlayer.setHealth(newPlayer.getMaxHealth());
			}
		});
	}
}

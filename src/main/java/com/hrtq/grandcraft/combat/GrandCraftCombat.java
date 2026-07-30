package com.hrtq.grandcraft.combat;

import com.hrtq.grandcraft.player.GrandCraftAttachments;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.world.entity.LivingEntity;

/**
 * Wires the combat system into the game.
 *
 * <p>Stagger needs no mixin: {@link ServerLivingEntityEvents#AFTER_DAMAGE} is
 * server-only and fires after vanilla damage has been resolved, which is exactly
 * the point at which a hit becomes eligible to stagger.
 */
public final class GrandCraftCombat {
	private GrandCraftCombat() {
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register(
				(entity, source, baseDamageTaken, damageTaken, blocked) -> {
					// The Fabric jar is stripped of parameter names, so which float is
					// the pre-mitigation amount is positional inference only. Requiring
					// both to be positive is correct either way: a fully blocked or
					// absorbed hit leaves the final amount at zero, and a hit that
					// really landed has both above zero. The boolean is deliberately
					// unused for the same reason.
					if (baseDamageTaken <= 0.0F || damageTaken <= 0.0F) {
						return;
					}

					if (entity.isDeadOrDying() || !CombatProfiles.isCombatant(entity)) {
						return;
					}

					controllerOf(entity).applyStagger(entity);
				});

		// Combatants need a controller before their first hit, because knockback
		// resistance has to already be in place when knockback is resolved.
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof LivingEntity living && CombatProfiles.isCombatant(living)) {
				controllerOf(living);
			}
		});
	}

	/**
	 * The actor's controller, created on first use.
	 *
	 * <p>Call this only from paths that are actually starting combat, so entities
	 * that never fight never allocate one.
	 */
	public static CombatController controllerOf(LivingEntity entity) {
		return entity.getAttachedOrCreate(GrandCraftAttachments.COMBAT_CONTROLLER);
	}
}

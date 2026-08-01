package com.hrtq.grandcraft.progression;

import com.hrtq.grandcraft.entity.LifeEssenceOrb;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * Wires Essence Power into the game.
 *
 * <p>One listener, and no mixin: kill attribution is already public API, so a mob's
 * death is enough to decide whether a player earned anything by it.
 */
public final class GrandCraftProgression {
	private GrandCraftProgression() {
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (!(entity.level() instanceof ServerLevel level)) {
				return;
			}

			// Players are not a source of Essence. Vanilla drops experience from them
			// and that is exactly the shape of an exploit here: two accounts, or one
			// player and a respawn loop, would mint progression out of nothing.
			if (entity instanceof Player) {
				return;
			}

			// Vanilla's own two conditions for dropping experience, reused for their
			// reasons rather than copied for their values. The memory time is how
			// vanilla asks "was a player responsible for this?", which keeps mob-on-mob
			// kills, fall damage and suffocation from feeding progression; and an admin
			// who has switched mob drops off has switched this off too.
			if (entity.getLastHurtByPlayerMemoryTime() <= 0) {
				return;
			}

			if (!level.getGameRules().get(GameRules.MOB_DROPS)) {
				return;
			}

			// Rolled per kill rather than per mob type. A weighting that varies by mob —
			// and a larger drop for a boss — is expected later, and this is the line it
			// will replace.
			int value = LevelTuning.current().rollOrbValue(entity.getRandom());

			LifeEssenceOrb.award(level, entity.position(), value);
		});
	}
}

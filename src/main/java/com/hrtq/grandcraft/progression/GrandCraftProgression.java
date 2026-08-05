package com.hrtq.grandcraft.progression;

import com.hrtq.grandcraft.entity.LifeEssenceOrb;
import com.hrtq.grandcraft.skill.SkillMilestones;
import com.hrtq.grandcraft.skill.SkillObjective;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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

			// Skill-line credit goes to whoever the killing blow belongs to. Vanilla makes
			// getEntity the *owner* rather than the projectile, so a bow kill counts and an
			// arrow does not have to be special-cased. Deliberately read from the damage
			// source rather than from the memory time checked above: that answers "was a
			// player involved recently", which is the right question for a drop and the
			// wrong one for crediting a kill to somebody.
			if (source.getEntity() instanceof ServerPlayer killer) {
				SkillMilestones.count(killer, SkillObjective.SLAY, 1);
			}

			// Rolled per kill rather than per mob type. A weighting that varies by mob —
			// and a larger drop for a boss — is expected later, and this is the line it
			// will replace.
			int value = LevelTuning.current().rollOrbValue(entity.getRandom());

			LifeEssenceOrb.award(level, entity.position(), value);
		});
	}
}

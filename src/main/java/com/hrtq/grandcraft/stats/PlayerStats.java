package com.hrtq.grandcraft.stats;

import com.hrtq.grandcraft.player.GrandCraftAttachments;
import com.hrtq.grandcraft.player.PlayerClass;
import com.hrtq.grandcraft.progression.EssenceAwards;
import com.hrtq.grandcraft.progression.EssenceProgress;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;

/**
 * Writes a player's stats from their class and the points they have spent.
 *
 * <p>Recomputed rather than stored: the class table and the spent-point record are
 * the sources of truth, so re-deriving on join, on any class change and after every
 * spend means an edit to that table reaches every existing character without a
 * migration. Attribute base values do persist and are copied across a respawn by
 * vanilla, but nothing here relies on that — running again is always safe and always
 * right.
 *
 * <h2>The levelling seam, now occupied</h2>
 * Each stat is written as {@code baseline + spent}. That sum was written from the
 * start against the day points existed; this is that day, and the only thing that
 * changed is where {@code spent} comes from.
 */
public final class PlayerStats {
	private PlayerStats() {
	}

	/**
	 * Sets every stat's base value from the class and spent points the player
	 * currently holds.
	 *
	 * <p>Server side only. Writing a base value on a client-side player would be
	 * overwritten by the next attribute sync anyway, and would hide a broken server
	 * path behind a client that looked correct.
	 *
	 * <p><strong>Not safe during a respawn.</strong> See the three-argument form.
	 */
	public static void applyBaselines(ServerPlayer player) {
		applyBaselines(player, classOf(player), spentOf(player));
	}

	/**
	 * Sets every stat's base value from an explicitly supplied class and spend record.
	 *
	 * <p>This exists because of a respawn ordering trap that is invisible until it
	 * bites. Fabric copies {@code copyOnDeath} attachments from a listener on
	 * {@link net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents#AFTER_RESPAWN}
	 * — the same event this mod listens to. Listeners run in registration order, which
	 * across mods is not something either of us controls, so a respawned player may or
	 * may not have its attachments yet when our listener runs.
	 *
	 * <p>Reading them there and losing the race does not merely skip the update: the
	 * attachments answer with their initialisers — {@code PEASANT} and a record with
	 * nothing spent — and the correct base values that vanilla's {@code restoreFrom}
	 * had already copied across get overwritten with a fresh peasant's. The character
	 * stays reduced until their next login.
	 *
	 * <p><strong>Both arguments are part of that fix.</strong> The class was the first
	 * casualty and cost a runtime round; spent points are the second and would have
	 * cost more, because losing them silently deletes everything the player has
	 * earned. So the respawn path passes the <em>old</em> player's values, which are
	 * never in doubt.
	 */
	public static void applyBaselines(ServerPlayer player, PlayerClass playerClass, StatBlock spent) {
		StatBlock baseline = playerClass.baseStats();

		for (CharacterStat stat : CharacterStat.values()) {
			set(player, stat, baseline.get(stat) + spent.get(stat));
		}
	}

	public static PlayerClass classOf(ServerPlayer player) {
		return player.getAttachedOrElse(GrandCraftAttachments.PLAYER_CLASS, PlayerClass.PEASANT);
	}

	/** The points this player has committed to each stat. */
	public static StatBlock spentOf(ServerPlayer player) {
		return progressOf(player).spentStats();
	}

	/** Delegated rather than re-read, so there is one reader of the attachment. */
	public static EssenceProgress progressOf(ServerPlayer player) {
		return EssenceAwards.progressOf(player);
	}

	/**
	 * The value a stat would reach with one more point in it, which is what the spend
	 * receiver checks against the attribute's own ceiling.
	 *
	 * <p>The ceiling matters even though it is a long way off: a
	 * {@code RangedAttribute} clamps silently, so a point spent past it would vanish
	 * with the player having no way to tell.
	 */
	public static double valueAfterSpend(ServerPlayer player, CharacterStat stat) {
		return classOf(player).baseStats().get(stat) + spentOf(player).get(stat) + 1;
	}

	private static void set(ServerPlayer player, CharacterStat stat, int value) {
		AttributeInstance instance = player.getAttribute(stat.attribute());

		if (instance != null) {
			instance.setBaseValue(value);
		}
	}
}

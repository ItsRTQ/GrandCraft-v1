package com.hrtq.grandcraft.progression;

import com.hrtq.grandcraft.GrandCraft;
import com.hrtq.grandcraft.player.GrandCraftAttachments;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/**
 * The one path by which Essence Power is gained, and the only place a level is
 * awarded.
 *
 * <p>Kept to a single entry point deliberately: levelling grants points, and points
 * feed {@code PlayerStats.spent()}, so anything that could grant a level from
 * somewhere else would be a second place for the two to drift apart.
 *
 * <p>Everything here is server side. The client is told through the attachment's own
 * sync and never computes a level for itself.
 */
public final class EssenceAwards {
	/**
	 * How the level-up announcement is timed, in ticks: fade in, hold, fade out.
	 * Matches the class-selection announcement so the two read as the same voice.
	 */
	private static final int FADE_IN_TICKS = 10;
	private static final int HOLD_TICKS = 70;
	private static final int FADE_OUT_TICKS = 20;

	private static final float LEVEL_UP_PITCH = 1.0F;

	private EssenceAwards() {
	}

	/** This player's Essence Power, defaulting to a character who has earned none. */
	public static EssenceProgress progressOf(Player player) {
		return player.getAttachedOrElse(GrandCraftAttachments.ESSENCE_PROGRESS, EssenceProgress.NONE);
	}

	/**
	 * Wipes a character's progression back to nothing: level, banked Essence, unspent
	 * points and everything already committed to a stat.
	 *
	 * <p>Used by {@code /grandcraft reclass}, which unmakes the character. Stats are
	 * derived from class plus spent points, so a reclass that reset only the class
	 * would leave a fresh peasant carrying the stat points of the character they used
	 * to be — which is worse than either extreme, since those points were bought with
	 * a class they no longer have.
	 *
	 * <p><strong>Does not rewrite the attributes itself.</strong> The caller does that,
	 * because it is also changing the class and one pass over the stats should cover
	 * both — and doing it here would write baselines from a class the caller has not
	 * finished changing.
	 */
	public static void reset(ServerPlayer player) {
		player.setAttached(GrandCraftAttachments.ESSENCE_PROGRESS, EssenceProgress.NONE);
	}

	/**
	 * Adds Essence and grants any levels it paid for.
	 *
	 * <p>A single pickup can cross more than one level boundary — not normally, since
	 * an orb is worth at most three, but certainly after an admin lowers the costs
	 * under a character who had banked progress. The loop handles that rather than
	 * granting one level and silently keeping the rest of the Essence.
	 *
	 * <p>Terminates because {@link LevelSettings#costOf} has a floor of one, so each
	 * pass removes at least one Essence from a finite pool. That floor is a
	 * termination guarantee, not a tuning choice.
	 */
	public static void award(ServerPlayer player, int amount) {
		if (amount <= 0) {
			return;
		}

		LevelSettings settings = LevelTuning.current();
		EssenceProgress before = progressOf(player);

		int essence = before.essence() + amount;
		int level = before.level();
		int statPoints = before.statPoints();
		int poolPoints = before.poolPoints();
		int levelsGained = 0;

		while (essence >= settings.costOf(level + 1)) {
			essence -= settings.costOf(level + 1);
			level++;
			levelsGained++;

			statPoints += settings.statPointsPerLevel();
			poolPoints += settings.poolPointsFor(level);
		}

		// Spent points are carried through untouched: gaining a level adds to the
		// unspent pile and never disturbs what has already been committed.
		player.setAttached(GrandCraftAttachments.ESSENCE_PROGRESS,
				new EssenceProgress(essence, level, statPoints, poolPoints,
						before.spentStats(), before.spentPools()));

		if (levelsGained > 0) {
			announce(player, level,
					statPoints - before.statPoints(),
					poolPoints - before.poolPoints());
		}
	}

	/**
	 * Tells the player they levelled, and what they gained by it.
	 *
	 * <p>Shipped in the same slice as the mechanic on purpose. Essence Power has no
	 * HUD bar by design, so without this a level would be a silent change to a number
	 * on a screen the player may not have open — and the tuning notes are explicit
	 * that a state nobody can perceive gets reported as a broken one.
	 *
	 * <p>The subtitle names the points rather than only the level, because the points
	 * are the part that asks the player to do something.
	 */
	private static void announce(ServerPlayer player, int level, int statPoints, int poolPoints) {
		GrandCraft.LOGGER.info("{} reached Essence Power level {}",
				player.getGameProfile().name(), level);

		// Deliberately not vanilla's level-up chime. Vanilla experience is left intact
		// by this feature, so its own sound still plays for its own levels — borrowing
		// it would leave a player unable to tell which of the two had just moved.
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, LEVEL_UP_PITCH);

		Component subtitle = poolPoints > 0
				? Component.translatable("screen.grandcraft.essence.level_up.points_milestone",
						statPoints, poolPoints)
				: Component.translatable("screen.grandcraft.essence.level_up.points", statPoints);

		player.connection.send(new ClientboundSetTitlesAnimationPacket(
				FADE_IN_TICKS, HOLD_TICKS, FADE_OUT_TICKS));
		player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
		player.connection.send(new ClientboundSetTitleTextPacket(
				Component.translatable("screen.grandcraft.essence.level_up", level)));
	}
}

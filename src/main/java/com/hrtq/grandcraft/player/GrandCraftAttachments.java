package com.hrtq.grandcraft.player;

import com.hrtq.grandcraft.GrandCraft;
import com.hrtq.grandcraft.combat.CombatController;
import com.hrtq.grandcraft.combat.RolledStats;
import com.hrtq.grandcraft.progression.EssenceProgress;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

public final class GrandCraftAttachments {
	public static final AttachmentType<PlayerClass> PLAYER_CLASS = AttachmentRegistry.<PlayerClass>builder()
			.initializer(() -> PlayerClass.PEASANT)
			.persistent(PlayerClass.CODEC)
			.copyOnDeath()
			.syncWith(PlayerClass.STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
			.buildAndRegister(GrandCraft.id("player_class"));

	/**
	 * Essence Power: level, progress towards the next one, and unspent points.
	 *
	 * <p>Persistent and {@code copyOnDeath}, because levels are the character rather
	 * than the life — dying must not cost progression. Synced to the owner only,
	 * since the character sheet is the only thing that draws it.
	 *
	 * <p><strong>Anything reading this during a respawn must be careful.</strong>
	 * Fabric copies {@code copyOnDeath} attachments from its own listener on
	 * {@code ServerPlayerEvents.AFTER_RESPAWN} — the same event this mod uses — so on
	 * the new player it may not have arrived yet. Reading it too early does not merely
	 * skip an update: it answers with the default and can overwrite real progress.
	 * {@code PlayerStats} takes the spent points as an argument for exactly this
	 * reason; see the note there.
	 */
	public static final AttachmentType<EssenceProgress> ESSENCE_PROGRESS =
			AttachmentRegistry.<EssenceProgress>builder()
					.initializer(() -> EssenceProgress.NONE)
					.persistent(EssenceProgress.CODEC)
					.copyOnDeath()
					.syncWith(EssenceProgress.STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
					.buildAndRegister(GrandCraft.id("essence_progress"));

	/**
	 * Live combat state. Neither persistent nor synced: it is transient by nature
	 * and Phase 1 has no client consumer. Adding {@code syncWith} later is what
	 * would feed a client animation layer.
	 */
	public static final AttachmentType<CombatController> COMBAT_CONTROLLER =
			AttachmentRegistry.createDefaulted(GrandCraft.id("combat"), CombatController::new);

	/**
	 * The stats this individual rolled on first spawn.
	 *
	 * <p>Persistent, so a mob that rolled tough stays tough across a chunk unload or
	 * a restart. Deliberately has no initializer: "not yet rolled" has to be
	 * distinguishable from "rolled and happened to get the defaults", and a defaulted
	 * attachment would answer every read with a value.
	 */
	public static final AttachmentType<RolledStats> ROLLED_STATS = AttachmentRegistry.<RolledStats>builder()
			.persistent(RolledStats.CODEC)
			.buildAndRegister(GrandCraft.id("rolled_stats"));

	private GrandCraftAttachments() {
	}

	public static void register() {
		// Attachment types are registered by the static initializer above.
	}
}

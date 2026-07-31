package com.hrtq.grandcraft.player;

import com.hrtq.grandcraft.GrandCraft;
import com.hrtq.grandcraft.combat.CombatController;
import com.hrtq.grandcraft.combat.RolledStats;
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

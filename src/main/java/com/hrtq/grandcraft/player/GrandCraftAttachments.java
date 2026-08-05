package com.hrtq.grandcraft.player;

import com.hrtq.grandcraft.GrandCraft;
import com.hrtq.grandcraft.combat.CombatController;
import com.hrtq.grandcraft.combat.RolledStats;
import com.hrtq.grandcraft.progression.EssenceProgress;
import com.hrtq.grandcraft.skill.SkillLoadout;
import com.hrtq.grandcraft.skill.SkillProgress;
import com.hrtq.grandcraft.stats.ManaState;
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
	 * How far this character has got towards their skill-line milestones — one counter
	 * per {@code SkillObjective}, and nothing else.
	 *
	 * <p>Persistent and {@code copyOnDeath} for the same reason {@link #ESSENCE_PROGRESS}
	 * is: what you have done is the character, not the life. Synced to the owner only,
	 * since the character sheet is the only thing that draws it — and it must be synced,
	 * because the sheet works out for itself which nodes are open rather than being told
	 * (see {@code SkillUnlocks}).
	 *
	 * <p><strong>Nothing on the respawn path may read or write this.</strong> It is
	 * {@code copyOnDeath}, so it is subject to THE RESPAWN TRAP exactly as
	 * {@link #ESSENCE_PROGRESS} is — Fabric copies it from its own listener on
	 * {@code ServerPlayerEvents.AFTER_RESPAWN}, the same event this mod subscribes to,
	 * and cross-mod listener order is nobody's to control. Today it is safe because
	 * nothing derives an attribute from it and nothing touches it during a respawn.
	 * That is a rule to keep, not an accident: a listener added here later would be
	 * reading a value that may not have arrived, and writing one would destroy it.
	 */
	public static final AttachmentType<SkillProgress> SKILL_PROGRESS =
			AttachmentRegistry.<SkillProgress>builder()
					.initializer(() -> SkillProgress.NONE)
					.persistent(SkillProgress.CODEC)
					.copyOnDeath()
					.syncWith(SkillProgress.STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
					.buildAndRegister(GrandCraft.id("skill_progress"));

	/**
	 * The four things this character has equipped — three abilities and an ultimate.
	 *
	 * <p><strong>Stored, unlike unlocking.</strong> Which nodes are open is derived from
	 * level and counters and deliberately never written down; which four the player
	 * chose is implied by nothing, so it has to be. Same persistence rules as the two
	 * above — it is the character, not the life.
	 *
	 * <p>Synced to the owner because the character sheet draws which slot each node sits
	 * in, and later the HUD will draw the four slots themselves.
	 *
	 * <p>Subject to THE RESPAWN TRAP exactly as {@link #SKILL_PROGRESS} is, and clear of
	 * it for the same reason: nothing on the respawn path reads or writes it. Keep it
	 * that way.
	 */
	public static final AttachmentType<SkillLoadout> SKILL_LOADOUT =
			AttachmentRegistry.<SkillLoadout>builder()
					.initializer(() -> SkillLoadout.EMPTY)
					.persistent(SkillLoadout.CODEC)
					.copyOnDeath()
					.syncWith(SkillLoadout.STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
					.buildAndRegister(GrandCraft.id("skill_loadout"));

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

	/**
	 * Mana, and the delay before it starts coming back.
	 *
	 * <p>Persistent because mana is now spendable and does not recover for everyone.
	 * Logging out empty and back in full would be a free refill, and a considerably
	 * better one for a character below the Arcane threshold, who otherwise cannot
	 * refill at all.
	 *
	 * <p><strong>Deliberately not {@code copyOnDeath}.</strong> Dying restores mana
	 * exactly as it restores health and stamina, which is the intended rule — and it
	 * is also what keeps this attachment clear of THE RESPAWN TRAP. Fabric copies only
	 * {@code copyOnDeath} attachments, from its own listener on the same
	 * {@code ServerPlayerEvents.AFTER_RESPAWN} this mod subscribes to, and cross-mod
	 * listener order is nobody's to control. With nothing to copy there is no race to
	 * lose. <em>Nothing on the respawn path may read or write this</em> — see the note
	 * on {@code CombatController.syncStatEffects}.
	 *
	 * <p><strong>Deliberately no initializer.</strong> "Never filled" has to stay
	 * distinguishable from "filled and spent to nothing", because the pool starts full
	 * and its size comes from settings no constructor can see. Absence means fill to
	 * maximum; a defaulted attachment would answer every read with zero and hand every
	 * new character an empty pool. Same reasoning as {@link #ROLLED_STATS}.
	 *
	 * <p><strong>Deliberately not synced.</strong> {@code ManaPayload} is the one
	 * channel to the client and it carries the derived ceiling and the effective
	 * recovery rate, neither of which is in this record. A second channel would be a
	 * second answer.
	 */
	public static final AttachmentType<ManaState> MANA = AttachmentRegistry.<ManaState>builder()
			.persistent(ManaState.CODEC)
			.buildAndRegister(GrandCraft.id("mana"));

	private GrandCraftAttachments() {
	}

	public static void register() {
		// Attachment types are registered by the static initializer above.
	}
}

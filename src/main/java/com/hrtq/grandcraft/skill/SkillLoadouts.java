package com.hrtq.grandcraft.skill;

import com.hrtq.grandcraft.player.GrandCraftAttachments;
import com.hrtq.grandcraft.player.PlayerClass;
import com.hrtq.grandcraft.progression.EssenceAwards;
import com.hrtq.grandcraft.progression.LevelSettings;
import com.hrtq.grandcraft.progression.LevelTuning;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Equipping: the rules, and the one place a loadout is written.
 *
 * <h2>The server decides the slot, not the client</h2>
 * A click sends only <em>which node</em> was clicked. Everything else — whether it is
 * unlocked, whether it is an ability or an ultimate, which slot it lands in, and
 * whether it was already equipped and should come off — is decided here.
 *
 * <p>That is deliberate. A packet naming a slot would need every one of those checks
 * anyway, plus a check that the named slot matched the node's kind; a packet naming
 * only a node has one thing to validate and cannot express an illegal request in the
 * first place.
 *
 * <p>The cost is that a player cannot say "put this one on key 2 specifically" — an
 * ability takes the lowest free slot. They can still reach any arrangement by taking
 * abilities off and putting them back in the order they want, and a drag-to-slot
 * interface can be added later without changing what is stored.
 *
 * <h2>The rules, in full</h2>
 * <ul>
 *   <li>Only an <strong>unlocked</strong> node can be equipped, re-checked here and
 *       never trusted from the client — the sheet only ever draws what it was told.</li>
 *   <li>A <strong>line node</strong> goes in the lowest free ability slot. With all
 *       three full, the request is refused rather than silently replacing one.</li>
 *   <li>An <strong>ultimate</strong> goes in the ultimate slot and replaces whatever
 *       was there, because there is only one and swapping is the only thing the player
 *       could mean.</li>
 *   <li>Clicking something already equipped <strong>takes it off</strong>.</li>
 *   <li>The <strong>root</strong> is never equipped. It is the class passive and is
 *       always in force, which is exactly why it does not occupy one of the four.</li>
 * </ul>
 */
public final class SkillLoadouts {
	private SkillLoadouts() {
	}

	/** This character's equipped set. Readable on both sides — the attachment syncs. */
	public static SkillLoadout loadoutOf(Player player) {
		return player.getAttachedOrElse(GrandCraftAttachments.SKILL_LOADOUT, SkillLoadout.EMPTY);
	}

	/**
	 * The node in a slot, resolved against this character's own tree, or {@code null}
	 * for an empty slot.
	 *
	 * <p>Also null when the stored path belongs to a class this character no longer is,
	 * which is the fail-safe {@link SkillLoadout} describes — worth knowing because it
	 * means a stale loadout reads as empty rather than as somebody else's ability.
	 */
	public static SkillNode equipped(Player player, int slot) {
		String path = loadoutOf(player).get(slot);

		return path.isEmpty() ? null : treeOf(player).nodeByPath(path);
	}

	public static SkillTree treeOf(Player player) {
		return SkillTree.of(player.getAttachedOrElse(
				GrandCraftAttachments.PLAYER_CLASS, PlayerClass.PEASANT));
	}

	/**
	 * Equips the node if it is not equipped, or takes it off if it is.
	 *
	 * @return the reason it was refused, or {@code null} if the loadout changed
	 */
	public static Refusal toggle(ServerPlayer player, String path) {
		SkillTree tree = treeOf(player);
		SkillNode node = tree.nodeByPath(path);

		if (node == null || node.isRoot()) {
			return Refusal.NO_SUCH_NODE;
		}

		SkillLoadout loadout = loadoutOf(player);
		int held = loadout.slotOf(path);

		if (held >= 0) {
			set(player, loadout.with(held, ""));
			return null;
		}

		// Only re-checked when equipping. Taking something off after its gate moved must
		// always be allowed, or an admin lowering a level would leave a player holding an
		// ability they can neither use nor remove.
		if (!isUnlocked(player, node)) {
			return Refusal.LOCKED;
		}

		if (node.isUltimate()) {
			set(player, loadout.with(SkillLoadout.ULTIMATE_SLOT, path));
			return null;
		}

		int slot = loadout.firstFreeAbilitySlot();

		if (slot < 0) {
			return Refusal.NO_FREE_SLOT;
		}

		set(player, loadout.with(slot, path));
		return null;
	}

	/** Why a toggle did nothing, for the caller to say so in a way the player can read. */
	public enum Refusal {
		/** The path named nothing in this character's tree, or named the root. */
		NO_SUCH_NODE,

		/** Its gates are not met. */
		LOCKED,

		/** All three ability slots are taken. */
		NO_FREE_SLOT
	}

	public static boolean isUnlocked(ServerPlayer player, SkillNode node) {
		LevelSettings settings = LevelTuning.current();

		return SkillUnlocks.isUnlocked(node,
				EssenceAwards.progressOf(player).level(),
				SkillMilestones.progressOf(player),
				settings);
	}

	private static void set(ServerPlayer player, SkillLoadout loadout) {
		player.setAttached(GrandCraftAttachments.SKILL_LOADOUT, loadout);
	}

	/**
	 * Empties every slot.
	 *
	 * <p>Used by {@code /grandcraft reclass}, beside the progression and milestone
	 * wipes. The stored paths name the old class's nodes, so they would resolve to
	 * nothing anyway — clearing them is what stops a save file carrying a record of a
	 * character who no longer exists.
	 */
	public static void reset(ServerPlayer player) {
		set(player, SkillLoadout.EMPTY);
	}
}

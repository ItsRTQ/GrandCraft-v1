package com.hrtq.grandcraft.skill;

import com.hrtq.grandcraft.player.GrandCraftAttachments;
import net.minecraft.server.level.ServerPlayer;

/**
 * Which milestone sits on which node, and the one place a counter is written.
 *
 * <p><strong>This is the file to edit when a milestone asks for the wrong thing or
 * the wrong amount.</strong> Nothing outside it decides either.
 *
 * <h2>The table is generated, and that is temporary</h2>
 * The real forty-eight — twelve nodes across four classes — are not designed, because
 * they belong with the abilities they gate and those are not designed either. Until
 * then the objective comes from the <em>line</em> and the target from the <em>tier</em>,
 * exactly as {@code SkillTree} generates the shape it draws.
 *
 * <p>That is enough to be honest about the mechanic rather than to fake it: the three
 * lines really do ask for three different things, so a Warrior working down line A is
 * doing something visibly different from one working down line B. What is missing is
 * per-class flavour, not the system.
 *
 * <p>When the real list arrives, {@link #forNode} is the method that reads a table
 * instead of computing, and nothing outside this file changes.
 *
 * <h2>On the numbers</h2>
 * Worked against {@code tuning.md}'s progression table rather than picked: at the
 * shipped cost curve (~1.18 Essence per kill, cost {@code 10 + 5×(level−1)}) level 5
 * is about sixty kills, level 25 about fifteen hundred. So the tier-1 targets are set
 * to land near their level gate, and <strong>from tier 2 up the level is the binding
 * gate by an order of magnitude</strong> — the milestone will already be done when the
 * level arrives.
 *
 * <p>That is a statement about the cost curve, not about these numbers, and both
 * levers are reachable without a rebuild: the level gates are on
 * {@code /grandcraft config levels} and so is the curve. Judge them by playing.
 */
public final class SkillMilestones {

	/**
	 * Which objective each line asks for, indexed by line.
	 *
	 * <p>One per line rather than one per node, which is what makes a line a subclass
	 * — following A down is a commitment to doing the thing A is about.
	 */
	private static final SkillObjective[] OBJECTIVE_FOR_LINE = {
			SkillObjective.SLAY,
			SkillObjective.STRIKE,
			SkillObjective.EVADE };

	/**
	 * The target at each tier, per objective, in the same order as
	 * {@link #OBJECTIVE_FOR_LINE}.
	 *
	 * <p>The three rows differ because the three verbs happen at different rates: a
	 * kill is several swings, and a dodge is rarer than either. Roughly, one row of
	 * this table should take about as long as its neighbours.
	 */
	private static final int[][] TARGETS = {
			//  tier 1  tier 2  tier 3  tier 4
			{ 30, 150, 400, 900 },        // SLAY
			{ 120, 600, 1600, 3600 },     // STRIKE
			{ 25, 120, 320, 700 } };      // EVADE

	/**
	 * What the three ultimates ask for, in unlock order.
	 *
	 * <p>All three want {@link SkillObjective#SLAY_WITH_SKILL} — kills made with a
	 * skill-line ability — which is the user's own answer and the right shape for the
	 * thing: an ultimate should be earned by <em>using</em> the tree, not by playing
	 * more of the same game the line nodes already measure.
	 *
	 * <p><strong>Nothing counts that objective yet</strong>, because no ability does
	 * anything to count. Until the first real one ships an ultimate is reachable only
	 * through {@code /grandcraft give milestone slay_with_skill}. See the constant's own
	 * note.
	 */
	private static final int[] ULTIMATE_TARGETS = { 100, 300, 700 };

	private SkillMilestones() {
	}

	/**
	 * The milestone on a node.
	 *
	 * <p>The root has none — it is the class's own passive and is in force from the
	 * moment the class is chosen — so this refuses it rather than inventing one.
	 *
	 * @throws IllegalArgumentException if given the root
	 */
	public static SkillMilestone forNode(SkillNode node) {
		if (node.isRoot()) {
			throw new IllegalArgumentException("The root node is ungated: " + node.path());
		}

		if (node.isUltimate()) {
			int index = Math.clamp(node.tier(), 0, ULTIMATE_TARGETS.length - 1);

			return new SkillMilestone(SkillObjective.SLAY_WITH_SKILL, ULTIMATE_TARGETS[index]);
		}

		// Clamped rather than trusted, so a shape change in SkillTree cannot walk off
		// the end of a table that was written for the old one. It reads as a repeated
		// last row, which is a visibly wrong number rather than a crash.
		int line = Math.clamp(node.line(), 0, OBJECTIVE_FOR_LINE.length - 1);
		int tier = Math.clamp(node.tier(), 0, TARGETS[line].length - 1);

		return new SkillMilestone(OBJECTIVE_FOR_LINE[line], TARGETS[line][tier]);
	}

	/** This character's counters, defaulting to one who has done nothing. */
	public static SkillProgress progressOf(ServerPlayer player) {
		return player.getAttachedOrElse(GrandCraftAttachments.SKILL_PROGRESS, SkillProgress.NONE);
	}

	/**
	 * Counts something the character did.
	 *
	 * <p>The single writer, which is why the three places in the game that count
	 * anything are each one line. Server side only — the attachment syncs itself to
	 * the owner, and a client that wrote here would be inventing progress.
	 *
	 * <p>No announcement. Unlike a level, a milestone advances constantly and by one,
	 * so anything on screen for it would be noise inside a minute; the character sheet
	 * is where it is read, and it is live there.
	 */
	public static void count(ServerPlayer player, SkillObjective objective, int amount) {
		if (amount <= 0) {
			return;
		}

		player.setAttached(GrandCraftAttachments.SKILL_PROGRESS,
				progressOf(player).plus(objective, amount));
	}

	/**
	 * Wipes every counter.
	 *
	 * <p>Used by {@code /grandcraft reclass}, which unmakes the character — the same
	 * reason and the same moment {@code EssenceAwards.reset} clears their levels.
	 */
	public static void reset(ServerPlayer player) {
		player.setAttached(GrandCraftAttachments.SKILL_PROGRESS, SkillProgress.NONE);
	}
}

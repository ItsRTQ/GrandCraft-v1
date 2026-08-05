package com.hrtq.grandcraft.skill;

import com.hrtq.grandcraft.progression.LevelSettings;

/**
 * Whether a node is open to a character, and why not if it is not.
 *
 * <h2>Derived, never stored</h2>
 * A node is unlocked when the character has <strong>both</strong> reached its tier's
 * level and completed its milestone. That is computed here on every ask, from the
 * character's level, their {@link SkillProgress} counters, and the level gates in
 * {@link LevelSettings} — nothing records the answer.
 *
 * <p>The alternative, a stored set of unlocked nodes, can disagree with the reasons it
 * was written, and by the time anyone notices there is no way to tell which of the two
 * was right. Deriving means the question has exactly one answer and no bookkeeping.
 *
 * <p>Two things follow, both intended:
 * <ul>
 *   <li>Moving a level gate on {@code /grandcraft config levels} re-evaluates every
 *       node at once, on every client, with no relog and no migration. Lowering one
 *       opens nodes; raising one closes them. The gate is what unlocking <em>means</em>,
 *       so an admin who moves it has moved what is unlocked.</li>
 *   <li><strong>The client can answer for itself.</strong> It already receives the
 *       level settings and the progress attachment, so the character sheet calls this
 *       same method rather than being told — no packet, and no chance of the two
 *       computing it differently, because there is only one computation.</li>
 * </ul>
 *
 * <p>The root is never gated: it is the class's own passive, in force from the moment
 * the class is chosen.
 */
public final class SkillUnlocks {
	private SkillUnlocks() {
	}

	/**
	 * The level a node requires.
	 *
	 * <p>Per tier and not per node — every line's third node opens at the same level;
	 * what differs between the lines is the milestone beside it.
	 *
	 * <p><strong>The ultimates reuse the row gates rather than owning four more.</strong>
	 * The first opens with the second row, the second with the third, the third with the
	 * fourth — the user's own framing, "by the division in rows". So there is nothing
	 * extra to configure, and moving a row's gate moves its ultimate with it, which is
	 * the behaviour anyone editing that field would expect from the way it is described.
	 */
	public static int levelGate(SkillNode node, LevelSettings settings) {
		if (node.isRoot()) {
			return 0;
		}

		// Ultimate 0 rides tier 1's gate (the second row), and so on up.
		return settings.skillGateLevel(node.isUltimate() ? node.tier() + 1 : node.tier());
	}

	public static SkillNodeState stateOf(SkillNode node, int level, SkillProgress progress,
			LevelSettings settings) {
		if (node.isRoot()) {
			return SkillNodeState.UNLOCKED;
		}

		if (level < levelGate(node, settings)) {
			return SkillNodeState.LOCKED;
		}

		return SkillMilestones.forNode(node).isComplete(progress)
				? SkillNodeState.UNLOCKED
				: SkillNodeState.IN_REACH;
	}

	public static boolean isUnlocked(SkillNode node, int level, SkillProgress progress,
			LevelSettings settings) {
		return stateOf(node, level, progress, settings).isUnlocked();
	}
}

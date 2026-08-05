package com.hrtq.grandcraft.skill;

/**
 * What a node is, to a particular character, right now.
 *
 * <p>Three states rather than two because "locked" hides the difference that matters
 * to a player: whether there is anything they can do about it today. A node whose
 * level has arrived and whose milestone has not is work in progress; one whose level
 * has not is not yet their problem.
 *
 * <p>Never stored. Always {@link SkillUnlocks#stateOf} of the character's current
 * level and counters.
 */
public enum SkillNodeState {
	/** The level gate has not been reached. Nothing to do here yet. */
	LOCKED,

	/** The level is there and the milestone is not. This is the one being worked on. */
	IN_REACH,

	/** Both gates met. */
	UNLOCKED;

	public boolean isUnlocked() {
		return this == UNLOCKED;
	}
}

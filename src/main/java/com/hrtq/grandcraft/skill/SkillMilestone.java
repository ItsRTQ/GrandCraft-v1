package com.hrtq.grandcraft.skill;

import net.minecraft.network.chat.Component;

/**
 * What a node asks you to have <em>done</em>, as opposed to what level you must have
 * reached. One of the two gates on every node; see {@link SkillUnlocks}.
 *
 * <p>An objective and a number, and deliberately nothing else. A milestone is not a
 * quest with its own state — the state is the counter on {@link SkillProgress}, which
 * many milestones read at different heights. That is what keeps forty-eight of them
 * from being forty-eight things to track.
 */
public record SkillMilestone(SkillObjective objective, int target) {

	public SkillMilestone {
		// A target of zero would be a milestone that is complete before the character
		// exists, which reads on the sheet as a gate that does not work. One is the
		// floor for the same reason LevelSettings.costOf has one.
		target = Math.max(target, 1);
	}

	/** Whether a character with this progress has done it. */
	public boolean isComplete(SkillProgress progress) {
		return progress.get(this.objective) >= this.target;
	}

	/** Capped at the target, so a finished milestone reads {@code 30 / 30} and not {@code 47 / 30}. */
	public int progressOf(SkillProgress progress) {
		return Math.min(progress.get(this.objective), this.target);
	}

	/** 0 to 1, for the sheet to draw a node filling up. */
	public float fractionOf(SkillProgress progress) {
		return (float) progressOf(progress) / this.target;
	}

	/** "Slay 30 hostiles" — the demand, without the progress against it. */
	public Component requirement() {
		return this.objective.requirement(this.target);
	}
}

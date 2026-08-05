package com.hrtq.grandcraft.skill;

/**
 * Holds the skill settings currently in force on the server.
 *
 * <p>Server-thread only, exactly like {@code CombatTuning}, {@code GameTuning},
 * {@code StatTuning} and {@code LevelTuning}. There is deliberately no client-side
 * counterpart: nothing on the client reads these numbers, because the one thing it has
 * to draw — the remaining ticks of a Combat Master window — arrives already resolved.
 *
 * <p>{@link #set} always swaps the whole object rather than mutating one, which keeps
 * the "compare by identity to notice a change" trick available to any per-tick reader.
 */
public final class SkillTuning {
	private static SkillSettings current = SkillSettings.DEFAULT;

	private SkillTuning() {
	}

	public static SkillSettings current() {
		return current;
	}

	public static void set(SkillSettings settings) {
		current = settings.clamped();
	}
}

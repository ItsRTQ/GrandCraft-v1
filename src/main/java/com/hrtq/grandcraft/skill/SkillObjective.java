package com.hrtq.grandcraft.skill;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.StringRepresentable;

/**
 * A thing a character can be counted as having done, which is what a milestone asks
 * for.
 *
 * <h2>One counter per objective, not one per node</h2>
 * Twelve nodes all asking "have you killed enough?" are twelve readings of a single
 * number. Storing it twelve times is how those readings drift apart, so
 * {@link SkillProgress} holds one counter per constant here and a node's milestone is
 * a {@link SkillMilestone#target()} read against it.
 *
 * <p>It also means a milestone is cheap: naming a new one is a target, not a new
 * thing to track. Only a genuinely new <em>kind</em> of doing needs a constant here.
 *
 * <h2>An enum, for as long as the list is short</h2>
 * The same bargain {@code Spell}, {@code CharacterStat} and {@code CharacterPool}
 * strike — wire-safe, switchable and iterable. A registry becomes right when
 * objectives are data rather than code, and that is a call to make with a real list
 * in hand rather than with three.
 *
 * <p>Adding one is four edits and no design: a constant, a field on
 * {@link SkillProgress}, an arm of each of its two switches, and the place in the
 * game that counts it.
 *
 * <h2>Why these three</h2>
 * One per skill-line, so the three subclasses are told apart by what they ask of you
 * and not merely by how much. Each also lands on a seam that already exists and
 * already means the thing being counted, which is why counting costs one line apiece
 * rather than a system.
 */
public enum SkillObjective implements StringRepresentable {
	/** Hostiles killed. Credited to whoever the killing blow belongs to. */
	SLAY("slay"),

	/**
	 * Melee swings that reached a target.
	 *
	 * <p>Swings, not landed damage: the count is taken where the character's own
	 * damage is worked out, which happens before the target has decided whether it
	 * was invulnerable. Close enough for what it gates, and it counts a swing at a
	 * shielded opponent, which is fair.
	 */
	STRIKE("strike"),

	/** Dodges the server accepted. A refused dodge is not one. */
	EVADE("evade"),

	/**
	 * Hostiles killed with a skill-line ability, which is what the three ultimates ask
	 * for.
	 *
	 * <p><strong>Nothing counts this yet, and that is not an oversight.</strong> No
	 * ability does anything, so nothing can kill with one — the counter is structurally
	 * zero until the first real ability ships, at which point crediting it is one line
	 * beside the {@link #SLAY} hook. Until then an ultimate is reachable only through
	 * {@code /grandcraft give milestone slay_with_skill}.
	 *
	 * <p>Named now rather than later because the ultimates' gate is real today: the
	 * level half of it works, the sheet reads it, and the alternative was to gate them
	 * on something they were never meant to ask for and then change it.
	 */
	SLAY_WITH_SKILL("slay_with_skill");

	private final String id;

	SkillObjective(String id) {
		this.id = id;
	}

	@Override
	public String getSerializedName() {
		return this.id;
	}

	public static SkillObjective byId(String id) {
		for (SkillObjective objective : values()) {
			if (objective.id.equals(id)) {
				return objective;
			}
		}

		return null;
	}

	/**
	 * What this objective asks for, at a given target — "Slay 30 hostiles".
	 *
	 * <p>The number is inside the sentence rather than appended to it, because the
	 * three read differently in English and a single "%s: %s" template would produce
	 * one of them badly. The <em>progress</em> against it is a separate line, since
	 * that part is the same shape for all three.
	 */
	public MutableComponent requirement(int target) {
		return Component.translatable("screen.grandcraft.sheet.objective." + this.id, target);
	}
}

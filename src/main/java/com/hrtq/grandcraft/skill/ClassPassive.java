package com.hrtq.grandcraft.skill;

import com.hrtq.grandcraft.player.PlayerClass;
import net.minecraft.network.chat.Component;

/**
 * The always-on ability at the root of a class's skill-lines.
 *
 * <p>A root passive is in force from the moment the class is chosen. It occupies none
 * of the four slots, has no keybind and cannot be unequipped — that is what makes it
 * the root rather than a first node.
 *
 * <h2>Two constants, and two classes with none</h2>
 * Warrior's and Outlaw's are designed. {@link #forRoot} answers <strong>null</strong>
 * for Sorcerer and Cleric, which is what "inert until designed" means concretely: their
 * root still draws, is still never gated, and still does nothing.
 *
 * <p>Same bargain {@code Spell} strikes — an enum while the list is short, so a second
 * passive is a constant and an arm of one switch rather than a rewrite. A registry
 * becomes right when passives are data rather than code, which is a call to make with
 * four of them in hand rather than one.
 *
 * <h2>This is what finally uses the naming seam</h2>
 * {@link SkillNode#translationKey()} has computed a per-node key since the structure
 * slice with nothing reading it. A node that has a passive is named by it; a node that
 * does not keeps the generic placeholder. So the character sheet gains a real name
 * exactly where there is a real ability behind it, and nowhere else.
 */
public enum ClassPassive {
	/**
	 * Warrior. A clean block opens a short window in which the Warrior moves quicker
	 * and their next connecting blow lands harder — and which that blow then closes.
	 *
	 * <p>See {@link CombatMaster} for the rules; this constant is only its identity and
	 * its text.
	 */
	COMBAT_MASTER(PlayerClass.WARRIOR),

	/**
	 * Outlaw. A second push in mid-air, and walls that can be caught and kicked off
	 * again — twice, until the ground is stood on long enough to reset them.
	 *
	 * <p>See {@link Acrobat} for the rules; this constant is only its identity and its
	 * text.
	 */
	ACROBAT(PlayerClass.OUTLAW);

	private final PlayerClass playerClass;

	ClassPassive(PlayerClass playerClass) {
		this.playerClass = playerClass;
	}

	public PlayerClass playerClass() {
		return this.playerClass;
	}

	/**
	 * The passive on a class's root node, or {@code null} if that class has none yet.
	 *
	 * <p>Null rather than an empty constant on purpose: "there is no ability here" is a
	 * real answer that the sheet and the combat hooks both have to branch on, and a
	 * do-nothing constant would make three classes look finished.
	 */
	public static ClassPassive forRoot(PlayerClass playerClass) {
		for (ClassPassive passive : values()) {
			if (passive.playerClass == playerClass) {
				return passive;
			}
		}

		return null;
	}

	/** The passive on this node, or null — only ever the root has one. */
	public static ClassPassive forNode(SkillNode node) {
		return node.isRoot() ? forRoot(node.playerClass()) : null;
	}

	/**
	 * Named by the node it sits on, so the lang key is the one
	 * {@link SkillNode#translationKey()} already computes — {@code
	 * skill.grandcraft.warrior.root}. Nothing has to agree with anything.
	 */
	public Component displayName() {
		return Component.translatable(rootKey());
	}

	public Component description() {
		return Component.translatable(rootKey() + ".description");
	}

	private String rootKey() {
		return SkillNode.root(this.playerClass).translationKey();
	}
}

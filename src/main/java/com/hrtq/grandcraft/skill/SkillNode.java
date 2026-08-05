package com.hrtq.grandcraft.skill;

import com.hrtq.grandcraft.player.PlayerClass;
import net.minecraft.network.chat.Component;

/**
 * One position in a class's skill-line structure.
 *
 * <p>A node is <em>identity only</em>. It carries no ability, no cost and no unlock
 * state, because none of those are designed yet — what exists today is the shape, and
 * this is a name for one place in it.
 *
 * <h2>Position is the identity</h2>
 * A node is {@code (class, line, tier)} and nothing else. That is enough to name it,
 * enough to find its text, and enough to compare two of them — records give equality
 * for free — so nothing has to hand out identifiers or keep a registry in step with
 * the tree.
 *
 * <p>The root is the same record with {@link #ROOT} in both slots rather than a type
 * of its own. It sits in the structure exactly as the others do and differs only in
 * where it is drawn and what it will eventually mean.
 *
 * <h2>{@link #translationKey()} is the seam, and is deliberately unused</h2>
 * It computes the key a node <em>will</em> be named by — {@code skill.grandcraft.
 * warrior.line_1.node_2} — the same way the class browser derives an icon path from
 * {@code PlayerClass.getSerializedName()}: from what the thing already is, so adding
 * one needs a lang entry rather than a lang entry <em>and</em> a field.
 *
 * <p>Nothing reads it yet. Writing 128 placeholder entries for four classes' worth of
 * nodes and lines would be 128 lines of text that every one of them gets rewritten the
 * moment an ability is designed, so the sheet describes a node generically for now.
 * When the names are real, they are added under these keys and the tooltip switches to
 * asking the node for its own.
 */
public record SkillNode(PlayerClass playerClass, int line, int tier) {

	/**
	 * The {@code line} of the root, which belongs to no line and sits above every tier.
	 *
	 * <p>Negative on purpose: a real index is zero-based, so there is no value a loop
	 * over the lines could produce that would collide with this or with
	 * {@link #ULTIMATE}.
	 */
	public static final int ROOT = -1;

	/**
	 * The {@code line} of an ultimate, whose {@code tier} is then its index among them.
	 *
	 * <p>Ultimates belong to the <em>class</em> rather than to a line — they are not the
	 * end of a subclass, and any of the three can be earned however the character got
	 * there. That is why they encode as "no line" rather than as a fifth tier: there is
	 * no line for them to be the fifth tier of.
	 */
	public static final int ULTIMATE = -2;

	/** The single node every line of a class hangs off. */
	public static SkillNode root(PlayerClass playerClass) {
		return new SkillNode(playerClass, ROOT, ROOT);
	}

	/** One of the class's ultimates, indexed from zero in unlock order. */
	public static SkillNode ultimate(PlayerClass playerClass, int index) {
		return new SkillNode(playerClass, ULTIMATE, index);
	}

	public boolean isRoot() {
		return this.line == ROOT;
	}

	public boolean isUltimate() {
		return this.line == ULTIMATE;
	}

	/**
	 * Whether this is one of the twelve nodes in a line — the ones that go in an
	 * ability slot.
	 *
	 * <p>Asked rather than {@code !isRoot()}, because "not the root" and "goes in a
	 * numbered slot" stopped being the same question the moment ultimates existed.
	 */
	public boolean isLineNode() {
		return this.line >= 0;
	}

	/**
	 * Where this node sits, as the dotted path its text is filed under — and, since the
	 * loadout stores nodes by it, the identity that survives a save.
	 *
	 * <p>One-based, because this is the display numbering — the first node of the first
	 * line reads {@code line_1.node_1} rather than {@code line_0.node_0}, which is what
	 * anyone writing the lang file or reading a log line would expect. The zero-based
	 * form stays inside the code.
	 *
	 * <p><strong>Stored in save files</strong> by {@code SkillLoadout}, so changing this
	 * format silently empties every existing character's slots. It is worth the
	 * readability: a slot reads {@code warrior.line_2.node_3} rather than {@code 7}, and
	 * a path that no longer resolves fails safe to an empty slot rather than to a
	 * different ability.
	 */
	public String path() {
		String id = this.playerClass.getSerializedName();

		if (isRoot()) {
			return id + ".root";
		}

		if (isUltimate()) {
			return id + ".ultimate_" + (this.tier + 1);
		}

		return id + ".line_" + (this.line + 1) + ".node_" + (this.tier + 1);
	}

	/** See the class javadoc: this is the seam, and nothing consumes it yet. */
	public String translationKey() {
		return "skill.grandcraft." + path();
	}

	public Component displayName() {
		return Component.translatable(translationKey());
	}
}

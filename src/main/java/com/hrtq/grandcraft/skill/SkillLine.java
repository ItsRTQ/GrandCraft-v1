package com.hrtq.grandcraft.skill;

import com.hrtq.grandcraft.player.PlayerClass;
import java.util.List;
import net.minecraft.network.chat.Component;

/**
 * One branch of a class's skill-lines: a straight chain of nodes, deepest last.
 *
 * <p>A chain rather than a graph, and that is the whole design — a line is a
 * commitment. Spending down one is what a character gives up to not have spent down
 * another, which is the mechanism {@code product-goal.md} names for making two
 * Warriors fight differently. A node with several parents would let a character take
 * the top of every line and lose nothing.
 *
 * <p>{@link #nodes()} is in tier order and is always {@link SkillTree#NODES_PER_LINE}
 * long. Immutable, like every record here, so there is no way to half-build one.
 *
 * <p>{@link #translationKey()} is the same deliberately-unused seam as
 * {@link SkillNode#translationKey()} — see that class for why the sheet names a line
 * generically today.
 */
public record SkillLine(PlayerClass playerClass, int index, List<SkillNode> nodes) {

	public SkillLine {
		nodes = List.copyOf(nodes);
	}

	/** The node at the given tier, zero-based and shallowest first. */
	public SkillNode node(int tier) {
		return this.nodes.get(tier);
	}

	/** One-based, for the same reason {@link SkillNode#path()} is. */
	public String translationKey() {
		return "skill.grandcraft." + this.playerClass.getSerializedName() + ".line_" + (this.index + 1);
	}

	public Component displayName() {
		return Component.translatable(translationKey());
	}
}

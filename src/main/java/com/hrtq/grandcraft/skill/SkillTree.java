package com.hrtq.grandcraft.skill;

import com.hrtq.grandcraft.player.PlayerClass;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A class's whole skill-line structure: one root, and three lines of four hanging off
 * it.
 *
 * <h2>This is the structure and nothing else</h2>
 * There are no abilities here, no costs, no unlock state and no points. Those are a
 * later slice and they arrive by giving {@link SkillNode} a payload — the shape, and
 * every node's identity, do not change when they do.
 *
 * <h2>Why the shape is generated rather than tabulated</h2>
 * All four classes share it, by decision: thirteen positions, the same thirteen every
 * time. Writing that out four times would be four copies of one fact, and the day a
 * fifth line or a fifth tier is wanted it would be four edits with three chances to
 * miss one. So {@link #LINES} and {@link #NODES_PER_LINE} are the shape, and
 * {@link #of} builds it for whichever class is asked.
 *
 * <p>The renderer reads those two constants too, so the drawing follows the data
 * rather than repeating the numbers. <strong>Changing either one changes the picture
 * on the character sheet with no other edit.</strong>
 *
 * <p>When the lines stop being identical — when Warrior's second line is a real thing
 * with a real name and Sorcerer's is a different real thing — this is the file that
 * gains a per-class table, and {@link #of} is the method that reads it. Nothing
 * outside asks how a tree was built.
 *
 * <p>Peasant gets a tree like every other class, because answering for one class and
 * not another is a null check at every call site. Nothing asks for it: the character
 * sheet's right panel belongs to the class browser until a class is chosen.
 */
public record SkillTree(PlayerClass playerClass, SkillNode root, List<SkillLine> lines,
		List<SkillNode> ultimates) {

	/** Branches under the root. */
	public static final int LINES = 3;

	/** Nodes in each branch, shallowest first. */
	public static final int NODES_PER_LINE = 4;

	/**
	 * The class's ultimates, in unlock order.
	 *
	 * <p>Deliberately <em>not</em> one per line. An ultimate belongs to the class, so
	 * any of the three can be earned however the character got there, and only one is
	 * ever equipped — which is what makes choosing between them a real decision rather
	 * than a consequence of which line you happened to walk down.
	 */
	public static final int ULTIMATES = 3;

	/**
	 * Declared before the static initialiser that fills it — the registration
	 * convention this mod follows everywhere, and the one ordering mistake in a static
	 * initialiser that fails silently rather than loudly.
	 */
	private static final Map<PlayerClass, SkillTree> TREES = new EnumMap<>(PlayerClass.class);

	static {
		for (PlayerClass playerClass : PlayerClass.values()) {
			TREES.put(playerClass, build(playerClass));
		}
	}

	public SkillTree {
		lines = List.copyOf(lines);
		ultimates = List.copyOf(ultimates);
	}

	/** The structure for a class. Built once at class-load; never null. */
	public static SkillTree of(PlayerClass playerClass) {
		return TREES.get(playerClass);
	}

	private static SkillTree build(PlayerClass playerClass) {
		List<SkillLine> lines = new ArrayList<>(LINES);

		for (int line = 0; line < LINES; line++) {
			List<SkillNode> nodes = new ArrayList<>(NODES_PER_LINE);

			for (int tier = 0; tier < NODES_PER_LINE; tier++) {
				nodes.add(new SkillNode(playerClass, line, tier));
			}

			lines.add(new SkillLine(playerClass, line, nodes));
		}

		List<SkillNode> ultimates = new ArrayList<>(ULTIMATES);

		for (int index = 0; index < ULTIMATES; index++) {
			ultimates.add(SkillNode.ultimate(playerClass, index));
		}

		return new SkillTree(playerClass, SkillNode.root(playerClass), lines, ultimates);
	}

	public SkillLine line(int index) {
		return this.lines.get(index);
	}

	public SkillNode ultimate(int index) {
		return this.ultimates.get(index);
	}

	/**
	 * Every node of this tree, root first, then the lines, then the ultimates.
	 *
	 * <p>Built on each call rather than cached: it is asked for once per screen layout
	 * and once per loadout lookup, and a cached list on a record that already promises
	 * immutability is a field that has to be kept honest for no measurable gain.
	 */
	public List<SkillNode> allNodes() {
		List<SkillNode> all = new ArrayList<>(1 + LINES * NODES_PER_LINE + ULTIMATES);

		all.add(this.root);
		this.lines.forEach(line -> all.addAll(line.nodes()));
		all.addAll(this.ultimates);

		return all;
	}

	/**
	 * The node a stored path names, or {@code null} if this tree has no such node.
	 *
	 * <p>How a saved loadout is read back. Null is the <em>expected</em> answer for a
	 * path belonging to another class or to a shape that no longer exists, and the
	 * caller turns it into an empty slot — a slot that quietly empties is a far better
	 * failure than one that resolves to a different ability than the player chose.
	 *
	 * <p>A scan of sixteen, not a map: it runs when a sheet opens or a key is pressed,
	 * and a map here would be a second thing to build and keep in step.
	 */
	public SkillNode nodeByPath(String path) {
		if (path == null || path.isEmpty()) {
			return null;
		}

		for (SkillNode node : allNodes()) {
			if (node.path().equals(path)) {
				return node;
			}
		}

		return null;
	}
}

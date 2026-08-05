package com.hrtq.grandcraft.client.gui;

import com.hrtq.grandcraft.progression.LevelSettings;
import com.hrtq.grandcraft.skill.ClassPassive;
import com.hrtq.grandcraft.skill.SkillLine;
import com.hrtq.grandcraft.skill.SkillLoadout;
import com.hrtq.grandcraft.skill.SkillMilestone;
import com.hrtq.grandcraft.skill.SkillMilestones;
import com.hrtq.grandcraft.skill.SkillNode;
import com.hrtq.grandcraft.skill.SkillNodeState;
import com.hrtq.grandcraft.skill.SkillProgress;
import com.hrtq.grandcraft.skill.SkillTree;
import com.hrtq.grandcraft.skill.SkillUnlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * The skill-line structure, drawn into the character sheet's right panel, and what
 * state each node is in for the character looking at it.
 *
 * <p><strong>This is the file to edit when the tree is the wrong shape, the wrong size
 * or the wrong colour.</strong> {@link #layout} decides every pixel and {@link #refresh}
 * decides every state; {@link #extract} only draws what those two decided. How many
 * lines and how many nodes are not here at all — they are {@link SkillTree#LINES} and
 * {@link SkillTree#NODES_PER_LINE}, so the picture follows the data rather than
 * agreeing with it by coincidence.
 *
 * <h2>The panel answers for itself, rather than being told</h2>
 * Unlocking is derived, not stored — see {@code SkillUnlocks} — and everything the
 * derivation needs is already on the client: the character's level and counters arrive
 * as synced attachments, and the level gates arrive with the level settings. So this
 * calls the <em>same method</em> the server would. There is no packet, and no way for
 * the two sides to compute it differently, because there is only one computation.
 *
 * <h2>Structure, not abilities</h2>
 * A node can be locked, in reach or unlocked, and that is all it can be. Nothing is
 * equipped, no key fires anything, and a click is not an input this panel has. What
 * unlocking currently earns you is the right to see the box light up.
 *
 * <p>The root carries the class picture and is never gated — it is the class's own
 * passive, in force from the moment the class is chosen. The twelve below it are blank
 * boxes, because there is nothing to put in one yet and a placeholder mark repeated
 * twelve times would read as content rather than as absence. This panel is therefore
 * only ever built for a character who has a class: Peasant has no icon, and the class
 * browser owns the panel until one is chosen.
 *
 * <h2>Sized from the space it is given</h2>
 * The right panel is half the sheet, and half the sheet is about 216x210 at GUI scale
 * 4 on a small window against 456x480 at scale 2 — better than twice over in both
 * directions. A fixed node size would either rattle around in the large case or run
 * off the bottom in the small one, so the size is derived from the room available,
 * the same way {@code GrandCraftScreen.modelSize()} derives the player model's.
 *
 * <p>{@link #MAX_NODE} is what stops that going the other way: a box the size of a
 * fist on a large window would read as a menu rather than a tree. When the cap bites,
 * the block is centred in what is left over rather than hanging from the top.
 *
 * <h2>Elbows, not diagonals</h2>
 * The reference drawing joins the root to the outer lines with diagonal strokes. These
 * are right-angled instead — down, across, down — because the extract pipeline draws
 * rectangles ({@code fill}) and everything else in this mod that draws without artwork
 * does it that way. A diagonal would need a rotated quad or a staircase of one-pixel
 * fills, for a picture that reads the same.
 *
 * <h2>Hover is drawn here, tooltips are not</h2>
 * The highlight under the mouse is computed from the mouse position in {@link #extract}
 * and owes nothing to the widget layer. The tooltips ride an invisible
 * {@link StringWidget} per node — see {@link #hoverTargets()}. Keeping the two separate
 * is deliberate: the boxes are drawn in the screen's extract pass anyway, so the
 * widgets exist for exactly one reason and the picture does not depend on them.
 */
public class SkillTreePanel {
	/** Matches the class browser's inset, so the two halves of the sheet agree. */
	private static final int PANEL_INSET = 10;

	private static final int LINE_HEIGHT = 9;
	private static final int SECTION_GAP = 7;

	/**
	 * Vertical air between two node boxes, which is also where a connector runs.
	 *
	 * <p>Derived, not fixed, and for a different reason than the node size is: once the
	 * node hits {@link #MAX_NODE} on a large panel there is height left over, and a tree
	 * that refuses to use it sits in the middle of an empty half-screen looking like a
	 * mistake. The leftover goes into the gaps until they hit their own cap, and only
	 * what is over from <em>that</em> becomes margin.
	 */
	private static final int MIN_ROW_GAP = 12;
	private static final int MAX_ROW_GAP = 28;

	/**
	 * Bounds on the derived node size.
	 *
	 * <p>The floor is a legibility floor rather than a layout one — below about ten
	 * pixels a bordered box is two pixels of border and nothing else. If the panel is
	 * ever genuinely too short for five of them, the tree overflows visibly instead of
	 * collapsing into a smear, which is the failure worth having.
	 */
	private static final int MIN_NODE = 10;
	private static final int MAX_NODE = 32;

	private static final int CONNECTOR_THICKNESS = 2;

	/**
	 * Air between the root's border and the class picture inside it, on every side.
	 *
	 * <p>One pixel on top of the one the border already takes. The icons are cropped to
	 * their own content, so without it the artwork would touch the frame on whichever
	 * side its subject happens to reach — which reads as a picture too big for its box
	 * rather than as a deliberately tight fit.
	 */
	private static final int ICON_INSET = 2;

	/**
	 * ARGB throughout, and the alpha byte is never optional: 0x6E6E6E is invisible.
	 *
	 * <p>The three states are told apart by <em>brightness</em>, not by hue — a locked
	 * node is dimmer, an unlocked one is gold. Hue is left free for whatever
	 * distinguishes one ability from another later, and brightness reads at a node size
	 * of ten pixels where a hue difference would not.
	 */
	private static final int BORDER_LOCKED = 0xFF4A4A4A;
	private static final int BORDER_IN_REACH = 0xFF8A8A8A;
	private static final int BORDER_UNLOCKED = 0xFFFFC24A;
	private static final int BORDER_HOVERED = 0xFFFFFFFF;

	private static final int INNER_LOCKED = 0x99000000;
	private static final int INNER_IN_REACH = 0x66000000;
	private static final int INNER_UNLOCKED = 0x44FFC24A;

	/** The milestone's share of an in-reach node, filled from the bottom up. */
	private static final int INNER_PROGRESS = 0x66FFC24A;

	private static final int CONNECTOR = 0xFF6E6E6E;

	/** ChatFormatting.GOLD, as a colour — the heading is drawn, not a styled widget. */
	private static final int HEADING_COLOUR = 0xFFFFAA00;

	/** The slot number written on an equipped node, over its own fill. */
	private static final int SLOT_NUMBER_COLOUR = 0xFFFFFFFF;

	private final Font font;
	private final SkillTree tree;

	/**
	 * Where a node ended up and what it currently is.
	 *
	 * <p>Mutable, and holding its own hover widget, so {@link #refresh} can restate one
	 * node without a parallel array to keep in step and without rebuilding the widget
	 * list — which is what would take a tooltip out from under the mouse.
	 */
	private static final class NodeBox {
		private final SkillNode node;
		private final int left;
		private final int top;
		private final StringWidget target;

		private SkillNodeState state = SkillNodeState.LOCKED;

		/** 0 to 1 of the milestone, and only ever drawn while in reach. */
		private float fill;

		/** Which key this node is on, or -1. */
		private int slot = -1;

		private NodeBox(SkillNode node, int left, int top, StringWidget target) {
			this.node = node;
			this.left = left;
			this.top = top;
			this.target = target;
		}

		/** Ultimates are drawn as discs, per the schema; everything else is a square. */
		private boolean isRound() {
			return this.node.isUltimate();
		}
	}

	private final List<NodeBox> boxes = new ArrayList<>();

	private int nodeSize;
	private int headingX;
	private int headingY;

	/** Centre line of each of the three lines, left to right. */
	private final int[] columnCentreX = new int[SkillTree.LINES];

	/**
	 * Top edge of each row. Index 0 is the root; 1 through
	 * {@link SkillTree#NODES_PER_LINE} are the tiers, so the row for tier {@code t} is
	 * {@code rowTop[t + 1]}.
	 */
	private final int[] rowTop = new int[SkillTree.NODES_PER_LINE + 1];

	/** Where the horizontal run joining the root to the three lines sits. */
	private int busY;

	/** Top edge of the sixth row, which only the bottom ultimate uses. */
	private int ultimateBottomTop;

	/**
	 * The root's own centre line, kept rather than re-derived.
	 *
	 * <p>It is the panel's centre, which is the middle column's centre only while there
	 * is an odd number of lines. Reading it off {@code columnCentreX} would draw the
	 * stub in the wrong place the day {@link SkillTree#LINES} became even, which is
	 * exactly the sort of edit this file claims to survive.
	 */
	private int rootCentreX;

	/**
	 * What {@link #refresh} was last given, so it can tell a call that changes something
	 * from the twenty a second that do not.
	 *
	 * <p>Re-setting a tooltip restarts its hover delay, so doing it every tick would
	 * mean a tooltip that never appears. Comparing first is what makes the numbers live
	 * without that cost — and all three are records or ints, so equality is free and
	 * exact.
	 */
	private int shownLevel = -1;
	private SkillProgress shownProgress;
	private LevelSettings shownSettings;
	private SkillLoadout shownLoadout;

	public SkillTreePanel(Font font, SkillTree tree) {
		this.font = font;
		this.tree = tree;
	}

	/**
	 * Places everything inside the given rectangle, which is the panel's own bounds —
	 * the inset is applied here rather than by the caller, so the heading lines up with
	 * the class browser's without the sheet having to know either number.
	 *
	 * <p>Placement only: every node comes out {@code LOCKED} until {@link #refresh} says
	 * otherwise, which the caller must do before drawing. Splitting the two is what lets
	 * state change twenty times a second while the layout is computed once.
	 *
	 * <p>Safe to call again: a resize rebuilds the screen and therefore this.
	 */
	public void layout(int left, int top, int right, int bottom) {
		this.boxes.clear();

		// Forget what was shown, or a refresh with unchanged values would decline to
		// restate tooltips onto the widgets this call is about to replace.
		this.shownLevel = -1;
		this.shownProgress = null;
		this.shownSettings = null;
		this.shownLoadout = null;

		int centreX = (left + right) / 2;

		this.rootCentreX = centreX;
		this.headingX = centreX;
		this.headingY = top + PANEL_INSET;

		int blockTop = this.headingY + LINE_HEIGHT + SECTION_GAP;
		int blockBottom = bottom - PANEL_INSET;

		int rows = SkillTree.NODES_PER_LINE + 1;

		// One more than there are node rows: the third ultimate hangs below the tree, and
		// sizing for five rows and then drawing six is how it ends up off the bottom of a
		// small window. The two upper ultimates need no reservation — they sit out to the
		// sides, in space the tree was never using.
		int slots = rows + 1;
		int available = blockBottom - blockTop;

		// Node size first, at the tightest gap it would accept — the boxes are what has
		// to be legible, so they get first call on the height and the gaps take what is
		// left rather than the other way round.
		this.nodeSize = Math.clamp(
				(available - (slots - 1) * MIN_ROW_GAP) / slots, MIN_NODE, MAX_NODE);

		int rowGap = Math.clamp(
				(available - slots * this.nodeSize) / (slots - 1), MIN_ROW_GAP, MAX_ROW_GAP);

		// Whatever is still over becomes margin, split evenly, so the tree is centred in
		// the panel rather than hanging from the heading.
		int used = slots * this.nodeSize + (slots - 1) * rowGap;
		int firstRowTop = blockTop + Math.max(0, (available - used) / 2);

		for (int row = 0; row < rows; row++) {
			this.rowTop[row] = firstRowTop + row * (this.nodeSize + rowGap);
		}

		// The sixth slot, which only the bottom ultimate occupies.
		this.ultimateBottomTop = firstRowTop + rows * (this.nodeSize + rowGap);

		// Spread with the node size, but never so far that a column runs under the
		// panel's edge on a narrow window. The inner width is what the columns have to
		// share; half of what is left once one node's width is taken off is the furthest
		// an outer column's centre can sit from the middle.
		int innerWidth = (right - left) - PANEL_INSET * 2;
		int spacing = Math.min(this.nodeSize * 2, Math.max(0, (innerWidth - this.nodeSize) / 2));

		for (int line = 0; line < SkillTree.LINES; line++) {
			this.columnCentreX[line] = centreX + (line - 1) * spacing;
		}

		this.busY = (this.rowTop[0] + this.nodeSize + this.rowTop[1]) / 2;

		place(this.tree.root(), this.rootCentreX, this.rowTop[0]);

		for (int line = 0; line < SkillTree.LINES; line++) {
			SkillLine skillLine = this.tree.line(line);

			for (int tier = 0; tier < SkillTree.NODES_PER_LINE; tier++) {
				place(skillLine.node(tier), this.columnCentreX[line], this.rowTop[tier + 1]);
			}
		}

		placeUltimates(left, right);
	}

	/**
	 * The three ultimates, where the schema puts them: the first two in the upper
	 * corners the tree does not reach, the third under its middle line.
	 *
	 * <p>Unconnected, also per the schema — and it turns out to be the honest drawing.
	 * An ultimate belongs to the <em>class</em>, not to any line, so a stub joining one
	 * to a column would claim a relationship that does not exist. Floating says
	 * "reachable from anywhere", which is exactly what they are.
	 *
	 * <p>Order is reading order: top-left, top-right, then bottom. That is also unlock
	 * order, so the first one a character can earn is the first one they read.
	 */
	private void placeUltimates(int left, int right) {
		if (SkillTree.ULTIMATES == 0) {
			return;
		}

		int innerLeft = left + PANEL_INSET;
		int innerRight = right - PANEL_INSET;

		// Level with the root rather than above it: the corners are empty either way, and
		// sharing a row stops them reading as a heading over the tree.
		place(this.tree.ultimate(0), innerLeft + this.nodeSize / 2, this.rowTop[0]);

		if (SkillTree.ULTIMATES > 1) {
			place(this.tree.ultimate(1), innerRight - this.nodeSize / 2, this.rowTop[0]);
		}

		// Any beyond the first two go under the tree, on the centre line.
		for (int index = 2; index < SkillTree.ULTIMATES; index++) {
			place(this.tree.ultimate(index), this.rootCentreX, this.ultimateBottomTop);
		}
	}

	/**
	 * Records where one node goes and builds the invisible widget that explains it.
	 *
	 * <p>A {@link StringWidget} with no text draws nothing and answers the mouse, which
	 * is the whole requirement — the box itself is drawn in {@link #extract}. A custom
	 * {@code AbstractWidget} would mean guessing at 26.2's render and narration
	 * signatures for a widget that renders nothing, and a wrong guess there is a launch
	 * failure rather than a compile error.
	 *
	 * <p>No tooltip yet: what a node has to say depends on the character, and that is
	 * {@link #refresh}'s to know.
	 */
	private void place(SkillNode node, int centreX, int top) {
		int left = centreX - this.nodeSize / 2;

		// A round node's tooltip zone is the square that fits *inside* the circle, not the
		// one around it. A widget is a rectangle and there is no making it otherwise, so
		// the choice is which way to be wrong: too small means the tooltip is missing at
		// the very edge, too large means it appears in a corner where nothing is drawn and
		// nothing would respond to a click. The hover highlight and the click both test
		// the true circle — see hovered().
		int hitSize = node.isUltimate()
				? (int) (this.nodeSize / Math.sqrt(2.0))
				: this.nodeSize;

		StringWidget target = new StringWidget(hitSize, hitSize, Component.empty(), this.font);

		target.setX(centreX - hitSize / 2);
		target.setY(top + (this.nodeSize - hitSize) / 2);

		this.boxes.add(new NodeBox(node, left, top, target));
	}

	/**
	 * The widgets the screen must register, in the order they were placed.
	 *
	 * <p>Handed back rather than added here because the screen owns its widget list —
	 * and because they have to go in <em>after</em> the panel is laid out, which is the
	 * one ordering this arrangement makes hard to get wrong.
	 */
	public List<AbstractWidget> hoverTargets() {
		return this.boxes.stream().map(box -> (AbstractWidget) box.target).toList();
	}

	// -------------------------------------------------------------------- state

	/**
	 * Restates every node for the character now looking at the sheet.
	 *
	 * <p>Cheap to call every tick, and meant to be: it returns immediately unless the
	 * level, the counters or the gates actually moved. That is what makes a milestone
	 * climb while the sheet is open without the screen being rebuilt — a rebuild would
	 * drop whatever tooltip the mouse was over, which is the whole reason the sheet
	 * distinguishes a restatement from a rebuild elsewhere too.
	 *
	 * @param level    the character's Essence Power level
	 * @param progress their milestone counters
	 * @param settings the server's level settings, which carry the gates
	 */
	public void refresh(int level, SkillProgress progress, LevelSettings settings,
			SkillLoadout loadout) {
		if (level == this.shownLevel
				&& progress.equals(this.shownProgress)
				&& settings.equals(this.shownSettings)
				&& loadout.equals(this.shownLoadout)) {
			return;
		}

		this.shownLevel = level;
		this.shownProgress = progress;
		this.shownSettings = settings;
		this.shownLoadout = loadout;

		for (NodeBox box : this.boxes) {
			box.state = SkillUnlocks.stateOf(box.node, level, progress, settings);
			box.slot = loadout.slotOf(box.node.path());

			// Only an in-reach node has a bar worth drawing: a locked one has not been
			// asked for anything yet, and an unlocked one is finished.
			box.fill = box.state == SkillNodeState.IN_REACH
					? SkillMilestones.forNode(box.node).fractionOf(progress)
					: 0.0F;

			box.target.setTooltip(Tooltip.create(tooltipFor(box)));
		}
	}

	/**
	 * The node under the mouse, or {@code null}.
	 *
	 * <p>How a click finds its target — {@code GrandCraftScreen.mouseClicked} asks this
	 * and sends the answer. Kept separate from the hover widgets deliberately: those
	 * exist only to carry tooltips, and giving them behaviour as well would mean two
	 * places deciding what a node does.
	 */
	public SkillNode nodeAt(double mouseX, double mouseY) {
		for (NodeBox box : this.boxes) {
			if (hovered(box, mouseX, mouseY)) {
				return box.node;
			}
		}

		return null;
	}

	/** Whether this node can be equipped at all — the root never can. */
	public static boolean isEquippable(SkillNode node) {
		return !node.isRoot();
	}

	/**
	 * What a node says when hovered: what it is, whether it is open, and the two gates
	 * with live numbers against them.
	 *
	 * <p>Both gates are always shown, met or not, because "why is this shut" and "what
	 * did I have to do" are the same question asked before and after — and a line that
	 * disappears once satisfied leaves the player unable to check what they did.
	 */
	private Component tooltipFor(NodeBox box) {
		if (box.node.isRoot()) {
			ClassPassive passive = ClassPassive.forNode(box.node);

			// A root with a real passive is named by it, and describes what it does. One
			// without says so plainly rather than pretending — three of the four classes
			// have no root ability designed yet, and a tooltip that read like the Warrior's
			// would be claiming something untrue.
			MutableComponent root = head(passive == null
							? Component.translatable("screen.grandcraft.sheet.skill_root",
									this.tree.playerClass().displayName())
							: passive.displayName(),
					ChatFormatting.YELLOW);

			then(root, Component.translatable("screen.grandcraft.sheet.skill_state.always_active"),
					ChatFormatting.GREEN);

			return then(root, passive == null
							? Component.translatable("screen.grandcraft.sheet.skill_root.tooltip")
							: passive.description(),
					ChatFormatting.GRAY);
		}

		SkillMilestone milestone = SkillMilestones.forNode(box.node);
		int gate = SkillUnlocks.levelGate(box.node, this.shownSettings);

		MutableComponent text = head(titleOf(box.node), ChatFormatting.YELLOW);

		then(text, stateName(box.state),
				box.state.isUnlocked() ? ChatFormatting.GREEN : ChatFormatting.RED);

		// What the player can do about it, right under what it is — before the gates,
		// which are the explanation rather than the action.
		if (box.slot >= 0) {
			then(text, Component.translatable("screen.grandcraft.sheet.skill_equipped",
					box.slot + 1), ChatFormatting.AQUA);
		} else if (box.state.isUnlocked()) {
			boolean room = box.node.isUltimate()
					|| this.shownLoadout.firstFreeAbilitySlot() >= 0;

			then(text, Component.translatable(room
							? "screen.grandcraft.sheet.skill_equip_hint"
							: "screen.grandcraft.sheet.equip.full"),
					room ? ChatFormatting.AQUA : ChatFormatting.RED);
		}

		// Each gate coloured by whether it is met, so which of the two is holding the
		// node shut is readable without comparing the numbers yourself.
		then(text, Component.translatable("screen.grandcraft.sheet.skill_gate.level",
						gate, this.shownLevel),
				this.shownLevel >= gate ? ChatFormatting.GRAY : ChatFormatting.RED);

		then(text, Component.translatable("screen.grandcraft.sheet.skill_gate.milestone",
						milestone.requirement(),
						milestone.progressOf(this.shownProgress), milestone.target()),
				milestone.isComplete(this.shownProgress) ? ChatFormatting.GRAY : ChatFormatting.RED);

		return then(text, Component.translatable("screen.grandcraft.sheet.skill_node.tooltip"),
				ChatFormatting.DARK_GRAY);
	}

	/**
	 * The first line of a tooltip.
	 *
	 * <p>A tooltip is one component rather than several: {@code Tooltip} takes a single
	 * message and splits it with the font, which handles {@code \n}. Separating the head
	 * from the rest is what stops a blank row at the top — the newline belongs
	 * <em>before</em> each following line, never after the last.
	 */
	private static MutableComponent head(Component text, ChatFormatting colour) {
		return text.copy().withStyle(colour);
	}

	/** Appends a further line. Mutates and returns {@code text}, for chaining. */
	private static MutableComponent then(MutableComponent text, Component line,
			ChatFormatting colour) {
		return text.append(Component.literal("\n")).append(line.copy().withStyle(colour));
	}

	/**
	 * What a node is called: "Line 2 — Tier 3", or "Ultimate I".
	 *
	 * <p>Ultimates are numbered in reading order, which is also unlock order, so the
	 * first one a character can earn is the first one they see.
	 */
	private static Component titleOf(SkillNode node) {
		return node.isUltimate()
				? Component.translatable("screen.grandcraft.sheet.ultimate", node.tier() + 1)
				: Component.translatable("screen.grandcraft.sheet.skill_node",
						lineName(node.line()), node.tier() + 1);
	}

	private static Component stateName(SkillNodeState state) {
		return Component.translatable(
				"screen.grandcraft.sheet.skill_state." + state.name().toLowerCase(Locale.ROOT));
	}

	/**
	 * Names a line for the tooltip.
	 *
	 * <p>Generic, not {@link SkillLine#displayName()}: the lines will be named after
	 * abilities that do not exist yet, so the per-line keys have nothing to say. When
	 * they do, this method is the one line that changes. See {@link SkillNode} for the
	 * full reasoning.
	 */
	private static Component lineName(int line) {
		return Component.translatable("screen.grandcraft.sheet.skill_line", line + 1);
	}

	// ------------------------------------------------------------------- drawing

	/**
	 * Draws the heading, the connectors and the boxes, in that order — the connectors
	 * run to the middle of a node's edge, so the boxes have to go over them.
	 */
	public void extract(GuiGraphicsExtractor extractor, int mouseX, int mouseY) {
		extractor.centeredText(this.font,
				Component.translatable("screen.grandcraft.sheet.skill_lines"),
				this.headingX, this.headingY, HEADING_COLOUR);

		drawConnectors(extractor);

		for (NodeBox box : this.boxes) {
			drawNode(extractor, box, hovered(box, mouseX, mouseY));
		}
	}

	private void drawConnectors(GuiGraphicsExtractor extractor) {
		int rootBottom = this.rowTop[0] + this.nodeSize;
		int firstTierTop = this.rowTop[1];

		// Down out of the root, across to both outer lines, then down into each line's
		// first node. The middle line's drop shares the root's centre line, which is
		// correct and needs no special case.
		vertical(extractor, this.rootCentreX, rootBottom, this.busY);
		horizontal(extractor, this.columnCentreX[0], this.columnCentreX[SkillTree.LINES - 1], this.busY);

		for (int line = 0; line < SkillTree.LINES; line++) {
			vertical(extractor, this.columnCentreX[line], this.busY, firstTierTop);

			// Straight down the line, from each node's bottom edge to the next one's top.
			for (int tier = 0; tier < SkillTree.NODES_PER_LINE - 1; tier++) {
				vertical(extractor, this.columnCentreX[line],
						this.rowTop[tier + 1] + this.nodeSize, this.rowTop[tier + 2]);
			}
		}
	}

	private static void vertical(GuiGraphicsExtractor extractor, int x, int top, int bottom) {
		int half = CONNECTOR_THICKNESS / 2;

		extractor.fill(x - half, top, x - half + CONNECTOR_THICKNESS, bottom, CONNECTOR);
	}

	/**
	 * A horizontal run between two verticals, given their centre lines.
	 *
	 * <p>Extended by half a thickness at each end so it covers the full width of the
	 * verticals it meets. Stopping on their centre lines instead leaves a one-pixel
	 * notch in both outer corners, which at these sizes is the difference between a
	 * drawn join and a rendering fault.
	 *
	 * @param left  centre line of the leftmost vertical this run joins
	 * @param right centre line of the rightmost one
	 */
	private static void horizontal(GuiGraphicsExtractor extractor, int left, int right, int y) {
		int half = CONNECTOR_THICKNESS / 2;

		extractor.fill(left - half, y - half,
				right - half + CONNECTOR_THICKNESS, y - half + CONNECTOR_THICKNESS, CONNECTOR);
	}

	/**
	 * A box: the border as a filled square, then the inside laid over it one pixel in.
	 * Two fills rather than four, exactly as the radial menu draws a slot.
	 *
	 * <p>An in-reach node then gets its milestone drawn as a third fill rising from the
	 * bottom edge. That is the one thing on this panel that moves while you play, and it
	 * exists because a node with no artwork and no ability has otherwise nothing to say
	 * — {@code tuning.md} lesson 4 is that a mechanic nobody can perceive gets reported
	 * as a broken one, and "counting, but not there yet" is exactly that state.
	 *
	 * <p>The root then gets the class picture on top, which is what says whose lines
	 * these are — the same image the browser pages through and the same badge that sits
	 * under the model, so the sheet pictures a class one way.
	 */
	private void drawNode(GuiGraphicsExtractor extractor, NodeBox box, boolean hovered) {
		int right = box.left + this.nodeSize;
		int bottom = box.top + this.nodeSize;
		int border = hovered ? BORDER_HOVERED : borderOf(box.state);

		if (box.isRound()) {
			int radius = this.nodeSize / 2;
			int cx = box.left + radius;
			int cy = box.top + radius;

			disc(extractor, cx, cy, radius, border);
			disc(extractor, cx, cy, radius - 1, innerOf(box.state));

			if (box.fill > 0.0F) {
				// Clipped to the disc by drawing it as a shortened disc from the bottom —
				// the same rows, only the lower ones.
				int height = Math.round(box.fill * (this.nodeSize - 2));

				discBottom(extractor, cx, cy, radius - 1, height, INNER_PROGRESS);
			}
		} else {
			extractor.fill(box.left, box.top, right, bottom, border);
			extractor.fill(box.left + 1, box.top + 1, right - 1, bottom - 1, innerOf(box.state));

			if (box.fill > 0.0F) {
				// Rounded rather than truncated, so a milestone one tick from done draws as
				// full rather than as one pixel short of it.
				int height = Math.round(box.fill * (this.nodeSize - 2));

				if (height > 0) {
					extractor.fill(box.left + 1, bottom - 1 - height, right - 1, bottom - 1,
							INNER_PROGRESS);
				}
			}
		}

		if (box.node.isRoot()) {
			// The whole texture into what is left inside the frame: u and v are zero and
			// the source size is given as the destination size, which is how vanilla asks
			// for a sprite scaled to fit rather than a region cut out of one.
			int size = this.nodeSize - ICON_INSET * 2;

			extractor.blit(RenderPipelines.GUI_TEXTURED,
					GrandCraftScreen.classIcon(this.tree.playerClass()),
					box.left + ICON_INSET, box.top + ICON_INSET, 0.0F, 0.0F,
					size, size, size, size);
		}

		// The key it is on, written in the node itself rather than in a row of slot boxes
		// somewhere else. There is no vertical budget for such a row, and this answers
		// "what is on key 2" and "is this equipped" with the same mark. Only an unlocked
		// node can be equipped and an unlocked node has no progress bar, so the two never
		// fight over the same pixels.
		if (box.slot >= 0) {
			extractor.centeredText(this.font, Component.literal(Integer.toString(box.slot + 1)),
					box.left + this.nodeSize / 2, box.top + (this.nodeSize - LINE_HEIGHT) / 2,
					SLOT_NUMBER_COLOUR);
		}
	}

	/**
	 * A filled circle, drawn as one horizontal {@code fill} per row.
	 *
	 * <p>The extract pipeline draws rectangles and nothing else, so a circle is spans or
	 * it is artwork. Spans, because the schema's circles have no art and because a
	 * chunky pixel circle is the right register for this game anyway. About two fills
	 * per pixel of diameter, three times over — trivial for a screen that is not the
	 * world.
	 */
	private static void disc(GuiGraphicsExtractor extractor, int centreX, int centreY,
			int radius, int colour) {
		if (radius <= 0) {
			return;
		}

		for (int dy = -radius; dy <= radius; dy++) {
			int half = (int) Math.sqrt((double) radius * radius - (double) dy * dy);

			extractor.fill(centreX - half, centreY + dy, centreX + half + 1, centreY + dy + 1,
					colour);
		}
	}

	/**
	 * The bottom {@code height} pixels of a disc — the round form of the progress bar,
	 * so an ultimate fills up the way a node does without spilling outside its outline.
	 */
	private static void discBottom(GuiGraphicsExtractor extractor, int centreX, int centreY,
			int radius, int height, int colour) {
		if (radius <= 0 || height <= 0) {
			return;
		}

		int top = centreY + radius - height;

		for (int dy = -radius; dy <= radius; dy++) {
			if (centreY + dy < top) {
				continue;
			}

			int half = (int) Math.sqrt((double) radius * radius - (double) dy * dy);

			extractor.fill(centreX - half, centreY + dy, centreX + half + 1, centreY + dy + 1,
					colour);
		}
	}

	private static int borderOf(SkillNodeState state) {
		return switch (state) {
			case LOCKED -> BORDER_LOCKED;
			case IN_REACH -> BORDER_IN_REACH;
			case UNLOCKED -> BORDER_UNLOCKED;
		};
	}

	private static int innerOf(SkillNodeState state) {
		return switch (state) {
			case LOCKED -> INNER_LOCKED;
			case IN_REACH -> INNER_IN_REACH;
			case UNLOCKED -> INNER_UNLOCKED;
		};
	}

	/**
	 * Whether the mouse is on a node.
	 *
	 * <p>A circle is hit-tested as a circle rather than as its bounding square. The
	 * corners are nearly a third of that square, and an ultimate that highlights while
	 * the cursor is visibly outside it reads as a misaligned hit box — which, on the one
	 * element drawn in a different shape from everything else, is exactly the thing that
	 * would be reported.
	 */
	private boolean hovered(NodeBox box, double mouseX, double mouseY) {
		if (box.isRound()) {
			int radius = this.nodeSize / 2;
			double dx = mouseX - (box.left + radius);
			double dy = mouseY - (box.top + radius);

			return dx * dx + dy * dy <= (double) radius * radius;
		}

		return mouseX >= box.left && mouseX < box.left + this.nodeSize
				&& mouseY >= box.top && mouseY < box.top + this.nodeSize;
	}
}

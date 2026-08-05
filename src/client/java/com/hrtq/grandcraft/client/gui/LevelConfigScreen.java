package com.hrtq.grandcraft.client.gui;

import com.hrtq.grandcraft.network.ApplyLevelConfigPayload;
import com.hrtq.grandcraft.progression.LevelSettings;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ScrollableLayout;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Admin screen for the Essence Power curve and drop rates, opened by
 * {@code /grandcraft config levels}.
 *
 * <p>Shipped in the same slice as the mechanic rather than after it, which is the
 * working method the tuning notes settled on: a progression curve is exactly the kind
 * of thing nobody gets right on the first attempt, and every value that is not on a
 * screen costs a rebuild each time it feels wrong.
 *
 * <p>Same shape as {@link StatConfigScreen} — one section, no tab row, typed
 * whole-number fields in explicit grid cells. Purely an editor: the server sends the
 * current values, Save sends the edited set back, and the server re-checks
 * permissions and clamps on arrival.
 */
public class LevelConfigScreen extends Screen {
	private static final int LABEL_WIDTH = 150;
	private static final int FIELD_WIDTH = 54;
	private static final int FIELD_HEIGHT = 16;
	private static final int BUTTON_WIDTH = 65;
	private static final int CELL_SPACING = 4;
	private static final int SECTION_SPACING = 8;

	/**
	 * Vertical space the fixed furniture needs: the title, the button row and the gaps
	 * around them, plus a margin. Subtracted from the window height to decide how tall
	 * the field list may grow before it scrolls.
	 *
	 * <p>Smaller than {@code WeaponConfigScreen}'s because this screen has no tab row
	 * and no description under it. Over-reserving costs a little scroll; under-reserving
	 * is the clipping this exists to stop.
	 */
	private static final int CHROME_HEIGHT = 80;

	/** Never shrink the field list to nothing, however short the window is. */
	private static final int MIN_CONTENT_HEIGHT = 60;

	private TunableField baseCost;
	private TunableField costPerLevel;
	private TunableField dropWeight1;
	private TunableField dropWeight2;
	private TunableField dropWeight3;
	private TunableField statPointsPerLevel;
	private TunableField milestoneInterval;
	private TunableField poolPointsPerMilestone;
	private TunableField skillTier1Level;
	private TunableField skillTier2Level;
	private TunableField skillTier3Level;
	private TunableField skillTier4Level;

	/** What the fields held before a rebuild, so a resize does not discard edits. */
	private LevelSettings working;

	public LevelConfigScreen(LevelSettings settings) {
		super(Component.translatable("screen.grandcraft.levels.title"));
		this.working = settings;
	}

	@Override
	protected void init() {
		// Bank whatever is on screen first. init() runs on window resize too, so
		// without this a resize would silently reset every field.
		if (this.baseCost != null) {
			this.working = readFields();
		}

		LevelSettings seed = this.working;

		LinearLayout root = LinearLayout.vertical().spacing(SECTION_SPACING);
		root.addChild(new StringWidget(this.title, this.font));

		GridLayout grid = new GridLayout().spacing(CELL_SPACING);
		int row = 0;

		// Floors of one on the two fields whose clamps have one, so a value the server
		// would quietly raise shows red here instead of looking accepted.
		this.baseCost = unit(seed.baseCost(), 1, LevelSettings.MAX_COST, "essence");
		row = addRow(grid, row, "base_cost", this.baseCost);

		this.costPerLevel = unit(seed.costPerLevel(), LevelSettings.MAX_COST_PER_LEVEL, "essence");
		row = addRow(grid, row, "cost_per_level", this.costPerLevel);

		this.dropWeight1 = weight(seed.dropWeight1());
		row = addRow(grid, row, "drop_weight_1", this.dropWeight1);

		this.dropWeight2 = weight(seed.dropWeight2());
		row = addRow(grid, row, "drop_weight_2", this.dropWeight2);

		this.dropWeight3 = weight(seed.dropWeight3());
		row = addRow(grid, row, "drop_weight_3", this.dropWeight3);

		this.statPointsPerLevel = unit(seed.statPointsPerLevel(), LevelSettings.MAX_POINTS, "points");
		row = addRow(grid, row, "stat_points_per_level", this.statPointsPerLevel);

		this.milestoneInterval = unit(seed.milestoneInterval(), 1,
				LevelSettings.MAX_MILESTONE_INTERVAL, "levels");
		row = addRow(grid, row, "milestone_interval", this.milestoneInterval);

		this.poolPointsPerMilestone = unit(seed.poolPointsPerMilestone(),
				LevelSettings.MAX_POINTS, "points");
		row = addRow(grid, row, "pool_points_per_milestone", this.poolPointsPerMilestone);

		// The four skill-line gates. On this screen rather than a screen of their own
		// because they are only judgeable against the cost curve above them — how long a
		// gate takes is the curve's answer, not the gate's, and splitting them would put
		// the two halves of one decision on two screens.
		this.skillTier1Level = level(seed.skillTier1Level());
		row = addRow(grid, row, "skill_tier_1_level", this.skillTier1Level);

		this.skillTier2Level = level(seed.skillTier2Level());
		row = addRow(grid, row, "skill_tier_2_level", this.skillTier2Level);

		this.skillTier3Level = level(seed.skillTier3Level());
		row = addRow(grid, row, "skill_tier_3_level", this.skillTier3Level);

		this.skillTier4Level = level(seed.skillTier4Level());
		addRow(grid, row, "skill_tier_4_level", this.skillTier4Level);

		// Scrolled, for the reason WeaponConfigScreen already documents: eight rows was
		// enough to clip off the bottom of a small window there, and the four skill gates
		// take this screen to twelve. The container is a widget in its own right and
		// renders and routes events to its contents, so only it is registered below —
		// the fields inside must not also be added or they would draw a second time
		// outside the clip.
		ScrollableLayout values = new ScrollableLayout(this.minecraft, grid,
				Math.max(MIN_CONTENT_HEIGHT, this.height - CHROME_HEIGHT),
				// Reserve the scrollbar's width on both sides, so the rows stay centred
				// whether or not the bar is showing.
				ScrollableLayout.ReserveStrategy.BOTH);

		root.addChild(values);
		root.addChild(buildButtons());

		root.arrangeElements();
		FrameLayout.centerInRectangle(root, 0, 0, this.width, this.height);
		root.visitWidgets(this::addRenderableWidget);
	}

	private int addRow(GridLayout grid, int row, String key, TunableField field) {
		grid.addChild(label(key), row, 0);
		grid.addChild(field, row, 1);
		return row + 1;
	}

	private StringWidget label(String key) {
		StringWidget widget = new StringWidget(LABEL_WIDTH, FIELD_HEIGHT,
				Component.translatable("screen.grandcraft.levels." + key), this.font);

		// Every setting here needs explaining: the three weights are relative rather
		// than percentages, and the cost fields describe a curve rather than a value.
		widget.setTooltip(Tooltip.create(
				Component.translatable("screen.grandcraft.levels." + key + ".tooltip")));

		return widget;
	}

	/** A relative share of the drop roll, not a percentage — see the tooltips. */
	private TunableField weight(int value) {
		return unit(value, LevelSettings.MAX_WEIGHT, "weight");
	}

	/**
	 * An Essence Power level a skill-line tier waits for.
	 *
	 * <p>Floor of zero rather than one, matching the server's clamp: zero is how a tier
	 * is opened outright, which is the quickest way to look at what is in it.
	 */
	private TunableField level(int value) {
		return unit(value, LevelSettings.MAX_SKILL_GATE, "level");
	}

	/** The unit key names the field for narration; every field is a whole number. */
	private TunableField unit(int value, int max, String unitKey) {
		return unit(value, 0, max, unitKey);
	}

	private TunableField unit(int value, int min, int max, String unitKey) {
		return new TunableField(this.font, FIELD_WIDTH, FIELD_HEIGHT,
				Component.translatable("screen.grandcraft.config." + unitKey), min, max, value);
	}

	private LinearLayout buildButtons() {
		LinearLayout buttons = LinearLayout.horizontal().spacing(CELL_SPACING);

		buttons.addChild(Button.builder(
						Component.translatable("screen.grandcraft.config.save"), button -> save())
				.width(BUTTON_WIDTH).build());

		Button reset = Button.builder(
						Component.translatable("screen.grandcraft.config.reset"), button -> reset())
				.width(BUTTON_WIDTH).build();
		reset.setTooltip(Tooltip.create(
				Component.translatable("screen.grandcraft.config.reset.tooltip")));
		buttons.addChild(reset);

		buttons.addChild(Button.builder(
						Component.translatable("screen.grandcraft.config.cancel"), button -> onClose())
				.width(BUTTON_WIDTH).build());

		return buttons;
	}

	private LevelSettings readFields() {
		return new LevelSettings(
				this.baseCost.intValue(),
				this.costPerLevel.intValue(),
				this.dropWeight1.intValue(),
				this.dropWeight2.intValue(),
				this.dropWeight3.intValue(),
				this.statPointsPerLevel.intValue(),
				this.milestoneInterval.intValue(),
				this.poolPointsPerMilestone.intValue(),
				this.skillTier1Level.intValue(),
				this.skillTier2Level.intValue(),
				this.skillTier3Level.intValue(),
				this.skillTier4Level.intValue());
	}

	/** Back to the mod's defaults, not to what the screen opened with. */
	private void reset() {
		this.working = LevelSettings.DEFAULT;
		this.baseCost = null;
		rebuildWidgets();
	}

	private void save() {
		ClientPlayNetworking.send(new ApplyLevelConfigPayload(readFields()));
		onClose();
	}
}

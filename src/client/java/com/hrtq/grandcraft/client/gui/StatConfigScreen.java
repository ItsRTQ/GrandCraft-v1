package com.hrtq.grandcraft.client.gui;

import com.hrtq.grandcraft.network.ApplyStatConfigPayload;
import com.hrtq.grandcraft.stats.StatSettings;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Admin screen for what a point of a stat is worth, opened by
 * {@code /grandcraft config stats}.
 *
 * <p>Exists for the reason recorded in the tuning notes: a number that is not on a
 * screen is a number that costs a rebuild every time it feels wrong, and "slightly"
 * is not a value anyone gets right on the first attempt. Everything here is
 * deliberately settleable in one session rather than five.
 *
 * <p>One section, so no tab row — the same typed whole-number fields the combat
 * screen uses, laid out with explicit grid cells.
 *
 * <p>Purely an editor. The server sends the current values, Save sends the edited
 * set back, and the server re-checks permissions and clamps on arrival.
 */
public class StatConfigScreen extends Screen {
	private static final int LABEL_WIDTH = 150;
	private static final int FIELD_WIDTH = 54;
	private static final int FIELD_HEIGHT = 16;
	private static final int BUTTON_WIDTH = 65;
	private static final int CELL_SPACING = 4;
	private static final int SECTION_SPACING = 8;

	private TunableField armourPerConstitution;
	private TunableField healthPerConstitution;
	private TunableField staminaRegenPerConstitution;
	private TunableField staminaCostPerAgility;
	private TunableField maxMana;
	private TunableField manaRegenPerSecond;
	private TunableField manaRegenDelayTicks;
	private TunableField healthPerPoolPoint;
	private TunableField staminaPerPoolPoint;
	private TunableField manaPerPoolPoint;

	/** What the fields held before a rebuild, so a resize does not discard edits. */
	private StatSettings working;

	public StatConfigScreen(StatSettings settings) {
		super(Component.translatable("screen.grandcraft.stats.title"));
		this.working = settings;
	}

	@Override
	protected void init() {
		// Bank whatever is on screen first. init() runs on window resize too, so
		// without this a resize would silently reset every field.
		if (this.armourPerConstitution != null) {
			this.working = readFields();
		}

		StatSettings seed = this.working;

		LinearLayout root = LinearLayout.vertical().spacing(SECTION_SPACING);
		root.addChild(new StringWidget(this.title, this.font));

		GridLayout grid = new GridLayout().spacing(CELL_SPACING);
		int row = 0;

		this.armourPerConstitution = hundredths(seed.armourPerConstitution(),
				StatSettings.MAX_ARMOUR_PER_CONSTITUTION);
		row = addRow(grid, row, "armour_per_constitution", this.armourPerConstitution);

		this.healthPerConstitution = hundredths(seed.healthPerConstitution(),
				StatSettings.MAX_HEALTH_PER_CONSTITUTION);
		row = addRow(grid, row, "health_per_constitution", this.healthPerConstitution);

		this.staminaRegenPerConstitution = percent(seed.staminaRegenPerConstitution());
		row = addRow(grid, row, "stamina_regen_per_constitution", this.staminaRegenPerConstitution);

		this.staminaCostPerAgility = percent(seed.staminaCostPerAgility());
		row = addRow(grid, row, "stamina_cost_per_agility", this.staminaCostPerAgility);

		this.maxMana = unit(seed.maxMana(), StatSettings.MAX_MANA, "stamina_points");
		row = addRow(grid, row, "max_mana", this.maxMana);

		this.manaRegenPerSecond = unit(seed.manaRegenPerSecond(), StatSettings.MAX_RATE, "per_second");
		row = addRow(grid, row, "mana_regen_per_second", this.manaRegenPerSecond);

		this.manaRegenDelayTicks = unit(seed.manaRegenDelayTicks(),
				StatSettings.MAX_DELAY_TICKS, "ticks");
		row = addRow(grid, row, "mana_regen_delay_ticks", this.manaRegenDelayTicks);

		this.healthPerPoolPoint = unit(seed.healthPerPoolPoint(),
				StatSettings.MAX_PER_POOL_POINT, "health_points");
		row = addRow(grid, row, "health_per_pool_point", this.healthPerPoolPoint);

		this.staminaPerPoolPoint = unit(seed.staminaPerPoolPoint(),
				StatSettings.MAX_PER_POOL_POINT, "stamina_points");
		row = addRow(grid, row, "stamina_per_pool_point", this.staminaPerPoolPoint);

		this.manaPerPoolPoint = unit(seed.manaPerPoolPoint(),
				StatSettings.MAX_PER_POOL_POINT, "mana_points");
		addRow(grid, row, "mana_per_pool_point", this.manaPerPoolPoint);

		root.addChild(grid);
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
				Component.translatable("screen.grandcraft.stats." + key), this.font);

		// Every setting here needs explaining: each one is priced per point above the
		// neutral value, which is not something a label can carry.
		widget.setTooltip(Tooltip.create(
				Component.translatable("screen.grandcraft.stats." + key + ".tooltip")));

		return widget;
	}

	/** A value typed in hundredths, so a fractional effect stays a whole number. */
	private TunableField hundredths(int value, int max) {
		return unit(value, max, "hundredths_per_point");
	}

	private TunableField percent(int value) {
		return unit(value, StatSettings.MAX_PERCENT_PER_POINT, "percent");
	}

	/** The unit key names the field for narration; every field is a whole number. */
	private TunableField unit(int value, int max, String unitKey) {
		return new TunableField(this.font, FIELD_WIDTH, FIELD_HEIGHT,
				Component.translatable("screen.grandcraft.config." + unitKey), 0, max, value);
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

	private StatSettings readFields() {
		return new StatSettings(
				this.armourPerConstitution.intValue(),
				this.healthPerConstitution.intValue(),
				this.staminaRegenPerConstitution.intValue(),
				this.staminaCostPerAgility.intValue(),
				this.maxMana.intValue(),
				this.manaRegenPerSecond.intValue(),
				this.manaRegenDelayTicks.intValue(),
				this.healthPerPoolPoint.intValue(),
				this.staminaPerPoolPoint.intValue(),
				this.manaPerPoolPoint.intValue());
	}

	/** Back to the mod's defaults, not to what the screen opened with. */
	private void reset() {
		this.working = StatSettings.DEFAULT;
		this.armourPerConstitution = null;
		rebuildWidgets();
	}

	private void save() {
		ClientPlayNetworking.send(new ApplyStatConfigPayload(readFields()));
		onClose();
	}
}

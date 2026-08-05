package com.hrtq.grandcraft.client.gui;

import com.hrtq.grandcraft.network.ApplySkillConfigPayload;
import com.hrtq.grandcraft.skill.SkillSettings;
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
 * Admin screen for what the skill-line abilities are worth, opened by
 * {@code /grandcraft config skills}.
 *
 * <p>Shipped in the same slice as the first ability rather than after it, which is the
 * working method the tuning notes settled on and one this project already paid for: an
 * ability's numbers are exactly the kind nobody gets right first time, and every value
 * that is not on a screen costs a rebuild each time it feels wrong.
 *
 * <p>Same shape as {@link LevelConfigScreen} — one section, no tab row, typed
 * whole-number fields in explicit grid cells, and a scroll area so the list can grow as
 * abilities are added without ever clipping off a short window. Purely an editor: the
 * server sends the current values, Save sends the edited set back, and the server
 * re-checks permissions and clamps on arrival.
 *
 * <p><strong>This screen will grow one section per ability.</strong> It is deliberately
 * built for that from the start, which is why the scroll area is here on day one with
 * only three rows in it.
 */
public class SkillConfigScreen extends Screen {
	private static final int LABEL_WIDTH = 170;
	private static final int FIELD_WIDTH = 54;
	private static final int FIELD_HEIGHT = 16;
	private static final int BUTTON_WIDTH = 65;
	private static final int CELL_SPACING = 4;
	private static final int SECTION_SPACING = 8;

	/** Title and buttons plus their gaps; see {@code WeaponConfigScreen}'s own note. */
	private static final int CHROME_HEIGHT = 80;
	private static final int MIN_CONTENT_HEIGHT = 60;

	private TunableField combatMasterDamage;
	private TunableField combatMasterSpeed;
	private TunableField combatMasterWindow;

	/** What the fields held before a rebuild, so a resize does not discard edits. */
	private SkillSettings working;

	public SkillConfigScreen(SkillSettings settings) {
		super(Component.translatable("screen.grandcraft.skills.title"));
		this.working = settings;
	}

	@Override
	protected void init() {
		// Bank whatever is on screen first. init() runs on window resize too, so
		// without this a resize would silently reset every field.
		if (this.combatMasterDamage != null) {
			this.working = readFields();
		}

		SkillSettings seed = this.working;

		LinearLayout root = LinearLayout.vertical().spacing(SECTION_SPACING);
		root.addChild(new StringWidget(this.title, this.font));

		GridLayout grid = new GridLayout().spacing(CELL_SPACING);
		int row = 0;

		this.combatMasterDamage = unit(seed.combatMasterDamagePercent(),
				SkillSettings.MAX_DAMAGE_PERCENT, "percent");
		row = addRow(grid, row, "combat_master_damage", this.combatMasterDamage);

		this.combatMasterSpeed = unit(seed.combatMasterSpeedPercent(),
				SkillSettings.MAX_SPEED_PERCENT, "percent");
		row = addRow(grid, row, "combat_master_speed", this.combatMasterSpeed);

		this.combatMasterWindow = unit(seed.combatMasterWindowTicks(),
				SkillSettings.MAX_WINDOW_TICKS, "ticks");
		addRow(grid, row, "combat_master_window", this.combatMasterWindow);

		ScrollableLayout values = new ScrollableLayout(this.minecraft, grid,
				Math.max(MIN_CONTENT_HEIGHT, this.height - CHROME_HEIGHT),
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
				Component.translatable("screen.grandcraft.skills." + key), this.font);

		// Every field here needs explaining: none of them is meaningful without knowing
		// what opens and closes the window they belong to.
		widget.setTooltip(Tooltip.create(
				Component.translatable("screen.grandcraft.skills." + key + ".tooltip")));

		return widget;
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

	private SkillSettings readFields() {
		return new SkillSettings(
				this.combatMasterDamage.intValue(),
				this.combatMasterSpeed.intValue(),
				this.combatMasterWindow.intValue());
	}

	/** Back to the mod's defaults, not to what the screen opened with. */
	private void reset() {
		this.working = SkillSettings.DEFAULT;
		this.combatMasterDamage = null;
		rebuildWidgets();
	}

	private void save() {
		ClientPlayNetworking.send(new ApplySkillConfigPayload(readFields()));
		onClose();
	}
}

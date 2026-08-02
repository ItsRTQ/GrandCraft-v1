package com.hrtq.grandcraft.client.gui;

import com.hrtq.grandcraft.combat.ArcaneSettings;
import com.hrtq.grandcraft.combat.CategorySettings;
import com.hrtq.grandcraft.combat.WeaponCategory;
import com.hrtq.grandcraft.combat.WeaponSettings;
import com.hrtq.grandcraft.network.ApplyWeaponConfigPayload;
import java.util.EnumMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
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
 * Admin screen for the weapon tuning values, opened by
 * {@code /grandcraft config weapons}.
 *
 * <p>One tab per {@link WeaponCategory}, built from {@code WeaponCategory.values()},
 * so a new category gains a tab with no change here. Tabs run in declaration order,
 * which is deliberately the order that reads well — light, medium, heavy, then the
 * special cases — rather than the order tags are matched in.
 *
 * <h2>Saying what it edits</h2>
 * The first version of this screen was reported as "super confusing", and read as
 * though each tab were a single weapon. It was always per-category, so the failure
 * was entirely in the presentation. Three things fix it, and they are the reason this
 * screen deviates from {@link CombatConfigScreen}:
 *
 * <ul>
 * <li>A subtitle stating outright that a value applies to every weapon in the
 *     category.</li>
 * <li>A line under the tab row naming what the open category actually contains, so
 *     "Heavy" is visibly a group rather than an item.</li>
 * <li><strong>No sections.</strong> The combat screen splits its tabs into groups
 *     because an actor has twenty-five values; a category has two, or eight for the
 *     one that casts. A section row over eight rows is a control that only adds a
 *     concept to learn, and it made the arcane tab behave differently from every
 *     other tab for no benefit. The value list is wrapped in a
 *     {@link ScrollableLayout} instead, which solves the height problem for every
 *     tab and every window size rather than for the one tab that had outgrown the
 *     screen.</li>
 * </ul>
 *
 * <p><strong>Startup and the hit window are still not shown</strong>, even though
 * {@link CategorySettings} stores them. Nothing reads them until the player gets real
 * attack phases, and a field that does nothing is worse than no field — someone will
 * tune it and report the mechanic as broken.
 *
 * <p>Purely an editor. The server sends the current values, Save sends the edited set
 * back, and the server re-checks permissions and clamps on arrival.
 */
public class WeaponConfigScreen extends Screen {
	private static final int LABEL_WIDTH = 96;
	private static final int FIELD_WIDTH = 54;
	private static final int FIELD_HEIGHT = 16;
	private static final int TAB_WIDTH = 62;
	private static final int BUTTON_WIDTH = 65;
	private static final int CELL_SPACING = 4;
	private static final int SECTION_SPACING = 8;

	/**
	 * Vertical space the fixed furniture needs: title, subtitle, tab row, the category
	 * description, the button row, and the gaps between them, plus a margin.
	 *
	 * <p>Subtracted from the window height to decide how tall the value list may grow
	 * before it scrolls. Deliberately generous — over-reserving costs a little scroll,
	 * while under-reserving is the clipping this exists to stop.
	 */
	private static final int CHROME_HEIGHT = 130;

	/** Never shrink the value list to nothing, however short the window is. */
	private static final int MIN_CONTENT_HEIGHT = 60;

	/** Every category's values as currently edited, including tabs not on screen. */
	private final Map<WeaponCategory, CategorySettings> working =
			new EnumMap<>(WeaponCategory.class);

	/**
	 * Opens on the baseline rather than on whatever happens to be declared first.
	 * Medium is the category every other one is tuned against, so it is the useful
	 * thing to be looking at first.
	 */
	private WeaponCategory activeTab = WeaponCategory.MEDIUM;

	/**
	 * Which category the fields on screen belong to, or null before the first build.
	 *
	 * <p>Tracked separately from {@link #activeTab} so {@link #init()} can bank the
	 * visible fields into the right entry: on a tab switch {@code activeTab} has
	 * already moved on, and writing to it would copy one category's values over
	 * another's. It also runs on window resize, where losing edits would be just as
	 * surprising.
	 */
	private WeaponCategory fieldsFor;

	private TunableField endlag;
	private TunableField staminaCost;

	/**
	 * The cast fields, present only on a category that casts.
	 *
	 * <p>Nulled rather than left stale when they are absent, because
	 * {@link #readFields()} reads them unconditionally otherwise and would write one
	 * category's cast values onto another's.
	 */
	private TunableField manaCost;
	private TunableField baseDamage;
	private TunableField projectileSpeed;
	private TunableField knockback;
	private TunableField cooldown;
	private TunableField range;

	public WeaponConfigScreen(WeaponSettings settings) {
		super(Component.translatable("screen.grandcraft.weapons.title"));

		for (WeaponCategory category : WeaponCategory.values()) {
			this.working.put(category, settings.forCategory(category));
		}
	}

	@Override
	protected void init() {
		// Bank whatever is on screen first. This runs on window resize as well as on a
		// tab switch, so without it either would silently discard edits.
		if (this.fieldsFor != null) {
			this.working.put(this.fieldsFor, readFields());
		}

		WeaponCategory category = this.activeTab;
		CategorySettings seed = this.working.get(category);

		LinearLayout root = LinearLayout.vertical().spacing(SECTION_SPACING);
		root.addChild(new StringWidget(this.title, this.font));

		// The whole point of the screen, said once at the top: a value here is a rule
		// about a class of weapon, not a property of one.
		root.addChild(new StringWidget(
				Component.translatable("screen.grandcraft.weapons.subtitle")
						.withStyle(ChatFormatting.GRAY),
				this.font));

		root.addChild(buildTabs(category));

		// And said again for the open tab specifically, naming what is in it.
		root.addChild(new StringWidget(
				category.description().withStyle(ChatFormatting.GRAY), this.font));

		// Scrolled rather than split into sections. The arcane tab's eight rows clipped
		// off the bottom of the window, and a scroll area fixes that for every tab at
		// every window size at once — including whatever the next category adds — where
		// a section row would only ever fix the one tab that had outgrown the screen.
		//
		// The container is a widget in its own right and renders and routes events to
		// its contents, so only it is registered below; the fields inside must not also
		// be added or they would draw a second time outside the clip.
		ScrollableLayout values = new ScrollableLayout(this.minecraft, buildGrid(category, seed),
				Math.max(MIN_CONTENT_HEIGHT, this.height - CHROME_HEIGHT),
				// Reserve the scrollbar's width on both sides, so the rows stay centred
				// whether or not the bar is showing and the layout does not jump
				// sideways when switching to the one tab that needs it.
				ScrollableLayout.ReserveStrategy.BOTH);

		root.addChild(values);
		root.addChild(buildButtons());

		root.arrangeElements();
		FrameLayout.centerInRectangle(root, 0, 0, this.width, this.height);
		root.visitWidgets(this::addRenderableWidget);

		this.fieldsFor = category;
	}

	/**
	 * Every value for the open category, in one grid.
	 *
	 * <p>Two rows for a category that only swings, eight for the one that casts —
	 * which is fewer than the combat screen's guard group and needs no splitting.
	 * Ordered the way someone tuning it would work: what a swing commits you to, what
	 * it costs, then the cast.
	 */
	private GridLayout buildGrid(WeaponCategory category, CategorySettings seed) {
		GridLayout grid = new GridLayout().spacing(CELL_SPACING);
		int row = 0;

		this.endlag = ticks(seed.recoveryTicks(), CategorySettings.MAX_PHASE_TICKS);
		row = addValue(grid, row, "endlag", this.endlag);

		this.staminaCost = unit(seed.staminaCost(), CategorySettings.MAX_COST, "stamina_points");
		row = addValue(grid, row, "stamina_cost", this.staminaCost);

		if (!category.casts()) {
			// Cleared rather than left stale: readFields() uses null to mean "this
			// category has no cast values of its own to read".
			this.manaCost = null;
			this.baseDamage = null;
			this.projectileSpeed = null;
			this.knockback = null;
			this.cooldown = null;
			this.range = null;
			return grid;
		}

		ArcaneSettings arcane = seed.arcane();

		this.manaCost = unit(arcane.manaCost(), ArcaneSettings.MAX_COST, "mana_points");
		row = addValue(grid, row, "mana_cost", this.manaCost);

		this.baseDamage = weaponUnit(arcane.baseDamage(), ArcaneSettings.MAX_DAMAGE,
				"hundredths_damage");
		row = addValue(grid, row, "base_damage", this.baseDamage);

		this.projectileSpeed = unit(arcane.projectileSpeed(), ArcaneSettings.MAX_SPEED,
				"hundredths_per_tick");
		row = addValue(grid, row, "projectile_speed", this.projectileSpeed);

		this.knockback = unit(arcane.knockback(), ArcaneSettings.MAX_KNOCKBACK,
				"hundredths_per_tick");
		row = addValue(grid, row, "knockback", this.knockback);

		this.cooldown = ticks(arcane.cooldownTicks(), ArcaneSettings.MAX_COOLDOWN_TICKS);
		row = addValue(grid, row, "cooldown", this.cooldown);

		this.range = ticks(arcane.rangeTicks(), ArcaneSettings.MAX_RANGE_TICKS);
		addValue(grid, row, "range", this.range);

		return grid;
	}

	private int addValue(GridLayout grid, int row, String key, TunableField field) {
		grid.addChild(label(key), row, 0);
		grid.addChild(field, row, 1);
		return row + 1;
	}

	/**
	 * Every label here carries a tooltip. Unlike the combat screen there is no
	 * self-explanatory subset — "Endlag" and "Per tick x100" both need saying what
	 * they do to a fight.
	 */
	private StringWidget label(String key) {
		StringWidget widget = new StringWidget(LABEL_WIDTH, FIELD_HEIGHT,
				Component.translatable("screen.grandcraft.weapons." + key), this.font);

		widget.setTooltip(Tooltip.create(
				Component.translatable("screen.grandcraft.weapons." + key + ".tooltip")));
		return widget;
	}

	private TunableField ticks(int value, int max) {
		return unit(value, max, "ticks");
	}

	/** Units shared with the combat screen, so the two screens name things alike. */
	private TunableField unit(int value, int max, String unitKey) {
		return new TunableField(this.font, FIELD_WIDTH, FIELD_HEIGHT,
				Component.translatable("screen.grandcraft.config." + unitKey), 0, max, value);
	}

	/** A unit this screen introduces and the combat one has no use for. */
	private TunableField weaponUnit(int value, int max, String unitKey) {
		return new TunableField(this.font, FIELD_WIDTH, FIELD_HEIGHT,
				Component.translatable("screen.grandcraft.weapons." + unitKey), 0, max, value);
	}

	private LinearLayout buildTabs(WeaponCategory open) {
		LinearLayout tabs = LinearLayout.horizontal().spacing(CELL_SPACING);

		for (WeaponCategory tab : WeaponCategory.values()) {
			Button button = Button.builder(tab.displayName(), ignored -> selectTab(tab))
					.width(TAB_WIDTH).build();

			// The open tab is shown as an unusable button, which greys it out and makes
			// "you are here" obvious without a custom widget.
			button.active = tab != open;
			button.setTooltip(Tooltip.create(tab.description()));
			tabs.addChild(button);
		}

		return tabs;
	}

	private LinearLayout buildButtons() {
		LinearLayout buttons = LinearLayout.horizontal().spacing(CELL_SPACING);

		buttons.addChild(Button.builder(
						Component.translatable("screen.grandcraft.config.save"), button -> save())
				.width(BUTTON_WIDTH).build());

		Button resetTab = Button.builder(
						Component.translatable("screen.grandcraft.config.reset"), button -> resetActiveTab())
				.width(BUTTON_WIDTH).build();
		resetTab.setTooltip(Tooltip.create(
				Component.translatable("screen.grandcraft.config.reset.tooltip")));
		buttons.addChild(resetTab);

		buttons.addChild(Button.builder(
						Component.translatable("screen.grandcraft.config.cancel"), button -> onClose())
				.width(BUTTON_WIDTH).build());

		return buttons;
	}

	private void selectTab(WeaponCategory tab) {
		if (tab == this.activeTab) {
			return;
		}

		// init() banks the outgoing tab's fields via fieldsFor, so switching here is
		// just a rebuild.
		this.activeTab = tab;
		rebuildWidgets();
	}

	/**
	 * Reloads the visible tab with that category's shipped defaults. Deliberately does
	 * not save, and deliberately leaves other tabs alone — the player still has to
	 * press Save, so a mis-click is recoverable with Cancel.
	 */
	private void resetActiveTab() {
		this.working.put(this.activeTab, this.activeTab.defaults());

		// Drop the banked values so init() does not immediately overwrite the defaults
		// with the fields it is about to replace.
		this.fieldsFor = null;
		rebuildWidgets();
	}

	/**
	 * The visible tab's values as the fields currently stand.
	 *
	 * <p>Startup and the hit window are always carried through from what is stored,
	 * because this screen never shows them — as are the cast values for any category
	 * that does not cast.
	 */
	private CategorySettings readFields() {
		CategorySettings stored = this.working.get(this.fieldsFor);

		return new CategorySettings(
				stored.startupTicks(),
				stored.activeTicks(),
				this.endlag.intValue(),
				this.staminaCost.intValue(),
				this.manaCost == null ? stored.arcane() : new ArcaneSettings(
						this.manaCost.intValue(),
						this.baseDamage.intValue(),
						this.projectileSpeed.intValue(),
						this.knockback.intValue(),
						this.cooldown.intValue(),
						this.range.intValue()));
	}

	private void save() {
		this.working.put(this.activeTab, readFields());
		ClientPlayNetworking.send(new ApplyWeaponConfigPayload(new WeaponSettings(this.working)));
		onClose();
	}
}

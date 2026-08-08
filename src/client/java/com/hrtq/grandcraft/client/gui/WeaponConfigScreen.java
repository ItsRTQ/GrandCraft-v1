package com.hrtq.grandcraft.client.gui;

import com.hrtq.grandcraft.combat.ArcaneSettings;
import com.hrtq.grandcraft.combat.CategorySettings;
import com.hrtq.grandcraft.combat.WeaponCategory;
import com.hrtq.grandcraft.combat.WeaponRules;
import com.hrtq.grandcraft.combat.WeaponSettings;
import com.hrtq.grandcraft.network.ApplyWeaponConfigPayload;
import com.hrtq.grandcraft.stats.StatWeights;
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
 *     because an actor has twenty-five values; a category has seven, or thirteen for
 *     the one that casts. A section row is a control that only adds a concept to
 *     learn, and it made the arcane tab behave differently from every other tab for
 *     no benefit. The value list is wrapped in a {@link ScrollableLayout} instead,
 *     which solves the height problem for every tab and every window size rather than
 *     for the one tab that had outgrown the screen.</li>
 * </ul>
 *
 * <h2>The Rules page</h2>
 * {@link WeaponRules} applies to every weapon whatever kind it is, so it belongs to no
 * category and gets its own page in the same tab row. Deliberately a tab rather than
 * the section row the combat screen uses: a tab is a concept this screen already has,
 * and these genuinely are different values rather than a different view of the same
 * ones. It carries the same banking obligation as a category tab, which is what
 * {@link #fieldsAreRules} exists for.
 *
 * <p><strong>The two ends of the swing are back, as modifiers</strong> (2026-08-07).
 * They were absolutes here between 2026-08-05 and 2026-08-07, then hidden for a
 * fortnight once both ends became globals on {@code /grandcraft config combat} — a field
 * that does nothing is worse than no field, because someone will tune it and report the
 * mechanic as broken. What brought them back is that they now do something: a signed
 * offset from the global, which is how a greatsword telegraphs longer than a dagger
 * without either escaping the rhythm the player has learned. <strong>The hit window
 * between them is the one phase length still owned by the weapon</strong>, and it has to
 * be: it decides whether a telegraphed swing can connect at all, and it only means
 * anything against that weapon's own reach.
 *
 * <p>Purely an editor. The server sends the current values, Save sends the edited set
 * back, and the server re-checks permissions and clamps on arrival.
 */
public class WeaponConfigScreen extends Screen {
	private static final int LABEL_WIDTH = 96;
	private static final int FIELD_WIDTH = 54;
	private static final int FIELD_HEIGHT = 16;
	/**
	 * Narrowed from 62 when the Rules page made a seventh tab. Seven at the old width
	 * ran wider than the row had ever been; at 56 the whole row is within a few pixels
	 * of what six tabs already occupied, so nothing moves for existing users.
	 */
	private static final int TAB_WIDTH = 56;
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

	private TunableField windUpModifier;
	private TunableField hitWindow;
	private TunableField endlagModifier;
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

	/** The four scaling weights for the open category. */
	private TunableField weightStrength;
	private TunableField weightAgility;
	private TunableField weightConstitution;
	private TunableField weightArcane;

	/**
	 * Live running total of the four weights above.
	 *
	 * <p>Weights are ratios, so any total is legal and none is an error — but 100 is
	 * the only total where a weight can be read as a percentage at a glance, which is
	 * how everyone will read them regardless. This nudges towards it without refusing
	 * anything.
	 */
	private StringWidget weightTotal;

	/** The shared rules' fields, present only on the rules page. */
	private TunableField weaponBase;
	private TunableField failedDamage;

	/** The shared rules as currently edited. */
	private WeaponRules rules;

	/** Whether the rules page is open instead of a category. */
	private boolean rulesOpen;

	/**
	 * Whether the fields on screen belong to the rules page.
	 *
	 * <p>The companion to {@link #fieldsFor}, and separate from it because that field
	 * already uses null to mean "nothing built yet". Both are needed for the same
	 * reason: {@link #init()} has to bank the visible fields into the right place, and
	 * by the time it runs the page has already changed.
	 */
	private boolean fieldsAreRules;

	public WeaponConfigScreen(WeaponSettings settings) {
		super(Component.translatable("screen.grandcraft.weapons.title"));

		for (WeaponCategory category : WeaponCategory.values()) {
			this.working.put(category, settings.forCategory(category));
		}

		this.rules = settings.rules();
	}

	@Override
	protected void init() {
		// Bank whatever is on screen first. This runs on window resize as well as on a
		// tab switch, so without it either would silently discard edits.
		if (this.fieldsAreRules) {
			this.rules = readRules();
		} else if (this.fieldsFor != null) {
			this.working.put(this.fieldsFor, readFields());
		}

		WeaponCategory category = this.activeTab;

		LinearLayout root = LinearLayout.vertical().spacing(SECTION_SPACING);
		root.addChild(new StringWidget(this.title, this.font));

		// The whole point of the screen, said once at the top: a value here is a rule
		// about a class of weapon, not a property of one.
		root.addChild(new StringWidget(
				Component.translatable("screen.grandcraft.weapons.subtitle")
						.withStyle(ChatFormatting.GRAY),
				this.font));

		root.addChild(buildTabs());

		// And said again for the open tab specifically, naming what is in it.
		root.addChild(new StringWidget(
				(this.rulesOpen
						? Component.translatable("screen.grandcraft.weapons.rules.description")
						: category.description()).withStyle(ChatFormatting.GRAY),
				this.font));

		GridLayout grid = this.rulesOpen
				? buildRulesGrid()
				: buildGrid(category, this.working.get(category));

		// Scrolled rather than split into sections. The arcane tab's eight rows clipped
		// off the bottom of the window, and a scroll area fixes that for every tab at
		// every window size at once — including whatever the next category adds — where
		// a section row would only ever fix the one tab that had outgrown the screen.
		//
		// The container is a widget in its own right and renders and routes events to
		// its contents, so only it is registered below; the fields inside must not also
		// be added or they would draw a second time outside the clip.
		ScrollableLayout values = new ScrollableLayout(this.minecraft, grid,
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

		this.fieldsFor = this.rulesOpen ? null : category;
		this.fieldsAreRules = this.rulesOpen;
	}

	/**
	 * Every value for the open category, in one grid.
	 *
	 * <p>Eleven rows for a category that only swings, seventeen for the one that casts.
	 * Ordered the way someone tuning it would work: what a swing commits you to, what
	 * it costs, who it rewards, then the cast — and within the first of those, the order
	 * the swing itself happens in.
	 */
	private GridLayout buildGrid(WeaponCategory category, CategorySettings seed) {
		GridLayout grid = new GridLayout().spacing(CELL_SPACING);
		int row = 0;

		// The three rows of the swing, in the order it happens. The two ends are
		// MODIFIERS and the middle is a length, which is why they are not one kind of
		// row: the wind-up and the endlag are globals on /grandcraft config combat, and
		// what a category may do is bend that rhythm by a signed number of ticks. The
		// day that stopped being true — 2026-08-07, when the greatsword's telegraph
		// needed ten ticks and the global was five — is the day these rows came back
		// after a fortnight hidden. See Weapons.startupFor and Weapons.recoveryFor.
		//
		// They read zero for every category but Heavy, and a zero row is honest here in
		// a way a dead row never was: it says this weapon keeps the rhythm.
		this.windUpModifier = modifier(seed.startupModifier());
		row = addValue(grid, row, "wind_up_modifier", this.windUpModifier);

		// The hit window is the one phase length still owned by the weapon, and it is the
		// one that has to be: it is what decides whether a telegraphed swing can connect
		// at all, and it only means anything against that weapon's own reach.
		this.hitWindow = ticks(seed.activeTicks(), CategorySettings.MAX_PHASE_TICKS);
		row = addValue(grid, row, "hit_window", this.hitWindow);

		this.endlagModifier = modifier(seed.recoveryModifier());
		row = addValue(grid, row, "endlag_modifier", this.endlagModifier);

		this.staminaCost = unit(seed.staminaCost(), CategorySettings.MAX_COST, "stamina_points");
		row = addValue(grid, row, "stamina_cost", this.staminaCost);

		// Which stats this kind of weapon turns into damage. The single most important
		// thing on the screen — it is what decides that a claymore is a Warrior's and a
		// dagger an Outlaw's — so it sits with the other per-category values rather than
		// on a page of its own.
		StatWeights weights = seed.weights();

		this.weightStrength = weight(weights.strength());
		row = addValue(grid, row, "weight_strength", this.weightStrength);

		this.weightAgility = weight(weights.agility());
		row = addValue(grid, row, "weight_agility", this.weightAgility);

		this.weightConstitution = weight(weights.constitution());
		row = addValue(grid, row, "weight_constitution", this.weightConstitution);

		this.weightArcane = weight(weights.arcane());
		row = addValue(grid, row, "weight_arcane", this.weightArcane);

		// Spans both columns: it is a readout for the four rows above it, not a value of
		// its own, and a label-plus-field shape would invite someone to type into it.
		this.weightTotal = new StringWidget(LABEL_WIDTH + CELL_SPACING + FIELD_WIDTH, FIELD_HEIGHT,
				Component.empty(), this.font);
		grid.addChild(this.weightTotal, row, 0, 1, 2);
		row++;

		refreshWeightTotal();

		for (TunableField field : new TunableField[] {
				this.weightStrength, this.weightAgility, this.weightConstitution, this.weightArcane}) {
			field.setChangeListener(this::refreshWeightTotal);
		}

		// The rules page's fields are not on screen, so clear them for the same reason
		// the cast fields below are cleared.
		this.weaponBase = null;
		this.failedDamage = null;

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

	/**
	 * The two values that are not about any one kind of weapon.
	 *
	 * <p>A page in the same tab row rather than a section row over the value list. The
	 * screen's whole argument against sections is that they add a concept to learn for
	 * a handful of rows; a tab is a concept this screen already has, and "Rules" is
	 * legitimately a different page rather than a different view of the same one.
	 */
	private GridLayout buildRulesGrid() {
		GridLayout grid = new GridLayout().spacing(CELL_SPACING);
		int row = 0;

		this.weaponBase = unit(this.rules.weaponBasePercent(), WeaponRules.MAX_BASE_PERCENT, "percent");
		row = addValue(grid, row, "weapon_base", this.weaponBase);

		this.failedDamage = weaponUnit(this.rules.failedDamagePercent(),
				WeaponRules.MAX_FAILED_PERCENT, "hundredths_damage");
		addValue(grid, row, "failed_damage", this.failedDamage);

		// Nulled for the same reason the cast fields are: readFields() must never read a
		// field belonging to a page that is not on screen.
		this.weightStrength = null;
		this.weightAgility = null;
		this.weightConstitution = null;
		this.weightArcane = null;
		this.weightTotal = null;
		this.windUpModifier = null;
		this.hitWindow = null;
		this.endlagModifier = null;
		this.staminaCost = null;
		this.manaCost = null;

		return grid;
	}

	/**
	 * Restates the four weights as the running total they add up to.
	 *
	 * <p>Yellow away from 100 rather than red: an unusual total is legal and works
	 * exactly as intended, it is just harder to read at a glance. Marking it as an error
	 * would be a lie about the maths.
	 */
	private void refreshWeightTotal() {
		if (this.weightTotal == null) {
			return;
		}

		int total = this.weightStrength.intValue() + this.weightAgility.intValue()
				+ this.weightConstitution.intValue() + this.weightArcane.intValue();

		this.weightTotal.setMessage(
				Component.translatable("screen.grandcraft.weapons.weight_total", total)
						.withStyle(total == 100 ? ChatFormatting.DARK_GRAY : ChatFormatting.YELLOW));
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

	/**
	 * A signed offset from one of the actor's globals, in ticks.
	 *
	 * <p>The one field on this screen whose minimum is negative, because a modifier that
	 * could only add would say a dagger and a sword swing alike. {@code TunableField}
	 * needs nothing special for it — {@code Integer.valueOf} takes the sign and the
	 * length allowance already covers it.
	 */
	private TunableField modifier(int value) {
		return new TunableField(this.font, FIELD_WIDTH, FIELD_HEIGHT,
				Component.translatable("screen.grandcraft.config.ticks"),
				-CategorySettings.MAX_MODIFIER_TICKS, CategorySettings.MAX_MODIFIER_TICKS, value);
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

	/** A scaling weight, which is a share rather than a quantity. */
	private TunableField weight(int value) {
		return new TunableField(this.font, FIELD_WIDTH, FIELD_HEIGHT,
				Component.translatable("screen.grandcraft.config.weight"),
				0, StatWeights.MAX_WEIGHT, value);
	}

	private LinearLayout buildTabs() {
		LinearLayout tabs = LinearLayout.horizontal().spacing(CELL_SPACING);

		for (WeaponCategory tab : WeaponCategory.values()) {
			Button button = Button.builder(tab.displayName(), ignored -> selectTab(tab))
					.width(TAB_WIDTH).build();

			// The open tab is shown as an unusable button, which greys it out and makes
			// "you are here" obvious without a custom widget.
			button.active = this.rulesOpen || tab != this.activeTab;
			button.setTooltip(Tooltip.create(tab.description()));
			tabs.addChild(button);
		}

		Button rulesButton = Button.builder(
						Component.translatable("screen.grandcraft.weapons.rules"),
						ignored -> selectRules())
				.width(TAB_WIDTH).build();

		rulesButton.active = !this.rulesOpen;
		rulesButton.setTooltip(Tooltip.create(
				Component.translatable("screen.grandcraft.weapons.rules.description")));
		tabs.addChild(rulesButton);

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
		if (tab == this.activeTab && !this.rulesOpen) {
			return;
		}

		// init() banks the outgoing page's fields via fieldsFor / fieldsAreRules, so
		// switching here is just a rebuild.
		this.activeTab = tab;
		this.rulesOpen = false;
		rebuildWidgets();
	}

	private void selectRules() {
		if (this.rulesOpen) {
			return;
		}

		this.rulesOpen = true;
		rebuildWidgets();
	}

	/**
	 * Reloads the visible page with its shipped defaults. Deliberately does not save,
	 * and deliberately leaves other pages alone — the player still has to press Save, so
	 * a mis-click is recoverable with Cancel.
	 */
	private void resetActiveTab() {
		if (this.rulesOpen) {
			this.rules = WeaponRules.DEFAULT;
		} else {
			this.working.put(this.activeTab, this.activeTab.defaults());
		}

		// Drop the banked values so init() does not immediately overwrite the defaults
		// with the fields it is about to replace.
		this.fieldsFor = null;
		this.fieldsAreRules = false;
		rebuildWidgets();
	}

	/**
	 * The visible tab's values as the fields currently stand.
	 *
	 * <p>The cast values for a category that does not cast are carried through from what
	 * is stored, because the screen does not show them for that category.
	 */
	private CategorySettings readFields() {
		CategorySettings stored = this.working.get(this.fieldsFor);

		return new CategorySettings(
				this.windUpModifier.intValue(),
				this.hitWindow.intValue(),
				this.endlagModifier.intValue(),
				this.staminaCost.intValue(),
				new StatWeights(
						this.weightStrength.intValue(),
						this.weightAgility.intValue(),
						this.weightConstitution.intValue(),
						this.weightArcane.intValue()),
				this.manaCost == null ? stored.arcane() : new ArcaneSettings(
						this.manaCost.intValue(),
						this.baseDamage.intValue(),
						this.projectileSpeed.intValue(),
						this.knockback.intValue(),
						this.cooldown.intValue(),
						this.range.intValue()));
	}

	/** The rules page's values as the fields currently stand. */
	private WeaponRules readRules() {
		return new WeaponRules(this.weaponBase.intValue(), this.failedDamage.intValue());
	}

	private void save() {
		// Bank the page that is actually on screen. Writing the category unconditionally
		// would read the weight fields while the rules page is up, which are null there.
		if (this.rulesOpen) {
			this.rules = readRules();
		} else {
			this.working.put(this.activeTab, readFields());
		}

		ClientPlayNetworking.send(
				new ApplyWeaponConfigPayload(new WeaponSettings(this.working, this.rules)));
		onClose();
	}
}

package com.hrtq.grandcraft.client.gui;

import com.hrtq.grandcraft.combat.ActorSettings;
import com.hrtq.grandcraft.combat.BlockSettings;
import com.hrtq.grandcraft.combat.CombatActor;
import com.hrtq.grandcraft.combat.CombatSettings;
import com.hrtq.grandcraft.combat.CombatVerb;
import com.hrtq.grandcraft.combat.DodgeSettings;
import com.hrtq.grandcraft.combat.StaminaSettings;
import com.hrtq.grandcraft.combat.StatRange;
import com.hrtq.grandcraft.network.ApplyCombatConfigPayload;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
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
 * Admin screen for the combat tuning values, opened by
 * {@code /grandcraft config combat}.
 *
 * <p>One tab per {@link CombatActor}, built from {@code CombatActor.values()}, so a
 * new mob gains a tab with no change here.
 *
 * <p>Values are typed rather than dragged. Every field is a whole number: ticks,
 * armour points, or a multiplier as whole percent, which keeps one input style
 * across the screen and avoids a decimal separator that would follow the system
 * locale.
 *
 * <p>Laid out with explicit grid cells rather than a row helper, because a tab
 * without ranges has one value column instead of two and a row-filling helper
 * would reflow the labels into the wrong places.
 *
 * <p>Purely an editor. The server sends the current values, Save sends the edited
 * set back, and the server re-checks permissions and clamps on arrival.
 */
public class CombatConfigScreen extends Screen {
	private static final int LABEL_WIDTH = 78;
	private static final int FIELD_WIDTH = 54;
	private static final int FIELD_HEIGHT = 16;
	private static final int TAB_WIDTH = 80;
	private static final int BUTTON_WIDTH = 65;
	private static final int CELL_SPACING = 4;
	private static final int SECTION_SPACING = 8;

	/** Percent is stored as a 0-1 style multiplier but typed as whole percent. */
	private static final int PERCENT = 100;

	/** Settings that always have a tooltip; the rest read for themselves. */
	private static final Set<String> EXPLAINED = Set.of(
			"startup", "active", "recovery", "stagger", "health", "defence",
			"max_stamina", "regen", "regen_delay", "attack_cost", "sprint_cost", "jump_cost",
			"dodge_invulnerable", "dodge_recovery", "dodge_speed", "dodge_cost",
			"block_raise", "block_recovery", "block_break", "block_cost",
			"block_hold", "block_arc", "block_slow", "block_shield");

	/**
	 * Which group of settings is on screen.
	 *
	 * <p>An actor's settings outgrew one readable column once stamina arrived. Split
	 * rather than scrolled, so every view stays short enough to take in at a glance —
	 * the same reason the value list was cut down in the first place.
	 */
	private enum Section {
		COMBAT("combat"),
		STAMINA("stamina"),
		DODGE("dodge"),
		BLOCK("block");

		private final String id;

		Section(String id) {
			this.id = id;
		}

		Component displayName() {
			return Component.translatable("screen.grandcraft.config.section." + this.id);
		}
	}

	/** Every actor's values as currently edited, including tabs not on screen. */
	private final Map<CombatActor, ActorSettings> working = new EnumMap<>(CombatActor.class);

	private CombatActor activeTab = CombatActor.values()[0];
	private Section activeSection = Section.COMBAT;

	/**
	 * Which actor the fields on screen belong to, or null before the first build.
	 *
	 * <p>Tracked separately from {@link #activeTab} so {@link #init()} can bank the
	 * visible fields into the right entry: on a tab switch {@code activeTab} has
	 * already moved on, and writing to it would copy one actor's values over
	 * another's.
	 */
	private CombatActor fieldsFor;

	/**
	 * Which section the fields on screen belong to, for the same reason as
	 * {@link #fieldsFor}: a section switch must bank into the group being left, not
	 * the one being opened, or one group's values are written over the other's.
	 */
	private Section sectionFor;

	private TunableField startup;
	private TunableField active;
	private TunableField recovery;
	private TunableField stagger;
	private TunableField healthMin;
	private TunableField healthMax;
	private TunableField damageMin;
	private TunableField damageMax;
	private TunableField speedMin;
	private TunableField speedMax;
	private TunableField defenceMin;
	private TunableField defenceMax;

	private TunableField maxStamina;
	private TunableField regen;
	private TunableField regenDelay;
	private TunableField attackCost;
	private TunableField sprintCost;
	private TunableField jumpCost;

	private TunableField dodgeInvulnerable;
	private TunableField dodgeRecovery;
	private TunableField dodgeSpeed;
	private TunableField dodgeCost;

	private TunableField blockRaise;
	private TunableField blockRecovery;
	private TunableField blockBreak;
	private TunableField blockCost;
	private TunableField blockHold;
	private TunableField blockArc;
	private TunableField blockSlow;
	private TunableField blockShield;

	public CombatConfigScreen(CombatSettings settings) {
		super(Component.translatable("screen.grandcraft.config.title"));

		for (CombatActor actor : CombatActor.values()) {
			this.working.put(actor, settings.forActor(actor));
		}
	}

	@Override
	protected void init() {
		// Bank whatever is on screen first. This runs on window resize as well as on
		// a tab switch, so without it either would silently discard edits.
		if (this.fieldsFor != null) {
			this.working.put(this.fieldsFor, readFields());
		}

		CombatActor actor = this.activeTab;

		// An actor that lacks a verb has no section for it to be looking at, which is
		// reachable by switching tabs while that section is open.
		if (!hasSection(actor, this.activeSection)) {
			this.activeSection = Section.COMBAT;
		}

		ActorSettings seed = this.working.get(actor);

		LinearLayout root = LinearLayout.vertical().spacing(SECTION_SPACING);
		root.addChild(new StringWidget(this.title, this.font));
		root.addChild(buildTabs(actor));

		// Only worth a row of its own when there is more than one section to reach.
		if (sectionCount(actor) > 1) {
			root.addChild(buildSections(actor));
		}

		root.addChild(switch (this.activeSection) {
			case STAMINA -> buildStaminaGrid(seed);
			case DODGE -> buildDodgeGrid(seed);
			case BLOCK -> buildBlockGrid(seed);
			case COMBAT -> buildCombatGrid(actor, seed);
		});

		root.addChild(buildButtons());

		root.arrangeElements();
		FrameLayout.centerInRectangle(root, 0, 0, this.width, this.height);
		root.visitWidgets(this::addRenderableWidget);

		this.fieldsFor = actor;
		this.sectionFor = this.activeSection;
	}

	private GridLayout buildCombatGrid(CombatActor actor, ActorSettings seed) {
		boolean ranges = actor.usesRandomStats();

		GridLayout grid = new GridLayout().spacing(CELL_SPACING);
		int row = 0;

		if (ranges) {
			// The two value columns need naming once; repeating "min"/"max" in every
			// label would not fit the field width.
			grid.addChild(new StringWidget(
					Component.translatable("screen.grandcraft.config.min"), this.font), row, 1);
			grid.addChild(new StringWidget(
					Component.translatable("screen.grandcraft.config.max"), this.font), row, 2);
			row++;
		}

		if (actor.usesMeleeGoal()) {
			this.startup = ticks(seed.startupTicks(), ActorSettings.MAX_PHASE_TICKS);
			row = addValue(grid, row, "startup", false, this.startup);

			this.active = ticks(seed.activeTicks(), ActorSettings.MAX_PHASE_TICKS);
			row = addValue(grid, row, "active", false, this.active);
		} else {
			// Nothing to delay: this actor's damage lands on vanilla timing, so it has
			// neither a wind-up nor a window of ours. Cleared rather than left stale,
			// because readFields() uses null to mean "keep the stored value".
			this.startup = null;
			this.active = null;
		}

		this.recovery = ticks(seed.recoveryTicks(), ActorSettings.MAX_PHASE_TICKS);
		row = addValue(grid, row, "recovery", false, this.recovery);

		this.stagger = ticks(seed.staggerTicks(), ActorSettings.MAX_STAGGER_TICKS);
		row = addValue(grid, row, "stagger", false, this.stagger);

		int maxHealth = (int) (ActorSettings.MAX_HEALTH_MULTIPLIER * PERCENT);
		int maxDamage = (int) (ActorSettings.MAX_DAMAGE_MULTIPLIER * PERCENT);
		int maxSpeed = (int) (ActorSettings.MAX_SPEED_MULTIPLIER * PERCENT);
		int maxDefence = (int) ActorSettings.MAX_DEFENCE;

		this.healthMin = percent(seed.health().min(), maxHealth);
		this.healthMax = ranges ? percent(seed.health().max(), maxHealth) : null;
		row = addStat(grid, row, "health", this.healthMin, this.healthMax);

		this.damageMin = percent(seed.damage().min(), maxDamage);
		this.damageMax = ranges ? percent(seed.damage().max(), maxDamage) : null;
		row = addStat(grid, row, "damage", this.damageMin, this.damageMax);

		this.speedMin = percent(seed.speed().min(), maxSpeed);
		this.speedMax = ranges ? percent(seed.speed().max(), maxSpeed) : null;
		row = addStat(grid, row, "speed", this.speedMin, this.speedMax);

		this.defenceMin = points(seed.defence().min(), maxDefence);
		this.defenceMax = ranges ? points(seed.defence().max(), maxDefence) : null;
		addStat(grid, row, "defence", this.defenceMin, this.defenceMax);

		return grid;
	}

	/**
	 * The stamina group: one value column, no ranges. Costs and rates describe the
	 * actor's behaviour rather than a stat rolled per individual, so nothing here is
	 * a band even for actors that roll everything else.
	 */
	private GridLayout buildStaminaGrid(ActorSettings seed) {
		StaminaSettings stamina = seed.stamina();

		GridLayout grid = new GridLayout().spacing(CELL_SPACING);
		int row = 0;

		this.maxStamina = points(stamina.maxStamina(), StaminaSettings.MAX_POOL, "stamina_points");
		row = addValue(grid, row, "max_stamina", true, this.maxStamina);

		this.regen = perSecond(stamina.regenPerSecond(), StaminaSettings.MAX_RATE);
		row = addValue(grid, row, "regen", false, this.regen);

		this.regenDelay = ticks(stamina.regenDelayTicks(), StaminaSettings.MAX_DELAY_TICKS);
		row = addValue(grid, row, "regen_delay", false, this.regenDelay);

		this.attackCost = points(stamina.attackCost(), StaminaSettings.MAX_COST, "stamina_points");
		row = addValue(grid, row, "attack_cost", false, this.attackCost);

		this.sprintCost = perSecond(stamina.sprintCostPerSecond(), StaminaSettings.MAX_RATE);
		row = addValue(grid, row, "sprint_cost", false, this.sprintCost);

		this.jumpCost = points(stamina.jumpCost(), StaminaSettings.MAX_COST, "stamina_points");
		addValue(grid, row, "jump_cost", false, this.jumpCost);

		return grid;
	}

	/**
	 * The dodge group: one value column, no ranges, for the same reason as stamina —
	 * these describe how the verb behaves rather than a stat rolled per individual.
	 */
	private GridLayout buildDodgeGrid(ActorSettings seed) {
		DodgeSettings dodge = seed.dodge();

		GridLayout grid = new GridLayout().spacing(CELL_SPACING);
		int row = 0;

		this.dodgeInvulnerable = ticks(dodge.invulnerableTicks(), DodgeSettings.MAX_TICKS);
		row = addValue(grid, row, "dodge_invulnerable", false, this.dodgeInvulnerable);

		this.dodgeRecovery = ticks(dodge.recoveryTicks(), DodgeSettings.MAX_TICKS);
		row = addValue(grid, row, "dodge_recovery", false, this.dodgeRecovery);

		this.dodgeSpeed = unit(dodge.speed(), DodgeSettings.MAX_SPEED, "hundredths_per_tick");
		row = addValue(grid, row, "dodge_speed", false, this.dodgeSpeed);

		this.dodgeCost = points(dodge.cost(), DodgeSettings.MAX_COST, "stamina_points");
		addValue(grid, row, "dodge_cost", false, this.dodgeCost);

		return grid;
	}

	/**
	 * The guard group: one value column, no ranges, for the same reason as stamina and
	 * dodge — these describe how the verb behaves rather than a stat rolled per
	 * individual.
	 *
	 * <p>The largest of the four groups, and the order is the order someone tuning it
	 * would work in: the timings that decide how committing it is, then what it costs
	 * to use, then how much of the actor it actually covers.
	 */
	private GridLayout buildBlockGrid(ActorSettings seed) {
		BlockSettings block = seed.block();

		GridLayout grid = new GridLayout().spacing(CELL_SPACING);
		int row = 0;

		this.blockRaise = ticks(block.raiseTicks(), BlockSettings.MAX_TICKS);
		row = addValue(grid, row, "block_raise", false, this.blockRaise);

		this.blockRecovery = ticks(block.recoveryTicks(), BlockSettings.MAX_TICKS);
		row = addValue(grid, row, "block_recovery", false, this.blockRecovery);

		this.blockBreak = ticks(block.breakTicks(), BlockSettings.MAX_TICKS);
		row = addValue(grid, row, "block_break", false, this.blockBreak);

		this.blockCost = unit(block.costPerDamage(), BlockSettings.MAX_COST_PER_DAMAGE,
				"hundredths_per_damage");
		row = addValue(grid, row, "block_cost", false, this.blockCost);

		this.blockHold = perSecond(block.holdCostPerSecond(), BlockSettings.MAX_HOLD_COST);
		row = addValue(grid, row, "block_hold", false, this.blockHold);

		this.blockArc = unit(block.arcDegrees(), BlockSettings.MAX_ARC_DEGREES, "degrees");
		row = addValue(grid, row, "block_arc", false, this.blockArc);

		this.blockSlow = unit(block.moveSlowPercent(), BlockSettings.MAX_SLOW_PERCENT, "percent");
		row = addValue(grid, row, "block_slow", false, this.blockSlow);

		this.blockShield = unit(block.shieldCostPercent(), BlockSettings.MAX_SHIELD_PERCENT,
				"percent");
		addValue(grid, row, "block_shield", false, this.blockShield);

		return grid;
	}

	/** Whether this actor has the verb a section exists to configure. */
	private static boolean hasSection(CombatActor actor, Section section) {
		return switch (section) {
			case COMBAT -> true;
			case STAMINA -> actor.has(CombatVerb.STAMINA);
			case DODGE -> actor.usesDodge();
			case BLOCK -> actor.usesBlock();
		};
	}

	/**
	 * How many sections this actor has anything to show in.
	 *
	 * <p>Counted from {@link #hasSection} rather than written out as its own list of
	 * verbs. The section row's visibility used to be exactly that — a second condition
	 * naming the same verbs — which is one more place to forget when a verb is added,
	 * and forgetting it hides every section behind a row that never appears.
	 */
	private static int sectionCount(CombatActor actor) {
		int count = 0;

		for (Section section : Section.values()) {
			if (hasSection(actor, section)) {
				count++;
			}
		}

		return count;
	}

	/** A single value in the label column and the first value column. */
	private int addValue(GridLayout grid, int row, String key, boolean base, TunableField field) {
		grid.addChild(label(key, base), row, 0);
		grid.addChild(field, row, 1);
		return row + 1;
	}

	/** A stat, which has a second value column only when this actor rolls a range. */
	private int addStat(GridLayout grid, int row, String key, TunableField min, TunableField max) {
		grid.addChild(label(key, true), row, 0);
		grid.addChild(min, row, 1);

		if (max != null) {
			grid.addChild(max, row, 2);
		}

		return row + 1;
	}

	private StringWidget label(String key, boolean base) {
		Component name = Component.translatable("screen.grandcraft.config." + key);

		// Every stat is a base value, for mobs as much as for the player: what is set
		// here is the starting point that later features are meant to add to rather
		// than replace. For a mob that starting point is itself rolled from the band,
		// so "Base Health / Min / Max" reads as the range the base is drawn from.
		Component text = base
				? Component.translatable("screen.grandcraft.config.base", name)
				: name;

		StringWidget widget = new StringWidget(LABEL_WIDTH, FIELD_HEIGHT, text, this.font);

		// Only some settings need explaining; the rest read for themselves.
		if (EXPLAINED.contains(key)) {
			widget.setTooltip(Tooltip.create(
					Component.translatable("screen.grandcraft.config." + key + ".tooltip")));
		}

		return widget;
	}

	private TunableField ticks(int value, int max) {
		return unit(value, max, "ticks");
	}

	private TunableField perSecond(int value, int max) {
		return unit(value, max, "per_second");
	}

	private TunableField points(double value, int max) {
		return points(value, max, "points");
	}

	private TunableField points(double value, int max, String unitKey) {
		return unit((int) Math.round(value), max, unitKey);
	}

	/** The unit key names the field for narration; every field is a whole number. */
	private TunableField unit(int value, int max, String unitKey) {
		return new TunableField(this.font, FIELD_WIDTH, FIELD_HEIGHT,
				Component.translatable("screen.grandcraft.config." + unitKey), 0, max, value);
	}

	private TunableField percent(double multiplier, int max) {
		return new TunableField(this.font, FIELD_WIDTH, FIELD_HEIGHT,
				Component.translatable("screen.grandcraft.config.percent"),
				0, max, (int) Math.round(multiplier * PERCENT));
	}

	private LinearLayout buildTabs(CombatActor open) {
		LinearLayout tabs = LinearLayout.horizontal().spacing(CELL_SPACING);

		for (CombatActor tab : CombatActor.values()) {
			Button button = Button.builder(tab.displayName(), ignored -> selectTab(tab))
					.width(TAB_WIDTH).build();

			// The open tab is shown as an unusable button, which greys it out and
			// makes "you are here" obvious without a custom widget.
			button.active = tab != open;
			tabs.addChild(button);
		}

		return tabs;
	}

	/**
	 * The section row, styled exactly like the actor tabs above it — an unusable
	 * button is how this screen already says "you are here".
	 */
	private LinearLayout buildSections(CombatActor actor) {
		LinearLayout sections = LinearLayout.horizontal().spacing(CELL_SPACING);

		for (Section section : Section.values()) {
			// A section for a verb this actor does not have would only ever show values
			// that nothing reads.
			if (!hasSection(actor, section)) {
				continue;
			}

			Button button = Button.builder(section.displayName(), ignored -> selectSection(section))
					.width(TAB_WIDTH).build();
			button.active = section != this.activeSection;
			sections.addChild(button);
		}

		return sections;
	}

	private void selectSection(Section section) {
		if (section == this.activeSection) {
			return;
		}

		// init() banks the outgoing section's fields via sectionFor, so switching here
		// is just a rebuild — the same contract as a tab switch.
		this.activeSection = section;
		rebuildWidgets();
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

	private void selectTab(CombatActor tab) {
		if (tab == this.activeTab) {
			return;
		}

		// init() banks the outgoing tab's fields via fieldsFor, so switching here is
		// just a rebuild.
		this.activeTab = tab;
		rebuildWidgets();
	}

	/**
	 * Reloads the visible tab with that actor's shipped defaults. Deliberately does
	 * not save, and deliberately leaves other tabs alone — the player still has to
	 * press Save, so a mis-click is recoverable with Cancel.
	 */
	private void resetActiveTab() {
		this.working.put(this.activeTab, this.activeTab.defaults());

		// Drop the banked values so init() does not immediately overwrite the
		// defaults with the fields it is about to replace.
		this.fieldsFor = null;
		rebuildWidgets();
	}

	/**
	 * The visible tab's values as the fields currently stand.
	 *
	 * <p>Only the open section is read from the widgets; the other is carried through
	 * from what is stored. Rebuilding it from defaults instead would let opening one
	 * section and pressing Save quietly discard the other.
	 */
	private ActorSettings readFields() {
		ActorSettings stored = this.working.get(this.fieldsFor);

		if (this.sectionFor == Section.STAMINA) {
			return new ActorSettings(
					stored.startupTicks(),
					stored.activeTicks(),
					stored.recoveryTicks(),
					stored.staggerTicks(),
					stored.health(),
					stored.damage(),
					stored.speed(),
					stored.defence(),
					new StaminaSettings(
							this.maxStamina.intValue(),
							this.regen.intValue(),
							this.regenDelay.intValue(),
							this.attackCost.intValue(),
							this.sprintCost.intValue(),
							this.jumpCost.intValue()),
					stored.dodge(),
					stored.block());
		}

		if (this.sectionFor == Section.DODGE) {
			return new ActorSettings(
					stored.startupTicks(),
					stored.activeTicks(),
					stored.recoveryTicks(),
					stored.staggerTicks(),
					stored.health(),
					stored.damage(),
					stored.speed(),
					stored.defence(),
					stored.stamina(),
					new DodgeSettings(
							this.dodgeInvulnerable.intValue(),
							this.dodgeRecovery.intValue(),
							this.dodgeSpeed.intValue(),
							this.dodgeCost.intValue()),
					stored.block());
		}

		if (this.sectionFor == Section.BLOCK) {
			return new ActorSettings(
					stored.startupTicks(),
					stored.activeTicks(),
					stored.recoveryTicks(),
					stored.staggerTicks(),
					stored.health(),
					stored.damage(),
					stored.speed(),
					stored.defence(),
					stored.stamina(),
					stored.dodge(),
					new BlockSettings(
							this.blockRaise.intValue(),
							this.blockRecovery.intValue(),
							this.blockBreak.intValue(),
							this.blockCost.intValue(),
							this.blockHold.intValue(),
							this.blockArc.intValue(),
							this.blockSlow.intValue(),
							this.blockShield.intValue()));
		}

		return new ActorSettings(
				this.startup == null ? stored.startupTicks() : this.startup.intValue(),
				this.active == null ? stored.activeTicks() : this.active.intValue(),
				this.recovery.intValue(),
				this.stagger.intValue(),
				percentBand(this.healthMin, this.healthMax),
				percentBand(this.damageMin, this.damageMax),
				percentBand(this.speedMin, this.speedMax),
				pointsBand(this.defenceMin, this.defenceMax),
				stored.stamina(),
				stored.dodge(),
				stored.block());
	}

	/** A fixed stat has no max field, so its band collapses onto the single value. */
	private static StatRange percentBand(TunableField min, TunableField max) {
		double low = min.intValue() / (double) PERCENT;
		return max == null ? StatRange.of(low) : new StatRange(low, max.intValue() / (double) PERCENT);
	}

	private static StatRange pointsBand(TunableField min, TunableField max) {
		double low = min.intValue();
		return max == null ? StatRange.of(low) : new StatRange(low, max.intValue());
	}

	private void save() {
		this.working.put(this.activeTab, readFields());
		ClientPlayNetworking.send(new ApplyCombatConfigPayload(new CombatSettings(this.working)));
		onClose();
	}
}

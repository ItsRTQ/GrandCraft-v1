package com.hrtq.grandcraft.client.gui;

import com.hrtq.grandcraft.combat.CombatSettings;
import com.hrtq.grandcraft.network.ApplyCombatConfigPayload;
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
 * Admin screen for the combat tuning values, opened by {@code /grandcraft config}.
 *
 * <p>Purely an editor. The server sends the current values, and Save sends the
 * edited set back; the server remains the authority on what is actually in force
 * and re-checks permissions on arrival. Nothing here changes local combat state.
 *
 * <p>Slider bounds come from {@link CombatSettings}, the same constants the server
 * clamps against, so the UI cannot offer a value the server would reject.
 */
public class CombatConfigScreen extends Screen {
	private static final int SLIDER_WIDTH = 150;
	private static final int WIDGET_HEIGHT = 20;
	private static final int GRID_SPACING = 6;
	private static final int SECTION_SPACING = 10;

	private final CombatSettings initialSettings;

	private TunableSlider stagger1Ticks;
	private TunableSlider stagger1Slow;
	private TunableSlider stagger2Ticks;
	private TunableSlider stagger2Slow;
	private TunableSlider staggerReset;
	private TunableSlider staggerJump;
	private TunableSlider knockbackResistance;
	private TunableSlider zombieStartup;
	private TunableSlider zombieActive;
	private TunableSlider zombieRecovery;
	private TunableSlider playerRecovery;

	public CombatConfigScreen(CombatSettings settings) {
		super(Component.translatable("screen.grandcraft.config.title"));
		this.initialSettings = settings;
	}

	@Override
	protected void init() {
		// init() re-runs on window resize, so seed from the sliders' present values
		// rather than the originals; otherwise resizing would silently discard edits.
		CombatSettings seed = this.stagger1Ticks == null ? this.initialSettings : edited();

		LinearLayout root = LinearLayout.vertical().spacing(SECTION_SPACING);
		root.addChild(new StringWidget(this.title, this.font));

		// Left column is the stagger reaction, right column is attack phases.
		// RowHelper fills row by row, so children are interleaved to keep the
		// two groups in their own columns.
		GridLayout grid = new GridLayout().spacing(GRID_SPACING);
		GridLayout.RowHelper rows = grid.createRowHelper(2);

		this.stagger1Ticks = ticks("stagger_1_ticks",
				CombatSettings.MAX_STAGGER_TICKS, seed.stagger1Ticks());
		this.zombieStartup = ticks("zombie_startup",
				CombatSettings.MAX_PHASE_TICKS, seed.zombieStartupTicks());
		this.stagger1Slow = percent("stagger_1_slow", seed.stagger1SlowFraction());
		// Floored at 1, matching the server clamp: an attack with no active window
		// could never connect.
		this.zombieActive = ticks("zombie_active", CombatSettings.MIN_ACTIVE_TICKS,
				CombatSettings.MAX_PHASE_TICKS, seed.zombieActiveTicks());
		this.stagger2Ticks = ticks("stagger_2_ticks",
				CombatSettings.MAX_STAGGER_TICKS, seed.stagger2Ticks());
		this.zombieRecovery = ticks("zombie_recovery",
				CombatSettings.MAX_PHASE_TICKS, seed.zombieRecoveryTicks());
		this.stagger2Slow = percent("stagger_2_slow", seed.stagger2SlowFraction());
		this.playerRecovery = ticks("player_recovery",
				CombatSettings.MAX_PHASE_TICKS, seed.playerRecoveryTicks());
		this.staggerJump = fraction("stagger_jump",
				CombatSettings.MAX_JUMP_PENALTY, seed.staggerJumpPenalty());
		this.knockbackResistance = fraction("knockback_resistance",
				CombatSettings.MAX_KNOCKBACK_RESISTANCE, seed.knockbackResistance());
		this.staggerReset = ticks("stagger_reset",
				CombatSettings.MAX_RESET_TICKS, seed.staggerResetTicks());

		// Below roughly 15 the reset window expires between ordinary consecutive
		// swings, and the strong / weak / none sequence stops forming. Worth saying
		// out loud, since the slider happily goes lower.
		this.staggerReset.setTooltip(Tooltip.create(
				Component.translatable("screen.grandcraft.config.stagger_reset.tooltip")));
		this.staggerJump.setTooltip(Tooltip.create(
				Component.translatable("screen.grandcraft.config.stagger_jump.tooltip")));
		this.knockbackResistance.setTooltip(Tooltip.create(
				Component.translatable("screen.grandcraft.config.knockback_resistance.tooltip")));

		rows.addChild(this.stagger1Ticks);
		rows.addChild(this.zombieStartup);
		rows.addChild(this.stagger1Slow);
		rows.addChild(this.zombieActive);
		rows.addChild(this.stagger2Ticks);
		rows.addChild(this.zombieRecovery);
		rows.addChild(this.stagger2Slow);
		rows.addChild(this.playerRecovery);
		rows.addChild(this.staggerJump);
		rows.addChild(this.knockbackResistance);
		rows.addChild(this.staggerReset);

		root.addChild(grid);

		LinearLayout buttons = LinearLayout.horizontal().spacing(GRID_SPACING);
		buttons.addChild(Button.builder(
						Component.translatable("screen.grandcraft.config.save"), button -> save())
				.width(SLIDER_WIDTH / 2).build());
		buttons.addChild(Button.builder(
						Component.translatable("screen.grandcraft.config.reset"), button -> resetToDefaults())
				.width(SLIDER_WIDTH / 2).build());
		buttons.addChild(Button.builder(
						Component.translatable("screen.grandcraft.config.cancel"), button -> onClose())
				.width(SLIDER_WIDTH / 2).build());
		root.addChild(buttons);

		root.arrangeElements();
		FrameLayout.centerInRectangle(root, 0, 0, this.width, this.height);
		root.visitWidgets(this::addRenderableWidget);
	}

	private TunableSlider ticks(String key, int max, int value) {
		return ticks(key, 0, max, value);
	}

	private TunableSlider ticks(String key, int min, int max, int value) {
		return new TunableSlider(SLIDER_WIDTH, WIDGET_HEIGHT,
				Component.translatable("screen.grandcraft.config." + key),
				min, max, value, TunableSlider.Style.TICKS);
	}

	private TunableSlider percent(String key, double value) {
		return fraction(key, CombatSettings.MAX_SLOW_FRACTION, value);
	}

	private TunableSlider fraction(String key, double max, double value) {
		return new TunableSlider(SLIDER_WIDTH, WIDGET_HEIGHT,
				Component.translatable("screen.grandcraft.config." + key),
				0.0, max, value, TunableSlider.Style.PERCENT);
	}

	/**
	 * Reloads the sliders with the shipped defaults. Deliberately does not save —
	 * the player still has to press Save, so a mis-click is recoverable with Cancel.
	 */
	private void resetToDefaults() {
		this.minecraft.setScreenAndShow(new CombatConfigScreen(CombatSettings.DEFAULT));
	}

	/** The values as the sliders currently stand. Only valid once {@link #init()} has run. */
	private CombatSettings edited() {
		return new CombatSettings(
				this.stagger1Ticks.intValue(),
				this.stagger1Slow.doubleValue(),
				this.stagger2Ticks.intValue(),
				this.stagger2Slow.doubleValue(),
				this.staggerReset.intValue(),
				this.staggerJump.doubleValue(),
				this.knockbackResistance.doubleValue(),
				this.zombieStartup.intValue(),
				this.zombieActive.intValue(),
				this.zombieRecovery.intValue(),
				this.playerRecovery.intValue());
	}

	private void save() {
		ClientPlayNetworking.send(new ApplyCombatConfigPayload(edited()));
		onClose();
	}
}

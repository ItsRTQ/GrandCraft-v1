package com.hrtq.grandcraft.client.gui;

import com.hrtq.grandcraft.config.GameSettings;
import com.hrtq.grandcraft.network.ApplyGameConfigPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Admin screen for the mod-wide settings, opened by {@code /grandcraft config game}.
 *
 * <p>Deliberately separate from the combat screen: this is where settings that are
 * not about combat live, and it is expected to grow. Each setting is one row, so
 * adding another is a field and a row rather than a layout change.
 *
 * <p>Purely an editor. Save sends the edited set back and the server re-checks
 * permissions on arrival.
 */
public class GameConfigScreen extends Screen {
	private static final int ROW_WIDTH = 200;
	private static final int WIDGET_HEIGHT = 20;
	private static final int BUTTON_WIDTH = 65;
	private static final int SPACING = 8;

	private boolean healthBars;
	private boolean combatAnimations;

	public GameConfigScreen(GameSettings settings) {
		super(Component.translatable("screen.grandcraft.game.title"));
		this.healthBars = settings.healthBars();
		this.combatAnimations = settings.combatAnimations();
	}

	@Override
	protected void init() {
		LinearLayout root = LinearLayout.vertical().spacing(SPACING);
		root.addChild(new StringWidget(this.title, this.font));

		Button toggle = Button.builder(healthBarsLabel(), button -> {
			this.healthBars = !this.healthBars;
			button.setMessage(healthBarsLabel());
		}).width(ROW_WIDTH).build();
		toggle.setTooltip(Tooltip.create(
				Component.translatable("screen.grandcraft.game.health_bars.tooltip")));
		root.addChild(toggle);

		Button animations = Button.builder(combatAnimationsLabel(), button -> {
			this.combatAnimations = !this.combatAnimations;
			button.setMessage(combatAnimationsLabel());
		}).width(ROW_WIDTH).build();
		animations.setTooltip(Tooltip.create(
				Component.translatable("screen.grandcraft.game.combat_animations.tooltip")));
		root.addChild(animations);

		LinearLayout buttons = LinearLayout.horizontal().spacing(SPACING);
		buttons.addChild(Button.builder(
						Component.translatable("screen.grandcraft.config.save"), button -> save())
				.width(BUTTON_WIDTH).build());
		buttons.addChild(Button.builder(
						Component.translatable("screen.grandcraft.config.cancel"), button -> onClose())
				.width(BUTTON_WIDTH).build());
		root.addChild(buttons);

		root.arrangeElements();
		FrameLayout.centerInRectangle(root, 0, 0, this.width, this.height);
		root.visitWidgets(this::addRenderableWidget);
	}

	private Component healthBarsLabel() {
		return Component.translatable("screen.grandcraft.game.health_bars",
				this.healthBars ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF);
	}

	private Component combatAnimationsLabel() {
		return Component.translatable("screen.grandcraft.game.combat_animations",
				this.combatAnimations ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF);
	}

	private void save() {
		ClientPlayNetworking.send(new ApplyGameConfigPayload(
				new GameSettings(this.healthBars, this.combatAnimations)));
		onClose();
	}
}

package com.hrtq.grandcraft.client.gui;

import com.hrtq.grandcraft.network.SelectClassPayload;
import com.hrtq.grandcraft.player.GrandCraftAttachments;
import com.hrtq.grandcraft.player.PlayerClass;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class GrandCraftScreen extends Screen {
	private static final int FONT_LINE_HEIGHT = 9;
	private static final int TITLE_TOP_MARGIN = 15;
	private static final int SUBTITLE_TOP_MARGIN = 35;
	private static final int BUTTON_WIDTH = 150;
	private static final int BUTTON_HEIGHT = 20;
	private static final int BUTTON_SPACING = 24;

	public GrandCraftScreen() {
		super(Component.translatable("screen.grandcraft.character_sheet"));
	}

	@Override
	protected void init() {
		addRenderableWidget(new StringWidget(
				0, TITLE_TOP_MARGIN,
				this.width, FONT_LINE_HEIGHT,
				this.title, this.font));

		PlayerClass playerClass = this.minecraft.player.getAttachedOrElse(
				GrandCraftAttachments.PLAYER_CLASS, PlayerClass.PEASANT);

		if (playerClass == PlayerClass.PEASANT) {
			initClassSelection();
		} else {
			addRenderableWidget(new StringWidget(
					0, SUBTITLE_TOP_MARGIN,
					this.width, FONT_LINE_HEIGHT,
					Component.translatable("screen.grandcraft.current_class", playerClass.displayName()),
					this.font));
		}
	}

	private void initClassSelection() {
		addRenderableWidget(new StringWidget(
				0, SUBTITLE_TOP_MARGIN,
				this.width, FONT_LINE_HEIGHT,
				Component.translatable("screen.grandcraft.choose_class"),
				this.font));

		int count = PlayerClass.SELECTABLE.size();
		int totalHeight = count * BUTTON_SPACING - (BUTTON_SPACING - BUTTON_HEIGHT);
		int y = (this.height - totalHeight) / 2;

		for (PlayerClass choice : PlayerClass.SELECTABLE) {
			addRenderableWidget(Button.builder(choice.displayName(), button -> selectClass(choice))
					.bounds((this.width - BUTTON_WIDTH) / 2, y, BUTTON_WIDTH, BUTTON_HEIGHT)
					.build());
			y += BUTTON_SPACING;
		}
	}

	private void selectClass(PlayerClass choice) {
		ClientPlayNetworking.send(new SelectClassPayload(choice));
		onClose();
	}

	@Override
	public void extractTransparentBackground(GuiGraphicsExtractor extractor) {
		// Vanilla uses 0xC0101010 -> 0xD0101010; use a darker, more opaque black.
		// The engine allows only one blur pass per frame, so darkness is the
		// only per-screen way to strengthen the background dimming.
		extractor.fillGradient(0, 0, this.width, this.height, 0xD0000000, 0xE0000000);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}

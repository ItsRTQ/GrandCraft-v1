package com.hrtq.grandcraft.client.hud;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Vanilla's armour icons, drawn at the bottom of the player's column instead of above
 * the hearts (user, 2026-08-05).
 *
 * <p>Vanilla's own sprites, so a resource pack still reskins it and it still looks like
 * Minecraft armour — only the ten blits are ours, and only so they can be aimed at
 * {@link HudBars}. {@code GrandCraftClient} hands vanilla's element to this and throws
 * the original away.
 *
 * <h2>Why this is redrawn and the hunger bar is translated</h2>
 *
 * The hunger bar is vanilla's element wrapped in a matrix translation
 * ({@link HudBars#intoColumn()}), which is the better trick wherever it works: vanilla
 * keeps deciding what to draw, so the shake, the saturation flash and the hunger-effect
 * variant all keep working for free.
 *
 * <p>It does not work here, and the reason is worth keeping. Armour is drawn inside
 * {@code Hud.extractPlayerHealth} at
 * {@code (guiHeight - 39) - (rows - 1) * rowHeight - 10}, where {@code rows} is the
 * number of <em>heart</em> rows — so vanilla's armour anchor moves whenever max health
 * crosses a multiple of twenty. This mod moves max health all the time: Constitution and
 * bought attribute points both do. Translating would mean recomputing vanilla's row
 * arithmetic every frame from a private lagging field, to cancel out a position nothing
 * ever sees, because the hearts themselves are removed. Ten blits is less code and less
 * to keep in step.
 *
 * <p>What that costs is what a redraw always costs: if vanilla changes how armour is
 * drawn, this will not follow. Armour has been ten icons, half-steps, no animation and
 * no variants for a very long time, which is what makes the trade acceptable here and
 * not for food.
 *
 * <h2>Last in the column, and it hides itself</h2>
 *
 * Armour is the only row the player is often without, so it goes last and nothing has to
 * close up behind it — {@link HudBars#armourRow()} is a plain increment rather than the
 * conditional {@link HudBars#manaRow()} has to be. With no armour on, nothing is drawn
 * at all, which is vanilla's own rule kept.
 *
 * <p>The column's ten 8-pixel icons come to exactly {@link HudBars#BAR_WIDTH}, which is
 * the same coincidence that let the hunger bar move here: a vanilla status row and the
 * artist's bar canvas are the same 81x9.
 */
public final class ArmourBarElement implements HudElement {
	// No ID constant, unlike its three siblings. They are attached to the chain under an
	// identifier of their own; this one is handed to replaceElement and therefore keeps
	// vanilla's ARMOR_BAR identifier. A constant here would name something unregistered,
	// and the first attachElementAfter to use it would silently find nothing.

	/**
	 * Vanilla's own sprites, named the way {@code Hud} names them. Not our textures, so
	 * a resource pack that restyles armour restyles this too.
	 */
	private static final Identifier FULL = Identifier.withDefaultNamespace("hud/armor_full");
	private static final Identifier HALF = Identifier.withDefaultNamespace("hud/armor_half");
	private static final Identifier EMPTY = Identifier.withDefaultNamespace("hud/armor_empty");

	/** Ten icons, 9x9 each, on an 8-pixel pitch — vanilla's status row, exactly. */
	private static final int ICONS = 10;
	private static final int ICON_SIZE = 9;
	private static final int ICON_PITCH = 8;

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		LocalPlayer player = Minecraft.getInstance().player;

		if (player == null) {
			return;
		}

		int armour = player.getArmorValue();

		// No armour, no row. Vanilla's own rule, and what keeps this the only row that
		// may be absent without anything below it having to move.
		if (armour <= 0) {
			return;
		}

		int left = HudBars.left();
		int top = HudBars.top(HudBars.armourRow());

		for (int icon = 0; icon < ICONS; icon++) {
			// Each icon stands for two armour points, so the value it fills at is the
			// odd number in its pair: below it the icon is full, on it exactly half.
			int threshold = icon * 2 + 1;
			Identifier sprite = threshold < armour ? FULL : (threshold == armour ? HALF : EMPTY);

			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite,
					left + icon * ICON_PITCH, top, ICON_SIZE, ICON_SIZE);
		}
	}
}

package com.hrtq.grandcraft.client.hud;

import com.hrtq.grandcraft.GrandCraft;
import com.hrtq.grandcraft.client.ClientMana;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudStatusBarHeightRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

/**
 * The mana bar, drawn in the right-hand status column alongside the stamina bar.
 *
 * <p>Attached to the stamina bar's layer, so like it this inherits vanilla's own
 * decision about when status bars are visible — it disappears in creative and
 * spectator with no gamemode check here.
 *
 * <h2>Drawn rather than blitted, on purpose and temporarily</h2>
 * This is three filled rectangles, not artwork. Mana became spendable before there
 * were frames for it, and a resource you spend but cannot see is indistinguishable
 * from a broken mechanic — you would run dry mid-fight with no idea why the staff
 * stopped working. A flat bar is worth far more now than a beautiful one later.
 *
 * <p>Every measurement below is deliberately identical to
 * {@link StaminaBarElement}'s, and taken from vanilla's own right-hand anchor. When
 * the artist delivers frames the swap is: delete the three fills, add the
 * {@code LEVELS} array and one {@code blit}, and keep every constant. The geometry
 * and the anchor are already theirs.
 *
 * <p><strong>Colours are ARGB and the alpha byte is not optional.</strong>
 * {@code 0x4C7DF0} is fully transparent, not blue — the same trap that once made
 * typed text invisible in the config screens.
 */
public final class ManaBarElement implements HudElement {
	public static final Identifier ID = GrandCraft.id("mana_bar");

	/**
	 * Exactly one vanilla status row: ten 8px icons plus the trailing pixel, and the
	 * 9px icon height. Matched to the stamina bar so the two read as a pair.
	 */
	private static final int BAR_WIDTH = 81;
	private static final int BAR_HEIGHT = 9;

	/** Vanilla's spacing between right-hand rows, which is what shifts the bars above. */
	private static final int RESERVED_HEIGHT = 10;

	/** Vanilla's own right-hand anchor, read out of {@code Hud.extractPlayerHealth}. */
	private static final int RIGHT_EDGE_OFFSET = 91;
	private static final int BASELINE_OFFSET = 39;

	/** One pixel of frame all round, so an empty bar still reads as a bar. */
	private static final int BORDER = 1;

	private static final int COLOUR_FRAME = 0xFF1B1B2F;
	private static final int COLOUR_EMPTY = 0xFF2E2E44;
	private static final int COLOUR_FILL = 0xFF4C7DF0;

	/**
	 * The row this element occupies, which is what shifts the bars above it.
	 *
	 * <p>Zero when there is nothing to draw, so the rest of the column closes the gap
	 * rather than leaving a hole where the mana bar would have been. That is also what
	 * makes a configured mana pool of zero disappear cleanly instead of leaving a
	 * permanently empty bar implying a resource that does not exist.
	 */
	public static int reservedHeight() {
		return ClientMana.hasData() ? RESERVED_HEIGHT : 0;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		if (!ClientMana.hasData()) {
			return;
		}

		long now = Util.getMillis();

		int right = graphics.guiWidth() / 2 + RIGHT_EDGE_OFFSET;
		int left = right - BAR_WIDTH;

		// The registry reports how far this element sits above the baseline, which is
		// what keeps it clear of the rows below and pushes the ones above it up in turn.
		int top = graphics.guiHeight() - BASELINE_OFFSET - HudStatusBarHeightRegistry.getHeight(ID);

		float max = ClientMana.max();
		float fraction = max <= 0.0F ? 0.0F : Math.clamp(ClientMana.current(now) / max, 0.0F, 1.0F);

		int inner = BAR_WIDTH - 2 * BORDER;
		int filled = Math.round(inner * fraction);

		graphics.fill(left, top, left + BAR_WIDTH, top + BAR_HEIGHT, COLOUR_FRAME);
		graphics.fill(left + BORDER, top + BORDER,
				left + BAR_WIDTH - BORDER, top + BAR_HEIGHT - BORDER, COLOUR_EMPTY);

		// Skipped at zero rather than drawn as a zero-width rectangle, which some fill
		// paths treat as a degenerate quad rather than as nothing.
		if (filled > 0) {
			graphics.fill(left + BORDER, top + BORDER,
					left + BORDER + filled, top + BAR_HEIGHT - BORDER, COLOUR_FILL);
		}
	}
}

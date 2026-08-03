package com.hrtq.grandcraft.client.hud;

import com.hrtq.grandcraft.GrandCraft;
import com.hrtq.grandcraft.client.ClientMana;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

/**
 * The mana bar, last in the top-left column under stamina. {@link HudBars} owns where
 * it sits; this owns what it shows.
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
 * <p>Geometry and position come from {@link HudBars}, exactly as the other two bars'
 * do. When the artist delivers frames the swap is: delete the three fills, add a
 * {@code LEVELS} array and one {@code blit} against {@code HudBars}, and change nothing
 * else. The row is already reserved and the size is already agreed.
 *
 * <p><strong>Colours are ARGB and the alpha byte is not optional.</strong>
 * {@code 0x4C7DF0} is fully transparent, not blue — the same trap that once made
 * typed text invisible in the config screens.
 */
public final class ManaBarElement implements HudElement {
	public static final Identifier ID = GrandCraft.id("mana_bar");

	/** One pixel of frame all round, so an empty bar still reads as a bar. */
	private static final int BORDER = 1;

	private static final int COLOUR_FRAME = 0xFF1B1B2F;
	private static final int COLOUR_EMPTY = 0xFF2E2E44;
	private static final int COLOUR_FILL = 0xFF4C7DF0;

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		if (!ClientMana.hasData()) {
			return;
		}

		int left = HudBars.left();
		int top = HudBars.top(HudBars.manaRow());

		float max = ClientMana.max();
		float fraction = max <= 0.0F
				? 0.0F
				: Math.clamp(ClientMana.current(Util.getMillis()) / max, 0.0F, 1.0F);

		int inner = HudBars.BAR_WIDTH - 2 * BORDER;
		int filled = Math.round(inner * fraction);

		graphics.fill(left, top,
				left + HudBars.BAR_WIDTH, top + HudBars.BAR_HEIGHT, COLOUR_FRAME);
		graphics.fill(left + BORDER, top + BORDER,
				left + HudBars.BAR_WIDTH - BORDER, top + HudBars.BAR_HEIGHT - BORDER, COLOUR_EMPTY);

		// Skipped at zero rather than drawn as a zero-width rectangle, which some fill
		// paths treat as a degenerate quad rather than as nothing.
		if (filled > 0) {
			graphics.fill(left + BORDER, top + BORDER,
					left + BORDER + filled, top + HudBars.BAR_HEIGHT - BORDER, COLOUR_FILL);
		}
	}
}

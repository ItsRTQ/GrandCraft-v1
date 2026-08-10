package com.hrtq.grandcraft.client.hud;

import com.hrtq.grandcraft.GrandCraft;
import com.hrtq.grandcraft.client.ClientDowned;
import com.hrtq.grandcraft.client.GrandCraftKeyMappings;
import java.util.Locale;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

/**
 * What a downed player is shown: how long they have, what someone else is doing
 * about it, and the way out under their own steam.
 *
 * <p>Drawn in the middle of the screen rather than in the corner column with the
 * resource bars, and that is not decoration. Going down is a state the player is
 * stuck in for up to a minute with almost nothing to do, and the one number that
 * matters — how long is left — has to be the thing they are looking at. The bars
 * describe a player who is playing; this describes one who is waiting.
 *
 * <p><strong>Three lines at most, and usually two.</strong> The clock is always
 * there. The give-up prompt turns into a filling bar while the key is held. The
 * revive line only appears once someone has actually started, because a prompt about
 * a rescue nobody is attempting is worse than silence.
 *
 * <p>Everything here is read from {@link ClientDowned} and decides nothing. The
 * server owns the clock, the revive and the death; a client that drew none of this
 * would play out exactly the same.
 */
public final class DownedOverlayElement implements HudElement {
	public static final Identifier ID = GrandCraft.id("downed_overlay");

	/** Vanilla's line height, which everything here is spaced by. */
	private static final int LINE_HEIGHT = 9;

	private static final int LINE_GAP = 4;

	/** How far above the centre of the screen the block sits. */
	private static final int ABOVE_CENTRE = 30;

	private static final int BAR_WIDTH = 120;
	private static final int BAR_HEIGHT = 5;

	private static final int TEXT_COLOUR = 0xFFE0E0E0;
	private static final int CLOCK_COLOUR = 0xFFFF6A6A;
	private static final int REVIVE_COLOUR = 0xFF7CE07C;
	private static final int BAR_BACKGROUND = 0xB0000000;
	private static final int BAR_BORDER = 0xFF202020;

	/** Below this many seconds the clock pulses, because it is nearly over. */
	private static final float URGENT_SECONDS = 10.0F;

	private static final long PULSE_MILLIS = 400L;

	private static final int TICKS_PER_SECOND = 20;

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft client = Minecraft.getInstance();

		if (client.font == null || !ClientDowned.isDowned()) {
			return;
		}

		long now = Util.getMillis();
		int centreX = graphics.guiWidth() / 2;
		int y = graphics.guiHeight() / 2 - ABOVE_CENTRE;

		y = drawClock(graphics, client, centreX, y, now);
		y = drawRevive(graphics, client, centreX, y);
		drawGiveUp(graphics, client, centreX, y);
	}

	/**
	 * The headline: seconds left, to one decimal.
	 *
	 * <p>One decimal rather than whole seconds for the reason the Combat Master badge
	 * gives at the other end of the scale — a number that sits still reads as a number
	 * that has stopped, and a player watching a stopped clock while dying will report
	 * it as frozen.
	 */
	private static int drawClock(GuiGraphicsExtractor graphics, Minecraft client,
			int centreX, int y, long now) {
		float seconds = ClientDowned.bleedOutTicks(now) / (float) TICKS_PER_SECOND;

		// Pulsing rather than merely reddening: the colour is already red, so a second
		// colour would have to be one nobody reads as more urgent than red.
		boolean dim = seconds <= URGENT_SECONDS && (now / PULSE_MILLIS) % 2 == 0;

		graphics.centeredText(client.font,
				Component.translatable("screen.grandcraft.hud.downed",
						String.format(Locale.ROOT, "%.1f", Math.max(0.0F, seconds))),
				centreX, y, dim ? TEXT_COLOUR : CLOCK_COLOUR);

		return y + LINE_HEIGHT + LINE_GAP;
	}

	/**
	 * Somebody is picking you up, and how far they have got.
	 *
	 * <p>Silent until the first tick of progress arrives. The total it is measured
	 * against travels on the packet, because a client has no other way to know it —
	 * see {@code DownedPayload}.
	 */
	private static int drawRevive(GuiGraphicsExtractor graphics, Minecraft client,
			int centreX, int y) {
		int ticks = ClientDowned.reviveTicks();

		if (ticks <= 0) {
			return y;
		}

		int total = ClientDowned.reviveTotalTicks();

		graphics.centeredText(client.font,
				Component.translatable("screen.grandcraft.hud.downed.reviving"),
				centreX, y, REVIVE_COLOUR);

		drawBar(graphics, centreX, y + LINE_HEIGHT + 1, Math.min(1.0F, ticks / (float) total),
				REVIVE_COLOUR);

		return y + LINE_HEIGHT + BAR_HEIGHT + LINE_GAP + 1;
	}

	/**
	 * The prompt, or the hold in progress.
	 *
	 * <p>It names the key by its current binding rather than by the letter it shipped
	 * on, so a player who rebound it is told what they actually pressed.
	 */
	private static void drawGiveUp(GuiGraphicsExtractor graphics, Minecraft client,
			int centreX, int y) {
		int held = ClientDowned.giveUpTicks();
		int total = ClientDowned.giveUpTotalTicks();

		if (held <= 0) {
			graphics.centeredText(client.font,
					Component.translatable("screen.grandcraft.hud.downed.give_up",
							GrandCraftKeyMappings.GIVE_UP.getTranslatedKeyMessage()),
					centreX, y, TEXT_COLOUR);
			return;
		}

		graphics.centeredText(client.font,
				Component.translatable("screen.grandcraft.hud.downed.giving_up"),
				centreX, y, CLOCK_COLOUR);

		drawBar(graphics, centreX, y + LINE_HEIGHT + 1, Math.min(1.0F, held / (float) total),
				CLOCK_COLOUR);
	}

	/** Border, ground, fill — the same three-fill idiom the radial slots use. */
	private static void drawBar(GuiGraphicsExtractor graphics, int centreX, int top,
			float fraction, int colour) {
		int left = centreX - BAR_WIDTH / 2;
		int right = left + BAR_WIDTH;
		int bottom = top + BAR_HEIGHT;

		graphics.fill(left - 1, top - 1, right + 1, bottom + 1, BAR_BORDER);
		graphics.fill(left, top, right, bottom, BAR_BACKGROUND);
		graphics.fill(left, top, left + Math.round(BAR_WIDTH * fraction), bottom, colour);
	}
}

package com.hrtq.grandcraft.client.hud;

import com.hrtq.grandcraft.GrandCraft;
import com.hrtq.grandcraft.client.ClientStamina;
import java.util.Locale;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudStatusBarHeightRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

/**
 * The stamina bar, drawn in the right-hand status column above the hunger bar.
 *
 * <p>Attached to the food bar's layer, so it inherits vanilla's own decision about
 * when status bars are visible — it disappears in creative and spectator without a
 * gamemode check here.
 *
 * <h2>How the art is organised</h2>
 * Twenty whole-bar frames rather than one fill sprite clipped to a fraction, so how
 * the bar depletes is the artist's decision rather than a rectangle's:
 * {@code level_00} is full, {@code level_19} is empty, and the eighteen between are
 * the depletion steps in order.
 *
 * <p>Exhaustion is a separate three-frame loop and <strong>replaces</strong> the
 * level frames entirely rather than tinting them. That is deliberate: while
 * exhausted the pool is refilling but the player still cannot act, so a bar showing
 * a rising green level would be telling them the opposite of the thing that matters.
 * Red means locked, and it clears at the exact moment acting becomes possible again.
 */
public final class StaminaBarElement implements HudElement {
	public static final Identifier ID = GrandCraft.id("stamina_bar");

	/**
	 * Exactly one vanilla status row: ten 8px icons plus the trailing pixel, and the
	 * 9px icon height. Every frame is authored at this size.
	 */
	private static final int BAR_WIDTH = 81;
	private static final int BAR_HEIGHT = 9;

	/** Vanilla's spacing between right-hand rows, which is what shifts the air bar. */
	private static final int RESERVED_HEIGHT = 10;

	/** Vanilla's own right-hand anchor, read out of {@code Hud.extractPlayerHealth}. */
	private static final int RIGHT_EDGE_OFFSET = 91;
	private static final int BASELINE_OFFSET = 39;

	/** Frame 0 is full, the last is empty, and the rest deplete in order. */
	private static final Identifier[] LEVELS = levels(20);

	private static final Identifier[] EXHAUSTED = {
			texture("exhausted_0"),
			texture("exhausted_1"),
			texture("exhausted_2")};

	/** Milliseconds per frame of the exhausted pulse. */
	private static final long EXHAUSTED_FRAME_MILLIS = 200L;

	/**
	 * The row this element occupies, which is what shifts the air bar up.
	 *
	 * <p>Zero when there is nothing to draw, so the other bars close the gap rather
	 * than leaving a hole where the stamina bar would have been.
	 */
	public static int reservedHeight() {
		return ClientStamina.hasData() ? RESERVED_HEIGHT : 0;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		if (!ClientStamina.hasData()) {
			return;
		}

		long now = Util.getMillis();

		int right = graphics.guiWidth() / 2 + RIGHT_EDGE_OFFSET;
		int left = right - BAR_WIDTH;

		// The registry reports how far this element sits above the baseline, which is
		// what keeps it clear of the hunger bar and pushes the air bar up in turn.
		int top = graphics.guiHeight() - BASELINE_OFFSET - HudStatusBarHeightRegistry.getHeight(ID);

		graphics.blit(RenderPipelines.GUI_TEXTURED, frame(now), left, top,
				0.0F, 0.0F, BAR_WIDTH, BAR_HEIGHT, BAR_WIDTH, BAR_HEIGHT);
	}

	private static Identifier frame(long nowMillis) {
		if (ClientStamina.exhausted()) {
			return EXHAUSTED[(int) (nowMillis / EXHAUSTED_FRAME_MILLIS % EXHAUSTED.length)];
		}

		float max = ClientStamina.max();
		float fraction = max <= 0.0F ? 0.0F : ClientStamina.current(nowMillis) / max;

		// Rounding rather than truncating so full and empty each claim half a step
		// instead of a whole one, which is what stops the bar reading as full when it
		// is merely close.
		int index = Math.round((1.0F - fraction) * (LEVELS.length - 1));
		return LEVELS[Math.clamp(index, 0, LEVELS.length - 1)];
	}

	private static Identifier[] levels(int count) {
		Identifier[] frames = new Identifier[count];

		for (int i = 0; i < count; i++) {
			// Locale.ROOT because this builds a file path: a default locale with
			// non-ASCII digits would produce a name nothing on disk matches.
			frames[i] = texture(String.format(Locale.ROOT, "level_%02d", i));
		}

		return frames;
	}

	private static Identifier texture(String name) {
		return GrandCraft.id("textures/gui/stamina/" + name + ".png");
	}
}

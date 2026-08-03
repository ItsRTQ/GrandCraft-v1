package com.hrtq.grandcraft.client.hud;

import com.hrtq.grandcraft.GrandCraft;
import com.hrtq.grandcraft.client.ClientStamina;
import java.util.Locale;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

/**
 * The stamina bar, second in the top-left column under health. {@link HudBars} owns
 * where it sits; this owns what it shows.
 *
 * <p>Attached to the health bar's layer, which through it reaches the food bar's, so it
 * inherits vanilla's own decision about when status bars are visible — it disappears in
 * creative and spectator without a gamemode check here.
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

	/** Frame 0 is full, the last is empty, and the rest deplete in order. */
	private static final Identifier[] LEVELS = levels(20);

	private static final Identifier[] EXHAUSTED = {
			texture("exhausted_0"),
			texture("exhausted_1"),
			texture("exhausted_2")};

	/** Milliseconds per frame of the exhausted pulse. */
	private static final long EXHAUSTED_FRAME_MILLIS = 200L;

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		if (!ClientStamina.hasData()) {
			return;
		}

		graphics.blit(RenderPipelines.GUI_TEXTURED, frame(Util.getMillis()),
				HudBars.left(), HudBars.top(HudBars.staminaRow()),
				0.0F, 0.0F, HudBars.BAR_WIDTH, HudBars.BAR_HEIGHT,
				HudBars.BAR_WIDTH, HudBars.BAR_HEIGHT);
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

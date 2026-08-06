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
 * Twenty-seven whole-bar frames rather than one fill sprite clipped to a fraction, so
 * how the bar depletes is the artist's decision rather than a rectangle's:
 * {@code level_00} is full, {@code level_26} is empty, and the twenty-five between are
 * the depletion steps in order.
 *
 * <p>The count is read from {@link #LEVELS} at every use, so a redelivery with a
 * different number of frames needs one number changed here and nothing else. The
 * second delivery (2026-08-05) replaced twenty frames with twenty-seven and that was
 * the whole edit.
 *
 * <p>The artist numbers frames from one and this numbers them from zero, so
 * {@code Stamina1} ships as {@code level_00}. <strong>That ordering is not taken on
 * trust</strong> — the frames lose a steady fourteen to fifteen green pixels each, 535
 * down to the bare trough, which is what confirms the sequence is monotonic and the
 * right way round. Same check the health bar's frames were put through, and worth
 * repeating on any redelivery: the delivered names are inconsistent
 * ({@code Stamina1}-{@code Stamina15}, then {@code Stamin16}-{@code Stamin27}), so the
 * filename is exactly the thing not to rely on.
 *
 * <p>Exhaustion is a separate three-frame loop and <strong>replaces</strong> the
 * level frames entirely rather than tinting them. That is deliberate: while
 * exhausted the pool is refilling but the player still cannot act, so a bar showing
 * a rising green level would be telling them the opposite of the thing that matters.
 * Red means locked, and it clears at the exact moment acting becomes possible again.
 *
 * <p><strong>The three exhausted frames are derived from {@code level_00}, not
 * authored.</strong> The first delivery shipped its own red frames; the second did not,
 * and drawing the first delivery's red over the second delivery's green changed the
 * bar's <em>shape</em> the moment stamina ran out, which reads as a glitch rather than
 * as a state. Each opaque pixel of the full frame is remapped by luminance onto a red
 * ramp: {@code red = luminance * 1.162 * step}, with green and blue at
 * {@code 0.359 * red}. Those two numbers are the first delivery's own — its fill was
 * {@code (220, 79, 79)}, and 1.162 is what puts the new art's fill
 * {@code (153, 229, 80)} on that same 220. The three steps are its pulse, 220 → 154 →
 * 44. So the shape is the new artist's and the colour is the old artist's.
 *
 * <p><strong>A tint at draw time cannot do this</strong>, which is worth recording
 * because it is the obvious first idea: the blit's colour argument multiplies, and the
 * green fill's red channel is only 153, so the reddest a tint could ever make it is
 * {@code (153, 0, 0)} — a third darker than the red it is replacing, and unreachable
 * whatever colour is passed. Bake, do not tint, when the source is the wrong hue.
 *
 * <p>Regenerate them if the level art is ever redelivered again, or the bar will change
 * shape on exhaustion once more.
 */
public final class StaminaBarElement implements HudElement {
	public static final Identifier ID = GrandCraft.id("stamina_bar");

	/** Frame 0 is full, the last is empty, and the rest deplete in order. */
	private static final Identifier[] LEVELS = levels(27);

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

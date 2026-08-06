package com.hrtq.grandcraft.client.hud;

import com.hrtq.grandcraft.GrandCraft;
import com.hrtq.grandcraft.client.ClientMana;
import java.util.Locale;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

/**
 * The mana bar, third in the top-left column under stamina. {@link HudBars} owns where
 * it sits; this owns what it shows.
 *
 * <p>Attached to the stamina bar's layer, so like it this inherits vanilla's own
 * decision about when status bars are visible — it disappears in creative and
 * spectator with no gamemode check here.
 *
 * <h2>How the art is organised</h2>
 * Twenty-seven whole-bar frames rather than one fill sprite clipped to a fraction, so
 * how the bar empties is the artist's decision rather than a rectangle's:
 * {@code level_00} is full, {@code level_26} is empty, and the twenty-five between are
 * the depletion steps in order. Same arrangement, same count and same 81x9 canvas as
 * the stamina bar delivered beside it, which is what makes the two read as one set.
 *
 * <p>The artist numbers frames from one and this numbers them from zero, and the
 * delivery's own names were inconsistent — {@code mana full}, then {@code mana1t},
 * then {@code mana2}-{@code mana26} — so they were renamed on the way in.
 * <strong>The order is not taken on trust:</strong> the frames lose a steady fourteen
 * to fifteen blue pixels each, 529 down to the bare trough, which is what confirms the
 * sequence is monotonic and the right way round. Same check the health and stamina
 * frames were put through, and the reason it is worth repeating is above — a filename
 * is exactly the thing not to rely on here.
 *
 * <h2>The shipped frames are brightened, and the delivery is not</h2>
 * As delivered the bar was the darkest of the three by some way — fill luminance 70.5
 * against stamina's 189.3 and health's 80.8 — and worse, its fill and its trough were
 * only 2.16x apart where the other two are near 5x. Full and empty were hard to tell
 * apart, which is what it was reported as: <em>"it seems darker than what the png
 * shows"</em>. It was not; the art is simply dark.
 *
 * <p>So every frame is regenerated from {@code assets/player/mana-bar/} with a gain
 * that <strong>ramps in with luminance</strong>:
 * {@code gain = 1 + 0.75 * clamp((luminance - 35) / 30)}. The fill is bright enough to
 * take the full lift and the trough is below the ramp entirely, so the fill goes to
 * 119.3 and the trough does not move at all — contrast 2.16x to 3.65x. A flat multiply
 * was offered and rejected for exactly that reason: it raises both ends and leaves the
 * two states as hard to tell apart as before.
 *
 * <p>119.3 is about the ceiling. The fill clamps at {@code (82, 112, 255)} and a
 * saturated blue cannot be pushed past it without desaturating toward white, which is
 * the artist's call and not this file's. It stays dimmer than the stamina bar because
 * blue is dimmer than yellow-green, which is colorimetry rather than a defect.
 *
 * <p><strong>Regenerate from the delivery, never from the shipped frames</strong>, or
 * the gain compounds. Same rule as the stamina bar's exhausted frames.
 *
 * <h2>It reads an extrapolated value, unlike health</h2>
 * {@link ClientMana#current(long)} advances the pool from the rate in the last sync
 * rather than waiting for the next one, so the bar climbs smoothly instead of stepping
 * every four ticks. That is why this takes the millisecond clock and
 * {@code HealthBarElement} does not: health is replicated by vanilla and the local
 * value is already true.
 *
 * <p>Mana has no exhausted state, so there is no second set of frames here. Stamina's
 * exists because hitting zero locks the actor out; running out of mana simply means the
 * next spell is refused.
 */
public final class ManaBarElement implements HudElement {
	public static final Identifier ID = GrandCraft.id("mana_bar");

	/** Frame 0 is full, the last is empty, and the rest deplete in order. */
	private static final Identifier[] LEVELS = levels(27);

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		if (!ClientMana.hasData()) {
			return;
		}

		graphics.blit(RenderPipelines.GUI_TEXTURED, frame(Util.getMillis()),
				HudBars.left(), HudBars.top(HudBars.manaRow()),
				0.0F, 0.0F, HudBars.BAR_WIDTH, HudBars.BAR_HEIGHT,
				HudBars.BAR_WIDTH, HudBars.BAR_HEIGHT);
	}

	private static Identifier frame(long nowMillis) {
		float max = ClientMana.max();
		float fraction = max <= 0.0F
				? 0.0F
				: Math.clamp(ClientMana.current(nowMillis) / max, 0.0F, 1.0F);

		// Rounding rather than truncating so full and empty each claim half a step
		// instead of a whole one, which is what stops the bar reading as full when it
		// is merely close. Same rule as the other two bars, deliberately.
		int index = Math.round((1.0F - fraction) * (LEVELS.length - 1));
		return LEVELS[Math.clamp(index, 0, LEVELS.length - 1)];
	}

	private static Identifier[] levels(int count) {
		Identifier[] frames = new Identifier[count];

		for (int i = 0; i < count; i++) {
			// Locale.ROOT because this builds a file path: a default locale with
			// non-ASCII digits would produce a name nothing on disk matches.
			frames[i] = GrandCraft.id(
					String.format(Locale.ROOT, "textures/gui/mana/level_%02d.png", i));
		}

		return frames;
	}
}

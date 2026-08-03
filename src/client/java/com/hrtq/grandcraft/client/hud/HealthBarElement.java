package com.hrtq.grandcraft.client.hud;

import com.hrtq.grandcraft.GrandCraft;
import java.util.Locale;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * The player's health, drawn as an authored bar in place of vanilla's hearts.
 *
 * <p>Vanilla's own health element is <strong>removed</strong> rather than drawn over —
 * see {@code GrandCraftClient}. Hearts and a bar saying the same thing in two visual
 * languages is worse than either alone, and a row of hearts is exactly the vanilla
 * furniture this mod exists to replace.
 *
 * <h2>How the art is organised</h2>
 *
 * Twenty-six whole-bar frames rather than one fill sprite clipped to a fraction, which
 * is the same arrangement the stamina bar uses and for the same reason: how the bar
 * empties is the artist's decision rather than a rectangle's. {@code level_00} is full,
 * {@code level_25} is empty, and the twenty-four between are the depletion steps in
 * order.
 *
 * <p>The animator delivered them as {@code health-bar-Full}, {@code health1} through
 * {@code health24}, then {@code health-bar-Empty}; they were renamed on the way in so
 * that the index is the frame number and the loop below needs no lookup table. That
 * ordering is not taken on trust — the frames lose a steady fifteen red pixels each,
 * 371 down to zero, which is what confirms the sequence is monotonic and the right way
 * round.
 *
 * <h2>No packet, and no store</h2>
 *
 * Unlike stamina and mana this reads the local player directly. Health is already
 * synced for every visible entity, so a client copy would be a second source of truth
 * for a number vanilla is keeping current anyway. It also means the bar follows
 * {@code MAX_HEALTH} for free, which is what Constitution and bought attribute points
 * both move.
 *
 * <p><strong>Absorption is not shown.</strong> Vanilla draws it as extra yellow hearts;
 * there is no frame for it here, and folding it into the fraction would make a golden
 * apple read as healing rather than as shielding. A player at full health with
 * absorption sees a full bar, which is true but incomplete. Worth revisiting if
 * absorption ever becomes common — nothing in the mod grants it today.
 */
public final class HealthBarElement implements HudElement {
	public static final Identifier ID = GrandCraft.id("health_bar");

	/** Frame 0 is full, the last is empty, and the rest deplete in order. */
	private static final Identifier[] LEVELS = levels(26);

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		LocalPlayer player = Minecraft.getInstance().player;

		if (player == null) {
			return;
		}

		float max = player.getMaxHealth();
		float fraction = max <= 0.0F ? 0.0F : Math.clamp(player.getHealth() / max, 0.0F, 1.0F);

		graphics.blit(RenderPipelines.GUI_TEXTURED, frame(fraction),
				HudBars.left(), HudBars.top(HudBars.healthRow()),
				0.0F, 0.0F, HudBars.BAR_WIDTH, HudBars.BAR_HEIGHT,
				HudBars.BAR_WIDTH, HudBars.BAR_HEIGHT);
	}

	private static Identifier frame(float fraction) {
		// Rounding rather than truncating so full and empty each claim half a step
		// instead of a whole one, which is what stops the bar reading as full when it is
		// merely close. Same rule as the stamina bar, deliberately.
		int index = Math.round((1.0F - fraction) * (LEVELS.length - 1));

		return LEVELS[Math.clamp(index, 0, LEVELS.length - 1)];
	}

	private static Identifier[] levels(int count) {
		Identifier[] frames = new Identifier[count];

		for (int i = 0; i < count; i++) {
			// Locale.ROOT because this builds a file path: a default locale with
			// non-ASCII digits would produce a name nothing on disk matches.
			frames[i] = GrandCraft.id(
					String.format(Locale.ROOT, "textures/gui/health/level_%02d.png", i));
		}

		return frames;
	}
}

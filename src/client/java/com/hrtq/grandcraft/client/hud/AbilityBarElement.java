package com.hrtq.grandcraft.client.hud;

import com.hrtq.grandcraft.GrandCraft;
import com.hrtq.grandcraft.client.ClientCombatMaster;
import com.hrtq.grandcraft.skill.ClassPassive;
import java.util.Locale;
import java.util.function.Function;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

/**
 * The ability bar: the artist's frame for the four ability keys, along the bottom of
 * the screen where the hotbar used to be.
 *
 * <p><strong>This is the file to edit when the bar is the wrong size or in the wrong
 * place.</strong> Three numbers decide all of it — {@link #BAR_WIDTH},
 * {@link #BOTTOM_MARGIN} and {@link #XP_BAR_DROP} — and each is named for the single
 * complaint it answers.
 *
 * <h2>Frame only, for now</h2>
 * Nothing is drawn <em>in</em> the four slots, because there is nothing to draw: no
 * ability has an icon, or an effect, yet. The frame is here so the bottom of the screen
 * stops being an empty strip and so the layout is settled before the icons arrive.
 *
 * <p>The art has three plain slots and one larger crowned one, which is exactly the
 * shape of the loadout — three abilities on {@code 1}–{@code 3} and the ultimate on
 * {@code 4}. When icons exist they go in those four boxes and nothing about this
 * position changes.
 *
 * <h2>Why the experience bar had to move</h2>
 * Removing the hotbar left a 22-pixel strip of nothing along the bottom with the
 * experience bar floating above it. {@link #shiftDown} pushes the experience bar and
 * its level number down into that strip, which frees the row above for this — so the
 * bottom of the screen reads ability bar, then experience, then the screen edge.
 *
 * <p>Done by wrapping vanilla's own elements rather than removing and reimplementing
 * them: the experience bar has three modes in 26.2 (experience, jump meter, locator)
 * and reimplementing it would mean owning all three forever. A wrapped element is
 * vanilla's drawing, two pixels lower.
 *
 * <h2>Not on the status-bar layer, and that is deliberate</h2>
 * The three resource bars hang off {@code VanillaHudElements.FOOD_BAR}, which is what
 * makes them inherit vanilla's "show status bars?" rule and vanish in creative and
 * spectator. This one hangs off the held-item name instead, beside the radial wheel.
 *
 * <p>A resource you cannot spend is worth hiding in creative; a row saying what four
 * keys do is not, and the keys still work there. <strong>Attaching it to the status
 * column cost a debugging round</strong> — in a creative test world every element on
 * that column disappeared at once, which reads as a broken HUD rather than as a
 * gamemode rule.
 */
public final class AbilityBarElement implements HudElement {
	public static final Identifier ID = GrandCraft.id("ability_bar");

	private static final Identifier TEXTURE = GrandCraft.id("textures/gui/ability/bar.png");

	/**
	 * The artwork, cropped to its own alpha bounds — every delivery so far has arrived
	 * on an oversized canvas, and a texture with padding renders smaller than its box
	 * says it should.
	 *
	 * <p>These two are the source size, not the drawn size. They set the aspect ratio,
	 * so the drawn height is derived from them and never guessed.
	 */
	private static final int TEXTURE_WIDTH = 1418;
	private static final int TEXTURE_HEIGHT = 444;

	/**
	 * How wide the bar draws.
	 *
	 * <p><strong>The single number to change if the bar is too big or too small.</strong>
	 * The height follows it, from the texture's aspect ratio.
	 *
	 * <p>It started at 182 — vanilla's hotbar and experience-bar width, so the two lined
	 * up exactly — and that was too large in game (user, 2026-08-04). Being the right
	 * size beats being the same width as the bar underneath, so the alignment was given
	 * up rather than the size.
	 *
	 * <p>Two other constants are measured against this and do not follow it
	 * automatically: {@link #HELD_ITEM_LIFT} has to clear the bar's top edge, and
	 * {@link #BOTTOM_MARGIN} has to clear the experience level number below it. Changing
	 * the width by much means re-checking both.
	 */
	private static final int BAR_WIDTH = 124;

	/** Derived, never typed: a hand-written height would stretch the artwork. */
	private static final int BAR_HEIGHT =
			Math.round((float) BAR_WIDTH * TEXTURE_HEIGHT / TEXTURE_WIDTH);

	/**
	 * How far the experience bar and its level number move down, in pixels.
	 *
	 * <p>Sized against vanilla's own figure rather than guessed:
	 * {@code ContextualBar.top()} is {@code guiHeight - 24} and the bar is 5 tall, so a
	 * drop of 15 leaves it four pixels clear of the bottom edge. <strong>Note it is not
	 * 22</strong> — the removed hotbar was 22 tall, but the experience bar never sat on
	 * top of it, and dropping by the hotbar's height would push it off the screen.
	 *
	 * <p><strong>Raise this if the experience bar still sits too high; lower it if it
	 * hangs off the bottom.</strong>
	 */
	public static final int XP_BAR_DROP = 15;

	/**
	 * How far the held-item name moves <em>up</em>, negative because it is a lift.
	 *
	 * <p>Vanilla draws it at {@code guiHeight - 59}, which lands squarely inside this
	 * bar — so without this, the item name would flash across the artwork every time the
	 * wheel changes the held item. Lifting it puts it back above the furniture, where it
	 * has always been relative to the hotbar it used to sit over.
	 *
	 * <p>Sized against the bar it has to clear, so it moved when the bar shrank: with a
	 * 39-pixel bar whose top edge is at {@code guiHeight - 61}, a lift of 16 puts the
	 * name at {@code guiHeight - 75} — five pixels of air above the frame.
	 *
	 * <p><strong>The number to change if the item name overlaps the bar or floats too
	 * far above it.</strong>
	 */
	public static final int HELD_ITEM_LIFT = -16;

	/**
	 * Air between the bottom of the screen and the bottom of this bar.
	 *
	 * <p>Has to clear the experience level number in its new home, not just the bar —
	 * the number is drawn above the bar and moves with it. <strong>The number to change
	 * if the ability bar and the experience bar overlap or sit too far apart.</strong>
	 */
	private static final int BOTTOM_MARGIN = 22;

	/**
	 * The Combat Master badge: a plate above the bar naming the passive and counting
	 * down, at the 70% opacity the user asked for.
	 *
	 * <p>{@code 0xB3} is 70% of 255, and it is the alpha byte on <em>both</em> the plate
	 * and its text — a translucent plate under solid text reads as a bug rather than as
	 * a faded badge.
	 *
	 * <p>Coded rather than blitted, and temporarily: there is no artwork for a buff
	 * indicator, and a buff nobody can see is indistinguishable from a broken one
	 * ({@code tuning.md} lesson 4). The same call the mana bar was shipped on, and it
	 * came out the same way — mana was three fills against {@link HudBars} until the
	 * frames arrived, and when they did the swap was one blit with the geometry
	 * untouched. That is the point of drawing against {@code HudBars} rather than
	 * against numbers of one's own.
	 */
	private static final int BADGE_BACKGROUND = 0xB3120C04;
	private static final int BADGE_BORDER = 0xB3FFC24A;
	private static final int BADGE_TEXT = 0xB3FFE9B0;

	/** Air inside the plate around its text, and between the plate and the bar. */
	private static final int BADGE_PADDING = 4;
	private static final int BADGE_GAP = 3;

	/**
	 * Wraps a HUD element so it draws somewhere else vertically.
	 *
	 * <p>Handed to {@code HudElementRegistry.replaceElement}, which gives up the original
	 * element and takes back whatever should draw in its place. Wrapping rather than
	 * reimplementing means vanilla keeps deciding <em>what</em> to draw — including which
	 * of the info bar's three modes (experience, jump meter, locator) is showing — and
	 * this only changes where.
	 *
	 * <p>The matrix is pushed and popped around the call, so nothing drawn afterwards
	 * inherits the offset. Getting that wrong would move every element registered after
	 * this one, which is most of the HUD.
	 *
	 * @param dy pixels down the screen; negative moves up
	 */
	public static Function<HudElement, HudElement> shiftedBy(int dy) {
		return original -> (graphics, delta) -> {
			graphics.pose().pushMatrix();
			graphics.pose().translate(0.0F, dy);

			original.extractRenderState(graphics, delta);

			graphics.pose().popMatrix();
		};
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		int left = (graphics.guiWidth() - BAR_WIDTH) / 2;
		int top = graphics.guiHeight() - BOTTOM_MARGIN - BAR_HEIGHT;

		// The whole texture scaled into the box: u and v are zero and the source size is
		// given as the destination size, which is how vanilla asks for a sprite fitted to
		// a rectangle rather than a region cut out of an atlas.
		graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
				left, top, 0.0F, 0.0F,
				BAR_WIDTH, BAR_HEIGHT, BAR_WIDTH, BAR_HEIGHT);

		extractCombatMaster(graphics, top);
	}

	/**
	 * The buff badge, drawn above the bar while a Combat Master window is open.
	 *
	 * <p>Lives here rather than in an element of its own because it is positioned
	 * against this bar and nothing else — a separate element would mean exporting the
	 * geometry so it could be positioned, which is more moving parts for the same
	 * picture.
	 *
	 * <p>Sized to its own text, so a longer passive name widens the plate rather than
	 * overflowing it. The countdown is drawn to one decimal because five seconds is
	 * short enough that whole seconds would sit still for a fifth of the window.
	 */
	private void extractCombatMaster(GuiGraphicsExtractor graphics, int barTop) {
		Minecraft client = Minecraft.getInstance();
		long now = Util.getMillis();

		if (client.font == null || !ClientCombatMaster.isActive(now)) {
			return;
		}

		Component text = Component.translatable("screen.grandcraft.hud.combat_master",
				ClassPassive.COMBAT_MASTER.displayName(),
				String.format(Locale.ROOT, "%.1f", ClientCombatMaster.secondsLeft(now)));

		int textWidth = client.font.width(text);
		int width = textWidth + BADGE_PADDING * 2;
		int height = LINE_HEIGHT + BADGE_PADDING;

		int left = (graphics.guiWidth() - width) / 2;
		int bottom = barTop - BADGE_GAP;
		int top = bottom - height;

		// Border then inside, one pixel in — the same two-fill idiom the radial slots
		// and the skill nodes use.
		graphics.fill(left, top, left + width, bottom, BADGE_BORDER);
		graphics.fill(left + 1, top + 1, left + width - 1, bottom - 1, BADGE_BACKGROUND);

		graphics.centeredText(client.font, text,
				graphics.guiWidth() / 2, top + (height - LINE_HEIGHT) / 2 + 1, BADGE_TEXT);
	}

	/** Vanilla's line height, which is what the badge is sized around. */
	private static final int LINE_HEIGHT = 9;
}

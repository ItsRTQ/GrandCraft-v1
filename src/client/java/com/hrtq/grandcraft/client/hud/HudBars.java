package com.hrtq.grandcraft.client.hud;

import com.hrtq.grandcraft.client.ClientMana;
import com.hrtq.grandcraft.client.ClientStamina;
import java.util.function.Function;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;

/**
 * Where the player's three resource bars sit, and in what order.
 *
 * <p><strong>This is the file to edit when the bars are in the wrong place.</strong>
 * The elements themselves decide what to draw; this decides where. Every measurement
 * the three share lives here rather than being repeated in each of them, because a
 * column whose members disagree about its left edge is not a column.
 *
 * <h2>Top left, not vanilla's status area</h2>
 *
 * The bars used to ride the right-hand status column above the hunger bar, anchored to
 * vanilla's own numbers and declared through {@code HudStatusBarHeightRegistry} so the
 * air bar would move up rather than draw over them. They are the player's primary
 * readouts in a mod that replaces vanilla combat outright, and the corner of the screen
 * is a better home for them than a row borrowed from food.
 *
 * <p>Two consequences worth knowing, both of which removed code rather than adding it:
 * nothing here needs {@code HudStatusBarHeightRegistry.getHeight} any more, because
 * this column stacks from a known corner instead of from a shifting baseline; and
 * nothing reserves a right-hand row any more, because the bars no longer occupy one and
 * reserving one would push the air bar up to clear a gap nothing fills.
 *
 * <p>The elements are still attached to the food bar's layer, which is a separate
 * matter from where they draw: that is what makes them inherit vanilla's own decision
 * about when status bars are shown, so they disappear in creative and spectator with no
 * gamemode check anywhere in this package.
 *
 * <h2>Rows close up</h2>
 *
 * A bar with nothing to show is not drawn, and the ones below it move up rather than
 * leaving a hole — see {@link #manaRow()}. A configured mana pool of zero is the case
 * that matters: it is the feature's off switch, and an empty bar that never moves
 * implies a resource the player does not have.
 *
 * <h2>Vanilla's hunger bar is in this column too</h2>
 * Moved here rather than left in its own corner, so everything the player reads about
 * themselves is in one place (user, 2026-08-04). It is vanilla's element, translated —
 * see {@link #intoColumn()} — not a reimplementation, so the icons, the saturation
 * shake and the hunger-effect variant all keep working without being rebuilt here.
 *
 * <p>It conveniently shares this column's measurements already: a vanilla status row is
 * 81x9, which is exactly what the artist authored the other three bars at.
 *
 * <h2>Armour is in it too, but redrawn rather than translated</h2>
 * Under the hunger bar, and last (user, 2026-08-05). Unlike hunger it is not vanilla's
 * element moved — {@link ArmourBarElement} explains why in full, but the short version
 * is that vanilla anchors armour above the <em>hearts</em>, so its position moves with
 * max health, which this mod changes constantly.
 *
 * <p><strong>Air is still left where vanilla puts it.</strong> It is not a GrandCraft
 * resource, it is absent almost always, and it is the one row vanilla draws on the right
 * that nothing here has a reason to claim.
 */
public final class HudBars {
	/**
	 * Exactly one vanilla status row: ten 8px icons plus the trailing pixel, and the
	 * 9px icon height.
	 *
	 * <p>Kept after the move off vanilla's anchor because it is the size every frame is
	 * authored at, for all three bars, across four separate deliveries. It is the
	 * artist's canvas now rather than vanilla's row, and changing it means reworking
	 * <strong>83 PNGs</strong> — 26 health, 27 stamina and its 3 exhausted, 27 mana.
	 */
	public static final int BAR_WIDTH = 81;
	public static final int BAR_HEIGHT = 9;

	/**
	 * The corner inset. Clear of the screen edge without drifting into the middle;
	 * vanilla's own hotbar and effect icons sit at a comparable margin.
	 */
	private static final int LEFT_MARGIN = 4;
	private static final int TOP_MARGIN = 4;

	/** One pixel of air between bars, matching vanilla's spacing between status rows. */
	private static final int ROW_SPACING = BAR_HEIGHT + 1;

	private HudBars() {
	}

	/** The column's left edge. Every bar shares it, which is what makes it a column. */
	public static int left() {
		return LEFT_MARGIN;
	}

	/** The top of a given row, counting down from the corner. */
	public static int top(int row) {
		return TOP_MARGIN + row * ROW_SPACING;
	}

	/**
	 * Health first — it is the one a player checks under pressure, and the one that is
	 * always present, so it is the only row whose position never moves.
	 */
	public static int healthRow() {
		return 0;
	}

	public static int staminaRow() {
		return healthRow() + 1;
	}

	/**
	 * Mana last, and only below stamina when stamina is actually drawn.
	 *
	 * <p>This is the only row that has to ask a question: health is unconditional and
	 * stamina sits directly under it, but mana is third and so inherits whatever gap the
	 * bar above it did or did not leave.
	 */
	public static int manaRow() {
		return staminaRow() + (ClientStamina.hasData() ? 1 : 0);
	}

	/**
	 * Hunger, under mana — vanilla's own bar, moved into this column so the player's
	 * readouts are in one place rather than two (user, 2026-08-04).
	 *
	 * <p>Closes up behind mana exactly as mana closes up behind stamina, and for the
	 * same reason.
	 */
	public static int foodRow() {
		return manaRow() + (ClientMana.hasData() ? 1 : 0);
	}

	/**
	 * Armour last, under hunger (user, 2026-08-05).
	 *
	 * <p>The one row that needs no condition. Every row above it asks whether the bar
	 * before it was drawn, because a gap in the middle of a column reads as a broken
	 * element; nothing is below this one, so an absent armour row is just a shorter
	 * column. That is also why it is last rather than, say, beside health: it is the row
	 * the player is most often without.
	 */
	public static int armourRow() {
		return foodRow() + 1;
	}

	// ------------------------------------------------- moving vanilla's hunger bar

	/**
	 * Where vanilla puts a right-hand status row, measured from the screen.
	 *
	 * <p>From {@code Hud.extractPlayerHealth}: rows are anchored at
	 * {@code guiWidth / 2 ± 91} with the first at {@code guiHeight - 39}, and one row is
	 * {@link #BAR_WIDTH} x {@link #BAR_HEIGHT}. The hunger bar is right-aligned, so its
	 * left edge is the anchor less the row width.
	 *
	 * <p>These two are vanilla's numbers, not ours. If the hunger bar ever lands in the
	 * wrong place after a Minecraft update, they are what changed.
	 */
	private static final int VANILLA_ROW_ANCHOR = 91;
	private static final int VANILLA_FIRST_ROW_BASELINE = 39;

	/**
	 * Wraps vanilla's hunger bar so it draws in this column instead of its own corner.
	 *
	 * <p>Handed to {@code HudElementRegistry.replaceElement}. A translation rather than
	 * a reimplementation: vanilla keeps drawing the icons, the shake when saturation is
	 * empty, and the hunger-effect variant — all of which would otherwise have to be
	 * rebuilt here and kept in step forever.
	 *
	 * <p>The offset is computed per frame because both ends of it move: vanilla's anchor
	 * follows the window size, and {@link #foodRow()} follows whether the bars above it
	 * are being drawn.
	 *
	 * <p>Pushed and popped around the call, so nothing after it inherits the offset.
	 */
	public static Function<HudElement, HudElement> intoColumn() {
		return original -> (graphics, delta) -> {
			int vanillaLeft = graphics.guiWidth() / 2 + VANILLA_ROW_ANCHOR - BAR_WIDTH;
			int vanillaTop = graphics.guiHeight() - VANILLA_FIRST_ROW_BASELINE;

			graphics.pose().pushMatrix();
			graphics.pose().translate(left() - vanillaLeft, top(foodRow()) - vanillaTop);

			original.extractRenderState(graphics, delta);

			graphics.pose().popMatrix();
		};
	}
}

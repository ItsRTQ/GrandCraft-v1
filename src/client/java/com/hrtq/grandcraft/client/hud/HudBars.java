package com.hrtq.grandcraft.client.hud;

import com.hrtq.grandcraft.client.ClientStamina;

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
 */
public final class HudBars {
	/**
	 * Exactly one vanilla status row: ten 8px icons plus the trailing pixel, and the
	 * 9px icon height.
	 *
	 * <p>Kept after the move off vanilla's anchor because it is the size every frame is
	 * authored at, for all three bars, by two separate deliveries. It is the artist's
	 * canvas now rather than vanilla's row, and changing it means reworking 46 PNGs.
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
}

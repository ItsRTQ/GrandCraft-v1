package com.hrtq.grandcraft.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * A whole-number entry box for one combat setting.
 *
 * <p>Replaces the sliders this screen used to have, which could not reliably be
 * landed on a specific value. Everything is expressed as an integer — ticks,
 * armour points, and multipliers as whole percent — so there is one input style
 * across the screen and no decimal separator to get locale-dependent.
 *
 * <p>26.2 has no {@code setFilter}, so bad input is tolerated rather than blocked:
 * the text turns red as you type and {@link #intValue()} falls back to the value
 * the field opened with. That is friendlier than silently rewriting what someone
 * is halfway through typing, and the server clamps regardless.
 */
public class TunableField extends EditBox {
	/**
	 * ARGB, and the alpha byte is not optional — {@code 0xE0E0E0} is fully
	 * transparent, not light grey. The valid colour matches vanilla's own
	 * {@code EditBox.DEFAULT_TEXT_COLOR}.
	 */
	private static final int VALID_COLOUR = 0xFFE0E0E0;
	private static final int INVALID_COLOUR = 0xFFFF5555;

	private final int min;
	private final int max;
	private final int fallback;

	/**
	 * Told whenever the text changes, for a screen that draws something derived from
	 * several fields at once.
	 *
	 * <p>A separate hook rather than letting callers use {@code setResponder}, because
	 * the responder is already spoken for: replacing it would silently drop the
	 * red-text validation, and that would show up as bad input being accepted rather
	 * than as a missing callback.
	 *
	 * <p>Initialised to a no-op so the responder never has to null-check.
	 */
	private Runnable changeListener = () -> {
	};

	public TunableField(Font font, int width, int height, Component label,
			int min, int max, int value) {
		super(font, 0, 0, width, height, label);
		this.min = min;
		this.max = max;
		this.fallback = value;

		// Headroom on purpose. Sized to the largest legal value, a field showing "10"
		// against a max of 40 is already at its two-character limit, so every
		// keystroke is silently dropped and the box feels broken. Over-length input
		// is harmless: it shows red and intValue() clamps.
		setMaxLength(Integer.toString(max).length() + 2);
		setValue(Integer.toString(value));

		// Only recolours and notifies; deliberately never calls setValue, which would
		// re-enter this responder through the box's own change notification.
		setResponder(text -> {
			setTextColor(isValid(text) ? VALID_COLOUR : INVALID_COLOUR);
			this.changeListener.run();
		});
	}

	/** @param listener run after every edit; must not write back to this field. */
	public void setChangeListener(Runnable listener) {
		this.changeListener = listener;
	}

	/** Out of range counts as invalid, so a value that would be clamped shows as one. */
	private boolean isValid(String text) {
		Integer parsed = parse(text);
		return parsed != null && parsed >= this.min && parsed <= this.max;
	}

	/**
	 * Selects the whole value when the field gains focus, so typing replaces it.
	 *
	 * <p>These fields always hold a number rather than starting empty, and replacing
	 * is what someone means by clicking one and typing — appending to "10" to get
	 * "105" almost never is.
	 */
	@Override
	public void setFocused(boolean focused) {
		super.setFocused(focused);

		if (focused) {
			// Collapse the selection at the start, then extend it to the end.
			moveCursorToStart(false);
			moveCursorToEnd(true);
		}
	}

	/** The typed value, clamped, or what the field opened with if it does not parse. */
	public int intValue() {
		Integer parsed = parse(getValue());

		if (parsed == null) {
			return this.fallback;
		}

		return Math.max(this.min, Math.min(parsed, this.max));
	}

	private Integer parse(String text) {
		String trimmed = text.trim();

		if (trimmed.isEmpty()) {
			return null;
		}

		try {
			return Integer.valueOf(trimmed);
		} catch (NumberFormatException exception) {
			return null;
		}
	}
}

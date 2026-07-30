package com.hrtq.grandcraft.client.gui;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

/**
 * A slider over an arbitrary numeric range, labelled with its current value.
 *
 * <p>{@link AbstractSliderButton} works in normalised 0-1 space, so this maps to
 * and from a real min/max. Values are read on demand rather than pushed through
 * {@link #applyValue()}, because the config screen only commits when Save is
 * pressed.
 */
public class TunableSlider extends AbstractSliderButton {
	private final Component label;
	private final double min;
	private final double max;
	private final Style style;

	public enum Style {
		/** Whole ticks, shown as "Label: 8". */
		TICKS,
		/** A 0-1 fraction, shown as "Label: 20%". */
		PERCENT
	}

	public TunableSlider(int width, int height, Component label, double min, double max,
			double initialValue, Style style) {
		super(0, 0, width, height, Component.empty(), normalise(initialValue, min, max));
		this.label = label;
		this.min = min;
		this.max = max;
		this.style = style;
		updateMessage();
	}

	private static double normalise(double value, double min, double max) {
		if (max <= min) {
			return 0.0;
		}

		return Math.max(0.0, Math.min(1.0, (value - min) / (max - min)));
	}

	/** The slider's position mapped back onto its real range. */
	public double doubleValue() {
		return this.min + this.value * (this.max - this.min);
	}

	public int intValue() {
		return (int) Math.round(doubleValue());
	}

	@Override
	protected void updateMessage() {
		String shown = switch (this.style) {
			case TICKS -> Integer.toString(intValue());
			case PERCENT -> Math.round(doubleValue() * 100.0) + "%";
		};

		setMessage(Component.translatable("screen.grandcraft.config.entry", this.label, shown));
	}

	@Override
	protected void applyValue() {
		// Nothing to do: the screen reads slider values when the player saves.
	}
}

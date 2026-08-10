package zone.moddev.mc.orespawn.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;

/** Java 8 equivalent of the vanilla cycle button introduced after this target. */
final class CycleButton<T> extends Button {
	private final ITextComponent label;
	private final Function<T, ITextComponent> valueLabel;
	private final List<T> values;
	private final OnValueChange<T> callback;
	private int index;

	private CycleButton(int x, int y, int width, int height, ITextComponent label,
			Function<T, ITextComponent> valueLabel, List<T> values, T initialValue,
			OnValueChange<T> callback) {
		super(x, y, width, height, message(label, valueLabel, initialValue), button -> {
			CycleButton<T> cycle = (CycleButton<T>) button;
			cycle.advance();
		});
		this.label = Objects.requireNonNull(label, "label");
		this.valueLabel = Objects.requireNonNull(valueLabel, "valueLabel");
		this.values = new ArrayList<>(values);
		this.callback = Objects.requireNonNull(callback, "callback");
		this.index = Math.max(0, this.values.indexOf(initialValue));
		setMessage(message(label, valueLabel, getValue()));
	}

	static <T> Builder<T> builder(Function<T, ITextComponent> valueLabel) {
		return new Builder<>(valueLabel);
	}

	static Builder<Boolean> onOffBuilder(boolean initialValue) {
		return CycleButton.<Boolean>builder(value -> new TextComponentTranslation(
				value ? "options.on" : "options.off"))
				.withValues(Arrays.asList(Boolean.FALSE, Boolean.TRUE))
				.withInitialValue(initialValue);
	}

	T getValue() {
		return values.get(index);
	}

	private void advance() {
		index = (index + 1) % values.size();
		T value = getValue();
		setMessage(message(label, valueLabel, value));
		callback.onValueChange(this, value);
	}

	private static <T> String message(ITextComponent label,
			Function<T, ITextComponent> valueLabel, T value) {
		return label.getFormattedText() + ": " + valueLabel.apply(value).getFormattedText();
	}

	interface OnValueChange<T> {
		void onValueChange(CycleButton<T> button, T value);
	}

	static final class Builder<T> {
		private final Function<T, ITextComponent> valueLabel;
		private List<T> values;
		private T initialValue;

		private Builder(Function<T, ITextComponent> valueLabel) {
			this.valueLabel = Objects.requireNonNull(valueLabel, "valueLabel");
		}

		Builder<T> withValues(List<T> values) {
			if (values == null || values.isEmpty()) {
				throw new IllegalArgumentException("Cycle button values must not be empty");
			}
			this.values = new ArrayList<>(values);
			return this;
		}

		Builder<T> withInitialValue(T initialValue) {
			this.initialValue = initialValue;
			return this;
		}

		CycleButton<T> create(int x, int y, int width, int height, ITextComponent label,
				OnValueChange<T> callback) {
			if (values == null || values.isEmpty()) {
				throw new IllegalStateException("Cycle button values were not configured");
			}
			T selected = values.contains(initialValue) ? initialValue : values.get(0);
			return new CycleButton<>(x, y, width, height, label, valueLabel, values,
					selected, callback);
		}
	}
}

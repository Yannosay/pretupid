package net.pretupid.client.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.util.Locale;

public final class NumberSetting extends Setting<Double> {
	private final double min;
	private final double max;
	private final double step;
	private final String unit;

	public NumberSetting(String id, double defaultValue, double min, double max, double step, String unit) {
		super(id, defaultValue);
		if (min >= max || step <= 0) {
			throw new IllegalArgumentException("Invalid range for setting " + id);
		}
		this.min = min;
		this.max = max;
		this.step = step;
		this.unit = unit == null ? "" : unit;
	}

	public NumberSetting(String id, double defaultValue, double min, double max, double step) {
		this(id, defaultValue, min, max, step, "");
	}

	public double min() {
		return min;
	}

	public double max() {
		return max;
	}

	public double step() {
		return step;
	}

	public String unit() {
		return unit;
	}

	public int intValue() {
		return (int) Math.round(get());
	}

	public float floatValue() {
		return get().floatValue();
	}

	public double progress() {
		return (get() - min) / (max - min);
	}

	public void setProgress(double progress) {
		set(min + Math.clamp(progress, 0.0, 1.0) * (max - min));
	}

	public void nudge(int direction) {
		set(get() + step * direction);
	}

	public String format() {
		double value = get();
		String text;
		if (step >= 1.0) {
			text = Long.toString(Math.round(value));
		} else if (step >= 0.1) {
			text = String.format(Locale.ROOT, "%.1f", value);
		} else {
			text = String.format(Locale.ROOT, "%.2f", value);
		}
		return unit.isEmpty() ? text : text + unit;
	}

	@Override
	protected Double sanitize(Double candidate) {
		if (candidate.isNaN() || candidate.isInfinite()) {
			return defaultValue();
		}
		double clamped = Math.clamp(candidate, min, max);
		double snapped = min + Math.round((clamped - min) / step) * step;
		return Math.clamp(Math.round(snapped * 10000.0) / 10000.0, min, max);
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(get());
	}

	@Override
	public void fromJson(JsonElement element) {
		if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
			set(element.getAsDouble());
		}
	}
}

package net.pretupid.client.nametag;

import net.pretupid.client.util.Colors;

public enum NameAnimation {
	NONE,
	STATIC,
	GRADIENT,
	WAVE,
	PULSE,
	SHIMMER,
	RAINBOW,
	FLAME,
	AURORA;

	public boolean recolors() {
		return this != NONE;
	}

	public boolean usesPrimary() {
		return this == STATIC || this == GRADIENT || this == WAVE || this == PULSE || this == SHIMMER;
	}

	public boolean usesSecondary() {
		return this == GRADIENT || this == WAVE || this == PULSE || this == SHIMMER;
	}

	public boolean animates() {
		return this == WAVE || this == PULSE || this == SHIMMER || this == RAINBOW || this == FLAME || this == AURORA;
	}

	public int colorAt(int index, int length, float seconds, int primary, int secondary, float speed) {
		float position = length <= 1 ? 0f : index / (float) (length - 1);
		float time = seconds * speed;
		return switch (this) {
			case NONE, STATIC -> primary;
			case GRADIENT -> Colors.lerp(primary, secondary, position);
			case WAVE -> Colors.lerp(primary, secondary, 0.5f + 0.5f * (float) Math.sin(time * 3.0 - index * 0.55));
			case PULSE -> Colors.lerp(primary, secondary, 0.5f + 0.5f * (float) Math.sin(time * 3.5));
			case SHIMMER -> {
				float span = length + 6f;
				float head = (time * 9f) % (span * 1.6f) - 3f;
				float distance = Math.abs(index - head);
				float glow = Math.max(0f, 1f - distance / 2.5f);
				yield Colors.lerp(Colors.lerp(primary, secondary, position), 0xFFFFFFFF, glow * 0.85f);
			}
			case RAINBOW -> Colors.hsv(time * 0.25f + index * 0.055f, 0.6f, 1f);
			case FLAME -> {
				double flicker = Math.sin(time * 7.0 + index * 1.7) * 0.5 + Math.sin(time * 11.0 + index * 0.9) * 0.5;
				yield Colors.hsv(0.015f + 0.075f * (float) (0.5 + 0.5 * flicker), 0.85f, 1f);
			}
			case AURORA -> {
				float hue = 0.38f + 0.18f * (float) Math.sin(time * 1.4 + index * 0.35);
				yield Colors.hsv(hue, 0.55f, 1f);
			}
		};
	}
}

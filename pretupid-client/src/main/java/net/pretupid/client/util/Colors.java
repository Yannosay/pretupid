package net.pretupid.client.util;

import java.util.Locale;

public final class Colors {
	public static final int WHITE = 0xFFFFFFFF;
	public static final int BLACK = 0xFF000000;

	private Colors() {
	}

	public static int alpha(int argb) {
		return argb >>> 24;
	}

	public static int red(int argb) {
		return (argb >> 16) & 0xFF;
	}

	public static int green(int argb) {
		return (argb >> 8) & 0xFF;
	}

	public static int blue(int argb) {
		return argb & 0xFF;
	}

	public static int argb(int a, int r, int g, int b) {
		return (clamp(a) << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
	}

	public static int withAlpha(int argb, int alpha) {
		return (clamp(alpha) << 24) | (argb & 0x00FFFFFF);
	}

	public static int multiplyAlpha(int argb, float factor) {
		return withAlpha(argb, Math.round(alpha(argb) * Math.clamp(factor, 0f, 1f)));
	}

	public static int lerp(int from, int to, float t) {
		float k = Math.clamp(t, 0f, 1f);
		return argb(
			Math.round(alpha(from) + (alpha(to) - alpha(from)) * k),
			Math.round(red(from) + (red(to) - red(from)) * k),
			Math.round(green(from) + (green(to) - green(from)) * k),
			Math.round(blue(from) + (blue(to) - blue(from)) * k)
		);
	}

	public static int brighten(int argb, float amount) {
		return lerp(argb, withAlpha(WHITE, alpha(argb)), amount);
	}

	public static int darken(int argb, float amount) {
		return lerp(argb, withAlpha(BLACK, alpha(argb)), amount);
	}

	public static int hsv(float hue, float saturation, float value) {
		float h = (hue - (float) Math.floor(hue)) * 6f;
		int sector = Math.min((int) h, 5);
		float f = h - sector;
		float p = value * (1f - saturation);
		float q = value * (1f - f * saturation);
		float t = value * (1f - (1f - f) * saturation);
		float r;
		float g;
		float b;
		switch (sector) {
			case 0 -> {
				r = value;
				g = t;
				b = p;
			}
			case 1 -> {
				r = q;
				g = value;
				b = p;
			}
			case 2 -> {
				r = p;
				g = value;
				b = t;
			}
			case 3 -> {
				r = p;
				g = q;
				b = value;
			}
			case 4 -> {
				r = t;
				g = p;
				b = value;
			}
			default -> {
				r = value;
				g = p;
				b = q;
			}
		}
		return argb(255, Math.round(r * 255f), Math.round(g * 255f), Math.round(b * 255f));
	}

	public static float[] toHsv(int argb) {
		float r = red(argb) / 255f;
		float g = green(argb) / 255f;
		float b = blue(argb) / 255f;
		float max = Math.max(r, Math.max(g, b));
		float min = Math.min(r, Math.min(g, b));
		float delta = max - min;
		float hue;
		if (delta == 0f) {
			hue = 0f;
		} else if (max == r) {
			hue = ((g - b) / delta) % 6f;
		} else if (max == g) {
			hue = (b - r) / delta + 2f;
		} else {
			hue = (r - g) / delta + 4f;
		}
		hue /= 6f;
		if (hue < 0f) {
			hue += 1f;
		}
		return new float[] {hue, max == 0f ? 0f : delta / max, max};
	}

	public static int chroma(float phase) {
		long period = 4000L;
		float base = (System.currentTimeMillis() % period) / (float) period;
		return hsv(base + phase, 0.6f, 1f);
	}

	public static String toHex(int argb) {
		return String.format(Locale.ROOT, "#%08X", argb);
	}

	public static String toRgbHex(int argb) {
		return String.format(Locale.ROOT, "#%06X", argb & 0xFFFFFF);
	}

	public static int parseHex(String text, int fallback) {
		if (text == null) {
			return fallback;
		}
		String value = text.trim();
		if (value.startsWith("#")) {
			value = value.substring(1);
		}
		try {
			if (value.length() == 6) {
				return 0xFF000000 | Integer.parseUnsignedInt(value, 16);
			}
			if (value.length() == 8) {
				return (int) Long.parseLong(value, 16);
			}
		} catch (NumberFormatException ignored) {
			return fallback;
		}
		return fallback;
	}

	private static int clamp(int channel) {
		return Math.clamp(channel, 0, 255);
	}
}

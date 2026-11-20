package net.pretupid.client.screenie;

import java.awt.image.BufferedImage;

public record ScreenieLook(Filter filter, float brightness, float contrast, float saturation, float warmth, float vignette) {
	public static final ScreenieLook NATURAL = new ScreenieLook(Filter.NONE, 0f, 0f, 0f, 0f, 0f);

	public enum Filter {
		NONE,
		VIVID,
		MONO,
		NOIR,
		SEPIA,
		RETRO,
		COLD
	}

	public ScreenieLook withFilter(Filter value) {
		return new ScreenieLook(value, brightness, contrast, saturation, warmth, vignette);
	}

	public ScreenieLook withBrightness(float value) {
		return new ScreenieLook(filter, value, contrast, saturation, warmth, vignette);
	}

	public ScreenieLook withContrast(float value) {
		return new ScreenieLook(filter, brightness, value, saturation, warmth, vignette);
	}

	public ScreenieLook withSaturation(float value) {
		return new ScreenieLook(filter, brightness, contrast, value, warmth, vignette);
	}

	public ScreenieLook withWarmth(float value) {
		return new ScreenieLook(filter, brightness, contrast, saturation, value, vignette);
	}

	public ScreenieLook withVignette(float value) {
		return new ScreenieLook(filter, brightness, contrast, saturation, warmth, value);
	}

	public boolean isNatural() {
		return equals(NATURAL);
	}

	public void apply(BufferedImage image) {
		if (isNatural()) {
			return;
		}
		int width = image.getWidth();
		int height = image.getHeight();
		int[] row = new int[width];
		float cx = width / 2f;
		float cy = height / 2f;
		float maxDistance = (float) Math.sqrt(cx * cx + cy * cy);
		float contrastFactor = 1f + contrast;
		float saturationFactor = 1f + saturation;
		for (int y = 0; y < height; y++) {
			image.getRGB(0, y, width, 1, row, 0, width);
			for (int x = 0; x < width; x++) {
				int argb = row[x];
				float r = ((argb >> 16) & 0xFF) / 255f;
				float g = ((argb >> 8) & 0xFF) / 255f;
				float b = (argb & 0xFF) / 255f;
				float[] filtered = filter(r, g, b);
				r = filtered[0];
				g = filtered[1];
				b = filtered[2];
				r += brightness * 0.5f;
				g += brightness * 0.5f;
				b += brightness * 0.5f;
				r = (r - 0.5f) * contrastFactor + 0.5f;
				g = (g - 0.5f) * contrastFactor + 0.5f;
				b = (b - 0.5f) * contrastFactor + 0.5f;
				float luma = 0.2126f * r + 0.7152f * g + 0.0722f * b;
				r = luma + (r - luma) * saturationFactor;
				g = luma + (g - luma) * saturationFactor;
				b = luma + (b - luma) * saturationFactor;
				r += warmth * 0.12f;
				b -= warmth * 0.12f;
				if (vignette > 0f) {
					float dx = x - cx;
					float dy = y - cy;
					float distance = (float) Math.sqrt(dx * dx + dy * dy) / maxDistance;
					float shade = 1f - vignette * smooth(Math.clamp((distance - 0.35f) / 0.75f, 0f, 1f));
					r *= shade;
					g *= shade;
					b *= shade;
				}
				row[x] = (argb & 0xFF000000) | (channel(r) << 16) | (channel(g) << 8) | channel(b);
			}
			image.setRGB(0, y, width, 1, row, 0, width);
		}
	}

	private float[] filter(float r, float g, float b) {
		float luma = 0.2126f * r + 0.7152f * g + 0.0722f * b;
		return switch (filter) {
			case NONE -> new float[] {r, g, b};
			case VIVID -> {
				float boost = 1.35f;
				yield new float[] {luma + (r - luma) * boost, luma + (g - luma) * boost, luma + (b - luma) * boost};
			}
			case MONO -> new float[] {luma, luma, luma};
			case NOIR -> {
				float value = Math.clamp((luma - 0.5f) * 1.6f + 0.48f, 0f, 1f);
				yield new float[] {value, value, value};
			}
			case SEPIA -> new float[] {
				Math.min(1f, r * 0.393f + g * 0.769f + b * 0.189f),
				Math.min(1f, r * 0.349f + g * 0.686f + b * 0.168f),
				Math.min(1f, r * 0.272f + g * 0.534f + b * 0.131f)
			};
			case RETRO -> new float[] {r * 0.9f + 0.08f, g * 0.85f + 0.06f, b * 0.7f + 0.1f};
			case COLD -> new float[] {r * 0.88f, g * 0.97f, Math.min(1f, b * 1.08f + 0.04f)};
		};
	}

	private static float smooth(float t) {
		return t * t * (3f - 2f * t);
	}

	private static int channel(float value) {
		return Math.round(Math.clamp(value, 0f, 1f) * 255f);
	}
}

package net.pretupid.client.screenie;

public record TextLayer(String text, float x, float y, float size, int color, Style style, float angle) {
	public static final int MAX_LENGTH = 120;

	public TextLayer(String text, float x, float y, float size, int color, Style style) {
		this(text, x, y, size, color, style, 0f);
	}

	public enum Style {
		MEME,
		CLEAN,
		LABEL
	}

	public TextLayer withText(String value) {
		String clean = value.length() > MAX_LENGTH ? value.substring(0, MAX_LENGTH) : value;
		return new TextLayer(clean, x, y, size, color, style, angle);
	}

	public TextLayer withPosition(float newX, float newY) {
		return new TextLayer(text, Math.clamp(newX, 0f, 1f), Math.clamp(newY, 0f, 1f), size, color, style, angle);
	}

	public TextLayer withSize(float value) {
		return new TextLayer(text, x, y, Math.clamp(value, 0.03f, 0.25f), color, style, angle);
	}

	public TextLayer withColor(int value) {
		return new TextLayer(text, x, y, size, value, style, angle);
	}

	public TextLayer withStyle(Style value) {
		return new TextLayer(text, x, y, size, color, value, angle);
	}

	public TextLayer withAngle(float value) {
		return new TextLayer(text, x, y, size, color, style, Math.clamp(value, -180f, 180f));
	}
}

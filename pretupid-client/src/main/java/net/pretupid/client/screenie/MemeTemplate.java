package net.pretupid.client.screenie;

import java.util.List;

public enum MemeTemplate {
	NONE(0f, 0f, 0f, 0),
	CLASSIC(0f, 0f, 0f, 0),
	CAPTION(0.24f, 0f, 0f, 0),
	POSTER(0.07f, 0.34f, 0.09f, 0xFF000000);

	private final float top;
	private final float bottom;
	private final float side;
	private final int background;

	MemeTemplate(float top, float bottom, float side, int background) {
		this.top = top;
		this.bottom = bottom;
		this.side = side;
		this.background = background;
	}

	public float top() {
		return top;
	}

	public float bottom() {
		return bottom;
	}

	public float side() {
		return side;
	}

	public int background() {
		return this == CAPTION ? 0xFFFFFFFF : background;
	}

	public List<String> slots() {
		return switch (this) {
			case NONE -> List.of();
			case CLASSIC -> List.of("top", "bottom");
			case CAPTION -> List.of("caption");
			case POSTER -> List.of("title", "subtitle");
		};
	}

	public List<TextLayer> layers(List<String> texts) {
		String first = texts.isEmpty() ? "" : texts.get(0);
		String second = texts.size() < 2 ? "" : texts.get(1);
		return switch (this) {
			case NONE -> List.of();
			case CLASSIC -> List.of(
				new TextLayer(first, 0.5f, 0.09f, 0.1f, 0xFFFFFFFF, TextLayer.Style.MEME),
				new TextLayer(second, 0.5f, 0.91f, 0.1f, 0xFFFFFFFF, TextLayer.Style.MEME));
			case CAPTION -> List.of(new TextLayer(first, 0.5f, top / (1f + top) / 2f, 0.07f, 0xFF111111, TextLayer.Style.LABEL));
			case POSTER -> List.of(
				new TextLayer(first.toUpperCase(java.util.Locale.ROOT), 0.5f, 0.8f, 0.1f, 0xFFFFFFFF, TextLayer.Style.LABEL),
				new TextLayer(second, 0.5f, 0.9f, 0.045f, 0xFFDDDDDD, TextLayer.Style.LABEL));
		};
	}
}

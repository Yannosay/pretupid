package net.pretupid.client.screenie;

import java.util.List;

public enum MemeTemplate {
	NONE(0f, 0f, 0f, 0),
	CLASSIC(0f, 0f, 0f, 0),
	CAPTION(0.24f, 0f, 0f, 0),
	POSTER(0.07f, 0.34f, 0.09f, 0xFF000000);

	public record Phrase(String top, String bottom) {
	}

	private static final List<Phrase> CLASSIC_PHRASES = List.of(
		new Phrase("When the creeper", "is right behind you"),
		new Phrase("One more block", "they said"),
		new Phrase("Me after five hours", "of strip mining"),
		new Phrase("Nobody:", "Me at 3 AM:"),
		new Phrase("POV:", "You finally found diamonds"),
		new Phrase("It's not a bug", "It's a feature"),
		new Phrase("Trust me", "I'm a redstone engineer"),
		new Phrase("When the server lags", "mid jump")
	);
	private static final List<Phrase> CAPTION_PHRASES = List.of(
		new Phrase("When you told your friends the base was safe", ""),
		new Phrase("Me pretending I know what this redstone does", ""),
		new Phrase("The building inspector has arrived", ""),
		new Phrase("When someone says 'just one more night'", "")
	);
	private static final List<Phrase> POSTER_PHRASES = List.of(
		new Phrase("Teamwork", "Because nobody builds a castle alone."),
		new Phrase("Patience", "The diamonds are always one more tunnel away."),
		new Phrase("Courage", "Walking into the Nether with three hearts."),
		new Phrase("Ambition", "It started as a dirt hut.")
	);

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

	public List<Phrase> phrases() {
		return switch (this) {
			case NONE, CLASSIC -> CLASSIC_PHRASES;
			case CAPTION -> CAPTION_PHRASES;
			case POSTER -> POSTER_PHRASES;
		};
	}

	public List<TextLayer> layers(Phrase phrase) {
		return switch (this) {
			case NONE, CLASSIC -> List.of(
				new TextLayer(phrase.top(), 0.5f, 0.09f, 0.1f, 0xFFFFFFFF, TextLayer.Style.MEME),
				new TextLayer(phrase.bottom(), 0.5f, 0.91f, 0.1f, 0xFFFFFFFF, TextLayer.Style.MEME));
			case CAPTION -> List.of(new TextLayer(phrase.top(), 0.5f, top / (1f + top) / 2f, 0.07f, 0xFF111111, TextLayer.Style.LABEL));
			case POSTER -> List.of(
				new TextLayer(phrase.top().toUpperCase(java.util.Locale.ROOT), 0.5f, 0.8f, 0.1f, 0xFFFFFFFF, TextLayer.Style.LABEL),
				new TextLayer(phrase.bottom(), 0.5f, 0.9f, 0.045f, 0xFFDDDDDD, TextLayer.Style.LABEL));
		};
	}
}

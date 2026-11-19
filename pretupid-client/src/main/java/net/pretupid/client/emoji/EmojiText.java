package net.pretupid.client.emoji;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

public final class EmojiText {
	public static final FontDescription FONT = new FontDescription.Resource(Identifier.fromNamespaceAndPath("pretupid", "emoji"));
	public static final Pattern SHORTCODE = Pattern.compile(":([a-z0-9_+\\-]{1,40}):");
	private static final int MAX_LENGTH = 4096;

	private EmojiText() {
	}

	public record Segment(String text, Style style) {
	}

	public record Insert(int index, String text) {
	}

	public static List<Segment> segments(Component component) {
		List<Segment> segments = new ArrayList<>();
		component.visit((style, text) -> {
			if (!text.isEmpty()) {
				segments.add(new Segment(text, style));
			}
			return Optional.empty();
		}, Style.EMPTY);
		return segments;
	}

	public static String plain(List<Segment> segments) {
		StringBuilder builder = new StringBuilder();
		for (Segment segment : segments) {
			builder.append(segment.text());
		}
		return builder.toString();
	}

	public static Style glyphStyle(Style base) {
		return base.withFont(FONT).withColor(0xFFFFFF).withShadowColor(0).withBold(false).withItalic(false).withObfuscated(false);
	}

	public static Component glyph(EmojiIndex.Emoji emoji) {
		return Component.literal(emoji.glyph()).withStyle(glyphStyle(Style.EMPTY));
	}

	public static Component render(Component input) {
		if (input == null) {
			return null;
		}
		List<Segment> segments = segments(input);
		if (plain(segments).length() > MAX_LENGTH) {
			return input;
		}
		return build(segments, input);
	}

	public static Component insertAndRender(Component input, int base, List<Insert> inserts) {
		List<Segment> segments = segments(input);
		for (int i = inserts.size() - 1; i >= 0; i--) {
			Insert insert = inserts.get(i);
			segments = insertAt(segments, base + insert.index(), insert.text());
		}
		Component built = build(segments, null);
		return built == null ? input : built;
	}

	private static List<Segment> insertAt(List<Segment> segments, int offset, String text) {
		List<Segment> result = new ArrayList<>(segments.size() + 2);
		int position = 0;
		boolean inserted = false;
		Style previous = Style.EMPTY;
		for (Segment segment : segments) {
			int end = position + segment.text().length();
			if (!inserted && offset >= position && offset < end) {
				int split = offset - position;
				if (split > 0) {
					result.add(new Segment(segment.text().substring(0, split), segment.style()));
				}
				result.add(new Segment(text, split > 0 ? segment.style() : previous));
				result.add(new Segment(segment.text().substring(split), segment.style()));
				inserted = true;
			} else {
				result.add(segment);
			}
			previous = segment.style();
			position = end;
		}
		if (!inserted) {
			result.add(new Segment(text, previous));
		}
		return result;
	}

	private static Component build(List<Segment> segments, Component unchanged) {
		EmojiIndex index = EmojiIndex.get();
		MutableComponent output = Component.empty();
		boolean changed = false;
		for (Segment segment : segments) {
			String text = segment.text();
			StringBuilder pending = new StringBuilder();
			int i = 0;
			while (i < text.length()) {
				char c = text.charAt(i);
				if (c == ':') {
					Matcher matcher = SHORTCODE.matcher(text).region(i, text.length());
					if (matcher.lookingAt()) {
						Optional<EmojiIndex.Emoji> emoji = index.byName(matcher.group(1));
						if (emoji.isPresent()) {
							flush(output, pending, segment.style());
							output.append(Component.literal(emoji.get().glyph()).withStyle(glyphStyle(segment.style())));
							changed = true;
							i = matcher.end();
							continue;
						}
					}
				} else if (c >= 0x80 || i + 1 < text.length()) {
					EmojiIndex.Match match = index.unicodeAt(text, i);
					if (match != null) {
						flush(output, pending, segment.style());
						output.append(Component.literal(match.emoji().glyph()).withStyle(glyphStyle(segment.style())));
						changed = true;
						i += match.length();
						continue;
					}
				}
				pending.append(c);
				i++;
			}
			flush(output, pending, segment.style());
		}
		if (!changed && unchanged != null) {
			return unchanged;
		}
		return output;
	}

	private static void flush(MutableComponent output, StringBuilder pending, Style style) {
		if (pending.length() > 0) {
			output.append(Component.literal(pending.toString()).withStyle(style));
			pending.setLength(0);
		}
	}
}

package net.pretupid.client.news;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

public final class MarkdownText {
	private static final Pattern INLINE = Pattern.compile("`([^`]+)`|\\*\\*([^*]+)\\*\\*|\\*([^*]+)\\*|~~([^~]+)~~|\\[([^\\]]+)]\\(([^)\\s]+)\\)");
	private static final Pattern HEADING = Pattern.compile("^(#{1,3})\\s+(.+)$");
	private static final Pattern BULLET = Pattern.compile("^[-*]\\s+(.+)$");
	private static final Pattern NUMBERED = Pattern.compile("^(\\d+)[.)]\\s+(.+)$");

	private static final Pattern BUTTON = Pattern.compile("^\\[\\[([^\\]]{1,48})]]\\(([^)\\s]+)\\)$");
	private static final Pattern IMAGE = Pattern.compile("^!\\[([^\\]]*)]\\(([^)\\s]+)\\)$");
	private static final float[] HEADING_SCALES = {1.5f, 1.25f, 1.1f};

	public enum Kind {
		TEXT,
		RULE,
		BUTTON,
		IMAGE
	}

	public record Line(Kind kind, FormattedCharSequence text, int indent, int color, int spacingBefore, float scale, String label, String url) {
		public boolean rule() {
			return kind == Kind.RULE;
		}

		static Line text(FormattedCharSequence text, int indent, int color, int spacingBefore, float scale) {
			return new Line(Kind.TEXT, text, indent, color, spacingBefore, scale, null, null);
		}
	}

	private MarkdownText() {
	}

	public static List<Line> layout(String source, Font font, int width, int textColor, int accentColor, int mutedColor) {
		List<Line> lines = new ArrayList<>();
		StringBuilder paragraph = new StringBuilder();
		boolean inCode = false;
		int pendingSpacing = 0;
		for (String raw : source.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
			String line = raw.strip();
			if (inCode) {
				if (line.startsWith("```")) {
					inCode = false;
					pendingSpacing = 4;
				} else {
					addWrapped(lines, font, Component.literal(raw.replace("\t", "  ")).withStyle(ChatFormatting.GRAY), width - 8, 8, mutedColor, takeSpacing(lines, pendingSpacing));
					pendingSpacing = 0;
				}
				continue;
			}
			if (line.startsWith("```")) {
				pendingSpacing = flush(lines, paragraph, font, width, textColor, pendingSpacing);
				inCode = true;
				continue;
			}
			if (line.isEmpty()) {
				pendingSpacing = Math.max(flush(lines, paragraph, font, width, textColor, pendingSpacing), 5);
				continue;
			}
			Matcher button = BUTTON.matcher(line);
			if (button.matches()) {
				pendingSpacing = flush(lines, paragraph, font, width, textColor, pendingSpacing);
				String url = safeUrl(button.group(2));
				if (url != null) {
					lines.add(new Line(Kind.BUTTON, FormattedCharSequence.EMPTY, 0, textColor, takeSpacing(lines, Math.max(pendingSpacing, 4)), 1f, button.group(1).strip(), url));
					pendingSpacing = 5;
				}
				continue;
			}
			Matcher image = IMAGE.matcher(line);
			if (image.matches()) {
				pendingSpacing = flush(lines, paragraph, font, width, textColor, pendingSpacing);
				String url = safeUrl(image.group(2));
				if (url != null && url.startsWith("https://")) {
					lines.add(new Line(Kind.IMAGE, FormattedCharSequence.EMPTY, 0, mutedColor, takeSpacing(lines, Math.max(pendingSpacing, 4)), 1f, image.group(1).strip(), url));
					pendingSpacing = 5;
				}
				continue;
			}
			Matcher heading = HEADING.matcher(line);
			if (heading.matches()) {
				pendingSpacing = flush(lines, paragraph, font, width, textColor, pendingSpacing);
				int level = heading.group(1).length();
				float scale = HEADING_SCALES[level - 1];
				MutableComponent text = inline(heading.group(2)).withStyle(style -> style.withBold(true));
				int color = level == 1 ? accentColor : textColor;
				int spacing = takeSpacing(lines, Math.max(pendingSpacing, level == 1 ? 8 : 6));
				boolean first = true;
				for (FormattedCharSequence part : font.split(text, Math.max(20, Math.round(width / scale)))) {
					lines.add(Line.text(part, 0, color, first ? spacing : 0, scale));
					first = false;
				}
				pendingSpacing = 3;
				continue;
			}
			if (line.matches("^(-{3,}|\\*{3,})$")) {
				pendingSpacing = flush(lines, paragraph, font, width, textColor, pendingSpacing);
				lines.add(new Line(Kind.RULE, FormattedCharSequence.EMPTY, 0, mutedColor, 4, 1f, null, null));
				pendingSpacing = 4;
				continue;
			}
			Matcher bullet = BULLET.matcher(line);
			Matcher numbered = NUMBERED.matcher(line);
			if (bullet.matches() || numbered.matches()) {
				pendingSpacing = flush(lines, paragraph, font, width, textColor, pendingSpacing);
				String marker = bullet.matches() ? "• " : numbered.group(1) + ". ";
				String body = bullet.matches() ? bullet.group(1) : numbered.group(2);
				MutableComponent text = Component.literal(marker).withStyle(style -> style.withColor(accentColor & 0xFFFFFF)).append(inline(body));
				addWrapped(lines, font, text, width - 6, 6, textColor, takeSpacing(lines, pendingSpacing));
				pendingSpacing = 1;
				continue;
			}
			if (line.startsWith(">")) {
				pendingSpacing = flush(lines, paragraph, font, width, textColor, pendingSpacing);
				MutableComponent text = inline(line.replaceFirst("^>\\s?", "")).withStyle(style -> style.withItalic(true));
				addWrapped(lines, font, text, width - 10, 10, mutedColor, takeSpacing(lines, pendingSpacing));
				pendingSpacing = 1;
				continue;
			}
			if (!paragraph.isEmpty()) {
				paragraph.append(' ');
			}
			paragraph.append(line);
		}
		flush(lines, paragraph, font, width, textColor, pendingSpacing);
		return lines;
	}

	private static int takeSpacing(List<Line> lines, int spacing) {
		return lines.isEmpty() ? 0 : spacing;
	}

	private static int flush(List<Line> lines, StringBuilder paragraph, Font font, int width, int color, int spacing) {
		if (paragraph.isEmpty()) {
			return spacing;
		}
		addWrapped(lines, font, inline(paragraph.toString()), width, 0, color, takeSpacing(lines, spacing));
		paragraph.setLength(0);
		return 5;
	}

	private static void addWrapped(List<Line> lines, Font font, Component text, int width, int indent, int color, int spacing) {
		boolean first = true;
		for (FormattedCharSequence part : font.split(text, Math.max(20, width))) {
			lines.add(Line.text(part, indent, color, first ? spacing : 0, 1f));
			first = false;
		}
	}

	private static MutableComponent inline(String text) {
		MutableComponent result = Component.empty();
		Matcher matcher = INLINE.matcher(text);
		int last = 0;
		while (matcher.find()) {
			if (matcher.start() > last) {
				result.append(Component.literal(text.substring(last, matcher.start())));
			}
			if (matcher.group(1) != null) {
				result.append(Component.literal(matcher.group(1)).withStyle(ChatFormatting.GRAY));
			} else if (matcher.group(2) != null) {
				result.append(Component.literal(matcher.group(2)).withStyle(style -> style.withBold(true)));
			} else if (matcher.group(3) != null) {
				result.append(Component.literal(matcher.group(3)).withStyle(style -> style.withItalic(true)));
			} else if (matcher.group(4) != null) {
				result.append(Component.literal(matcher.group(4)).withStyle(style -> style.withStrikethrough(true)));
			} else {
				String url = safeUrl(matcher.group(6));
				MutableComponent label = Component.literal(matcher.group(5));
				result.append(url == null ? label : label.withStyle(Style.EMPTY.withColor(0x60A5FA).withUnderlined(true).withInsertion(url)));
			}
			last = matcher.end();
		}
		if (last < text.length()) {
			result.append(Component.literal(text.substring(last)));
		}
		return result;
	}

	public static String safeUrl(String raw) {
		try {
			URI uri = URI.create(raw);
			String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
			return ("https".equals(scheme) || "http".equals(scheme)) && uri.getHost() != null ? uri.toString() : null;
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}
}

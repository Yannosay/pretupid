package net.pretupid.client.nametag;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.pretupid.client.PretupidClient;

public final class NametagFormatter {
	public static final String BADGE_GLYPH = "";
	public static final FontDescription BADGE_FONT = new FontDescription.Resource(Identifier.fromNamespaceAndPath(PretupidClient.MOD_ID, "badge"));
	private NametagFormatter() {
	}

	private static final long EPOCH = System.nanoTime();
	private static final long PERIOD_NANOS = 3_600_000_000_000L;

	public static float seconds() {
		return ((System.nanoTime() - EPOCH) % PERIOD_NANOS) / 1_000_000_000f;
	}

	public static MutableComponent badge() {
		return Component.literal(BADGE_GLYPH).withStyle(Style.EMPTY.withFont(BADGE_FONT).withColor(0xFFFFFF).withBold(false).withItalic(false));
	}

	public static Component format(Component original, String name, NametagStyle style, boolean includeBadge, boolean decorate) {
		List<Segment> segments = new ArrayList<>();
		original.visit((segmentStyle, text) -> {
			if (!text.isEmpty()) {
				segments.add(new Segment(segmentStyle, text));
			}
			return Optional.empty();
		}, Style.EMPTY);
		StringBuilder plainBuilder = new StringBuilder();
		for (Segment segment : segments) {
			plainBuilder.append(segment.text());
		}
		String plain = plainBuilder.toString();
		boolean applyStyle = decorate && style.decoratesName() && name != null && !name.isEmpty();
		int start = applyStyle ? plain.indexOf(name) : -1;
		int end = start < 0 ? -1 : start + name.length();
		MutableComponent result = Component.empty();
		if (includeBadge && style.badge()) {
			result.append(badge());
			result.append(Component.literal(" ").withStyle(Style.EMPTY.withBold(false)));
		}
		float seconds = seconds();
		int offset = 0;
		for (Segment segment : segments) {
			String text = segment.text();
			int segmentStart = offset;
			int segmentEnd = offset + text.length();
			offset = segmentEnd;
			if (start < 0 || segmentEnd <= start || segmentStart >= end) {
				result.append(Component.literal(text).withStyle(segment.style()));
				continue;
			}
			int localStart = Math.max(0, start - segmentStart);
			int localEnd = Math.min(text.length(), end - segmentStart);
			if (localStart > 0) {
				result.append(Component.literal(text.substring(0, localStart)).withStyle(segment.style()));
			}
			for (int i = localStart; i < localEnd; ) {
				int codePoint = text.codePointAt(i);
				int width = Character.charCount(codePoint);
				int nameIndex = segmentStart + i - start;
				Style charStyle = segment.style().withBold(style.bold() || segment.style().isBold());
				if (style.animation().recolors()) {
					int color = style.animation().colorAt(nameIndex, name.length(), seconds, style.primary(), style.secondary(), style.speed());
					charStyle = charStyle.withColor(color & 0xFFFFFF);
				}
				if (style.effect().moves()) {
					charStyle = charStyle.withInsertion(style.effect().marker(nameIndex, style.speed()));
				}
				result.append(Component.literal(new String(Character.toChars(codePoint))).withStyle(charStyle));
				i += width;
			}
			if (localEnd < text.length()) {
				result.append(Component.literal(text.substring(localEnd)).withStyle(segment.style()));
			}
		}
		return result;
	}

	private record Segment(Style style, String text) {
	}
}

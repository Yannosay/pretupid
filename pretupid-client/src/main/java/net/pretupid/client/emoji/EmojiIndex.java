package net.pretupid.client.emoji;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.pretupid.client.PretupidClient;

public final class EmojiIndex {
	private static final String RESOURCE = "/assets/pretupid/emoji/emoji.json";
	private static EmojiIndex instance;

	public record Emoji(int codepoint, List<String> names, String unicode, String category) {
		public String name() {
			return names.getFirst();
		}

		public String glyph() {
			return new String(Character.toChars(codepoint));
		}

		public String shortcode() {
			return ":" + name() + ":";
		}
	}

	public record Match(Emoji emoji, int length) {
	}

	private final List<Emoji> all;
	private final Map<String, Emoji> byName;
	private final Map<Integer, Emoji> byCodepoint;
	private final Map<String, Emoji> byUnicode;
	private final Map<String, List<Emoji>> byCategory;
	private final int longestUnicode;

	private EmojiIndex(List<Emoji> all, Map<String, Emoji> byName, Map<Integer, Emoji> byCodepoint, Map<String, Emoji> byUnicode,
		Map<String, List<Emoji>> byCategory, int longestUnicode) {
		this.all = all;
		this.byName = byName;
		this.byCodepoint = byCodepoint;
		this.byUnicode = byUnicode;
		this.byCategory = byCategory;
		this.longestUnicode = longestUnicode;
	}

	public static synchronized EmojiIndex get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	public List<Emoji> all() {
		return all;
	}

	public Map<String, List<Emoji>> categories() {
		return byCategory;
	}

	public Optional<Emoji> byName(String name) {
		return Optional.ofNullable(byName.get(name.toLowerCase(Locale.ROOT)));
	}

	public boolean isGlyph(int codepoint) {
		return byCodepoint.containsKey(codepoint);
	}

	public Optional<Emoji> byGlyph(int codepoint) {
		return Optional.ofNullable(byCodepoint.get(codepoint));
	}

	public List<Emoji> search(String query, int limit) {
		String needle = query.toLowerCase(Locale.ROOT);
		List<Emoji> starts = new ArrayList<>();
		List<Emoji> contains = new ArrayList<>();
		for (Emoji emoji : all) {
			boolean started = false;
			boolean found = false;
			for (String name : emoji.names()) {
				if (name.startsWith(needle)) {
					started = true;
				} else if (name.contains(needle)) {
					found = true;
				}
			}
			if (started) {
				starts.add(emoji);
			} else if (found) {
				contains.add(emoji);
			}
			if (starts.size() >= limit) {
				break;
			}
		}
		starts.sort((a, b) -> Integer.compare(shortestMatch(a, needle), shortestMatch(b, needle)));
		List<Emoji> result = new ArrayList<>(starts);
		for (Emoji emoji : contains) {
			if (result.size() >= limit) {
				break;
			}
			result.add(emoji);
		}
		return result.size() > limit ? result.subList(0, limit) : result;
	}

	private static int shortestMatch(Emoji emoji, String needle) {
		int best = Integer.MAX_VALUE;
		for (String name : emoji.names()) {
			if (name.startsWith(needle)) {
				best = Math.min(best, name.length());
			}
		}
		return best;
	}

	public Match unicodeAt(String text, int index) {
		char first = text.charAt(index);
		if (first < 0x80) {
			if (index + 1 >= text.length()) {
				return null;
			}
			char next = text.charAt(index + 1);
			if (next != 0xFE0F && next != 0x20E3) {
				return null;
			}
		} else if (first < 0x2000) {
			if (first != 0xA9 && first != 0xAE) {
				return null;
			}
		}
		int max = Math.min(longestUnicode, text.length() - index);
		for (int length = max; length >= 1; length--) {
			Emoji emoji = byUnicode.get(text.substring(index, index + length));
			if (emoji != null) {
				int end = index + length;
				boolean textStyleSymbol = length == 1 && first < 0x2600 && (end >= text.length() || text.charAt(end) != 0xFE0F);
				if (textStyleSymbol) {
					return null;
				}
				if (end < text.length() && text.charAt(end) == 0xFE0F) {
					length++;
				}
				return new Match(emoji, length);
			}
		}
		return null;
	}

	private static EmojiIndex load() {
		List<Emoji> all = new ArrayList<>();
		Map<String, Emoji> byName = new HashMap<>();
		Map<Integer, Emoji> byCodepoint = new HashMap<>();
		Map<String, Emoji> byUnicode = new HashMap<>();
		Map<String, List<Emoji>> byCategory = new LinkedHashMap<>();
		int longest = 1;
		try (InputStream stream = EmojiIndex.class.getResourceAsStream(RESOURCE)) {
			if (stream != null) {
				JsonElement parsed = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
				for (JsonElement element : parsed.getAsJsonArray()) {
					JsonObject object = element.getAsJsonObject();
					List<String> names = new ArrayList<>();
					JsonArray array = object.getAsJsonArray("n");
					for (JsonElement name : array) {
						names.add(name.getAsString().toLowerCase(Locale.ROOT));
					}
					if (names.isEmpty()) {
						continue;
					}
					Emoji emoji = new Emoji(object.get("c").getAsInt(), List.copyOf(names), object.get("u").getAsString(), object.get("g").getAsString());
					all.add(emoji);
					byCodepoint.put(emoji.codepoint(), emoji);
					for (String name : names) {
						byName.putIfAbsent(name, emoji);
					}
					byUnicode.putIfAbsent(emoji.unicode(), emoji);
					longest = Math.max(longest, emoji.unicode().length());
					JsonElement plain = object.get("q");
					if (plain != null && !plain.isJsonNull()) {
						byUnicode.putIfAbsent(plain.getAsString(), emoji);
						longest = Math.max(longest, plain.getAsString().length());
					}
					String stripped = emoji.unicode().replace("️", "");
					if (!stripped.isEmpty() && stripped.codePointCount(0, stripped.length()) > 0) {
						byUnicode.putIfAbsent(stripped, emoji);
					}
					byCategory.computeIfAbsent(emoji.category(), key -> new ArrayList<>()).add(emoji);
				}
			}
		} catch (IOException | RuntimeException exception) {
			PretupidClient.LOGGER.warn("Could not read the bundled emoji list", exception);
		}
		Map<String, List<Emoji>> categories = new LinkedHashMap<>();
		byCategory.forEach((key, value) -> categories.put(key, Collections.unmodifiableList(value)));
		return new EmojiIndex(Collections.unmodifiableList(all), byName, byCodepoint, byUnicode, Collections.unmodifiableMap(categories), longest);
	}
}

package net.pretupid.client.emoji;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

public final class ChatEmojis {
	public static final int MAX_INSERTS = 32;
	private static final Pattern INSERT_TEXT = Pattern.compile("(?: |:[a-z0-9_+\\-]{1,40}:){1,40}");
	private static final long OVERLAY_LIFETIME_MS = 20_000L;
	private static final int MAX_PENDING = 64;
	private static final int MAX_RECENT = 50;

	public record Overlay(UUID sender, String hash, int length, List<EmojiText.Insert> inserts, long at) {
	}

	public record Outgoing(String message, Overlay overlay) {
	}

	public interface ChatAccess {
		int size();

		Component content(int index);

		void replace(int index, Component content);

		void refresh();
	}

	private record Recent(Component content, long at) {
	}

	private final Deque<Overlay> pending = new ArrayDeque<>();
	private final Deque<Recent> recent = new ArrayDeque<>();
	private Consumer<Overlay> publisher = overlay -> { };

	public void setPublisher(Consumer<Overlay> publisher) {
		this.publisher = publisher;
	}

	public static String shortcodesToUnicode(String message) {
		EmojiIndex index = EmojiIndex.get();
		Matcher matcher = EmojiText.SHORTCODE.matcher(message);
		StringBuilder builder = new StringBuilder();
		while (matcher.find()) {
			Optional<EmojiIndex.Emoji> emoji = index.byName(matcher.group(1));
			matcher.appendReplacement(builder, Matcher.quoteReplacement(emoji.map(EmojiIndex.Emoji::unicode).orElse(matcher.group())));
		}
		matcher.appendTail(builder);
		return builder.toString();
	}

	public Outgoing prepare(String message, UUID self) {
		EmojiIndex index = EmojiIndex.get();
		Matcher matcher = EmojiText.SHORTCODE.matcher(message);
		List<int[]> spans = new ArrayList<>();
		while (matcher.find()) {
			if (index.byName(matcher.group(1)).isEmpty()) {
				continue;
			}
			int start = matcher.start();
			int end = matcher.end();
			if (end < message.length() && message.charAt(end) == ' ') {
				end++;
			} else if (start > 0 && message.charAt(start - 1) == ' ' && (spans.isEmpty() || spans.getLast()[1] < start)) {
				start--;
			}
			if (!spans.isEmpty() && spans.getLast()[1] >= start) {
				spans.getLast()[1] = Math.max(spans.getLast()[1], end);
			} else {
				spans.add(new int[] {start, end});
			}
		}
		if (spans.isEmpty()) {
			return new Outgoing(message, null);
		}
		StringBuilder stripped = new StringBuilder();
		List<EmojiText.Insert> inserts = new ArrayList<>();
		int cursor = 0;
		for (int[] span : spans) {
			stripped.append(message, cursor, span[0]);
			inserts.add(new EmojiText.Insert(stripped.length(), message.substring(span[0], span[1])));
			cursor = span[1];
		}
		stripped.append(message, cursor, message.length());
		String text = stripped.toString();
		boolean usable = !text.isBlank() && !text.startsWith("/") && text.equals(text.strip()) && !text.contains("  ") && inserts.size() <= MAX_INSERTS;
		if (!usable) {
			return new Outgoing(shortcodesToUnicode(message), null);
		}
		Overlay overlay = new Overlay(self, hash(text), text.length(), List.copyOf(inserts), System.currentTimeMillis());
		return new Outgoing(text, overlay);
	}

	public void sent(Overlay overlay) {
		remember(overlay);
		publisher.accept(overlay);
	}

	public void received(UUID sender, JsonObject frame) {
		String hash = frame.has("h") ? frame.get("h").getAsString() : null;
		int length = frame.has("l") ? frame.get("l").getAsInt() : -1;
		JsonElement rawInserts = frame.get("x");
		if (hash == null || !hash.matches("[0-9a-f]{16}") || length <= 0 || length > 256 || rawInserts == null || !rawInserts.isJsonArray()) {
			return;
		}
		JsonArray array = rawInserts.getAsJsonArray();
		if (array.isEmpty() || array.size() > MAX_INSERTS) {
			return;
		}
		List<EmojiText.Insert> inserts = new ArrayList<>();
		int last = -1;
		for (JsonElement element : array) {
			if (!element.isJsonArray() || element.getAsJsonArray().size() != 2) {
				return;
			}
			int position = element.getAsJsonArray().get(0).getAsInt();
			String text = element.getAsJsonArray().get(1).getAsString();
			if (position < last || position > length || text.length() > 160 || !INSERT_TEXT.matcher(text).matches()) {
				return;
			}
			last = position;
			inserts.add(new EmojiText.Insert(position, text));
		}
		Overlay overlay = new Overlay(sender, hash, length, List.copyOf(inserts), System.currentTimeMillis());
		if (!patchRecent(overlay)) {
			remember(overlay);
		}
	}

	public static JsonObject toFrame(Overlay overlay) {
		JsonObject frame = new JsonObject();
		frame.addProperty("t", "chat");
		frame.addProperty("h", overlay.hash());
		frame.addProperty("l", overlay.length());
		JsonArray inserts = new JsonArray();
		for (EmojiText.Insert insert : overlay.inserts()) {
			JsonArray pair = new JsonArray();
			pair.add(insert.index());
			pair.add(insert.text());
			inserts.add(pair);
		}
		frame.add("x", inserts);
		return frame;
	}

	public Component decorate(Component message) {
		long now = System.currentTimeMillis();
		expire(now);
		Component result = message;
		List<EmojiText.Segment> segments = EmojiText.segments(message);
		String plain = EmojiText.plain(segments);
		for (Iterator<Overlay> iterator = pending.iterator(); iterator.hasNext(); ) {
			Overlay overlay = iterator.next();
			int at = find(plain, overlay);
			if (at >= 0) {
				iterator.remove();
				result = EmojiText.insertAndRender(message, at, overlay.inserts());
				break;
			}
		}
		Component rendered = EmojiText.render(result);
		recent.addLast(new Recent(rendered, now));
		while (recent.size() > MAX_RECENT) {
			recent.removeFirst();
		}
		return rendered;
	}

	private boolean patchRecent(Overlay overlay) {
		if (!(Minecraft.getInstance().gui.hud.getChat() instanceof ChatAccess access)) {
			return false;
		}
		long now = System.currentTimeMillis();
		expire(now);
		int scanned = 0;
		for (int i = 0; i < access.size() && scanned < MAX_RECENT; i++, scanned++) {
			Component content = access.content(i);
			if (!isRecent(content)) {
				continue;
			}
			String plain = EmojiText.plain(EmojiText.segments(content));
			int at = find(plain, overlay);
			if (at >= 0) {
				Component patched = EmojiText.insertAndRender(content, at, overlay.inserts());
				access.replace(i, patched);
				access.refresh();
				return true;
			}
		}
		return false;
	}

	private boolean isRecent(Component content) {
		for (Recent entry : recent) {
			if (entry.content() == content) {
				return true;
			}
		}
		return false;
	}

	private int find(String plain, Overlay overlay) {
		int length = overlay.length();
		if (plain.length() < length) {
			return -1;
		}
		if (!senderMentioned(plain, overlay.sender())) {
			return -1;
		}
		for (int start = plain.length() - length; start >= 0; start--) {
			if (hash(plain.substring(start, start + length)).equals(overlay.hash())) {
				return start;
			}
		}
		return -1;
	}

	private static boolean senderMentioned(String plain, UUID sender) {
		Minecraft minecraft = Minecraft.getInstance();
		if (sender == null || minecraft.getConnection() == null || minecraft.getUser().getProfileId().equals(sender)) {
			return true;
		}
		PlayerInfo info = minecraft.getConnection().getPlayerInfo(sender);
		return info == null || plain.contains(info.getProfile().name());
	}

	private void remember(Overlay overlay) {
		pending.addLast(overlay);
		while (pending.size() > MAX_PENDING) {
			pending.removeFirst();
		}
	}

	private void expire(long now) {
		pending.removeIf(overlay -> now - overlay.at() > OVERLAY_LIFETIME_MS);
		recent.removeIf(entry -> now - entry.at() > OVERLAY_LIFETIME_MS);
	}

	public void clear() {
		pending.clear();
		recent.clear();
	}

	static String hash(String text) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest, 0, 8);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException(exception);
		}
	}
}

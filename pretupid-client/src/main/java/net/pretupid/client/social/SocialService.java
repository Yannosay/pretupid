package net.pretupid.client.social;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.PretupidInfo;
import net.pretupid.client.network.ApiClient;
import net.pretupid.client.network.AuthSession;
import net.pretupid.client.network.Json;
import net.pretupid.client.ui.Icon;

public final class SocialService {
	private static final long ACTIVE_POLL_MS = 4_000L;
	private static final long IDLE_POLL_MS = 20_000L;
	private static final int MAX_MESSAGES_PER_CHAT = 200;

	private final ApiClient api;
	private final AuthSession session;
	private final Map<UUID, List<Message>> conversations = new LinkedHashMap<>();
	private final Set<UUID> knownIncoming = new HashSet<>();
	private volatile List<Friend> friends = List.of();
	private volatile List<Person> incoming = List.of();
	private volatile List<Person> outgoing = List.of();
	private volatile List<Person> blocked = List.of();
	private volatile boolean loaded;
	private volatile boolean syncing;
	private long cursor;
	private long nextSync;
	private boolean firstSync = true;
	private boolean active;
	private UUID openConversation;

	public record Friend(UUID uuid, String name, boolean online, long lastSeen, String server, String minecraft, int unread) {
	}

	public record Person(UUID uuid, String name, long at) {
	}

	public record Invite(String address, String label, boolean lan, String minecraft) {
	}

	public record Message(long id, UUID from, UUID to, String text, Invite invite, long at) {
		public boolean mine(UUID self) {
			return from.equals(self);
		}
	}

	public SocialService(ApiClient api, AuthSession session) {
		this.api = api;
		this.session = session;
	}

	public boolean available() {
		return session.online();
	}

	public boolean loaded() {
		return loaded;
	}

	public List<Friend> friends() {
		return friends;
	}

	public List<Person> incoming() {
		return incoming;
	}

	public List<Person> outgoing() {
		return outgoing;
	}

	public List<Person> blocked() {
		return blocked;
	}

	public int pendingCount() {
		int unread = 0;
		for (Friend friend : friends) {
			unread += friend.unread();
		}
		return unread + incoming.size();
	}

	public Optional<Friend> friend(UUID uuid) {
		return friends.stream().filter(friend -> friend.uuid().equals(uuid)).findFirst();
	}

	public Set<UUID> friendIds() {
		Set<UUID> ids = new HashSet<>();
		for (Friend friend : friends) {
			ids.add(friend.uuid());
		}
		return ids;
	}

	public List<Message> conversation(UUID uuid) {
		synchronized (conversations) {
			List<Message> messages = conversations.get(uuid);
			return messages == null ? List.of() : List.copyOf(messages);
		}
	}

	public void setActive(boolean active) {
		this.active = active;
		if (active) {
			nextSync = Math.min(nextSync, System.currentTimeMillis());
		}
	}

	public void openConversation(UUID uuid) {
		openConversation = uuid;
		if (uuid != null) {
			loadHistory(uuid);
		}
	}

	public void tick(Minecraft minecraft) {
		if (!session.online()) {
			if (loaded) {
				reset();
			}
			return;
		}
		long now = System.currentTimeMillis();
		if (now >= nextSync && !syncing) {
			sync();
		}
	}

	private void reset() {
		loaded = false;
		friends = List.of();
		incoming = List.of();
		outgoing = List.of();
		blocked = List.of();
		cursor = 0L;
		firstSync = true;
		knownIncoming.clear();
		synchronized (conversations) {
			conversations.clear();
		}
	}

	public void syncSoon() {
		nextSync = Math.min(nextSync, System.currentTimeMillis() + 300L);
	}

	private void sync() {
		syncing = true;
		String after = firstSync ? "init" : Long.toString(cursor);
		session.withToken(token -> api.json("GET", "/social/sync?after=" + after, null, token)).whenComplete((response, error) -> Minecraft.getInstance().execute(() -> {
			syncing = false;
			boolean pushed = PretupidClient.get().realtime().connected();
			long interval = active ? ACTIVE_POLL_MS : IDLE_POLL_MS;
			nextSync = System.currentTimeMillis() + (pushed ? interval * 4 : interval);
			if (error != null) {
				nextSync = System.currentTimeMillis() + 30_000L;
				return;
			}
			apply(response);
		}));
	}

	private void apply(JsonObject response) {
		UUID self = Minecraft.getInstance().getUser().getProfileId();
		List<Friend> parsedFriends = new ArrayList<>();
		for (JsonElement element : Json.array(response, "friends")) {
			JsonObject object = element.isJsonObject() ? element.getAsJsonObject() : null;
			UUID uuid = uuid(Json.string(object, "uuid"));
			String name = Json.string(object, "name", 16);
			if (uuid != null && name != null) {
				String server = Json.string(object, "server", 253);
				String minecraft = Json.string(object, "minecraft", 16);
				int unread = (int) Math.clamp(Json.number(object, "unread", 0), 0, 999);
				if (uuid.equals(openConversation)) {
					unread = 0;
				}
				parsedFriends.add(new Friend(uuid, name, Json.bool(object, "online", false), Json.number(object, "lastSeen", 0L), server, minecraft, unread));
			}
		}
		parsedFriends.sort((a, b) -> a.online() != b.online() ? (a.online() ? -1 : 1) : a.name().compareToIgnoreCase(b.name()));
		friends = Collections.unmodifiableList(parsedFriends);
		incoming = people(response, "incoming");
		outgoing = people(response, "outgoing");
		blocked = people(response, "blocked");
		for (Person person : incoming) {
			if (knownIncoming.add(person.uuid()) && !firstSync) {
				notify(Component.translatable("pretupid.friends.toast_request"), Component.literal(person.name()), Icon.item("name_tag"));
			}
		}
		knownIncoming.retainAll(incoming.stream().map(Person::uuid).toList());
		for (JsonElement element : Json.array(response, "messages")) {
			Message message = message(element);
			if (message == null) {
				continue;
			}
			UUID other = message.from().equals(self) ? message.to() : message.from();
			boolean added = store(other, message);
			if (added && !firstSync && !message.from().equals(self) && !other.equals(openConversation)) {
				String sender = friend(other).map(Friend::name).orElse("?");
				if (message.invite() != null) {
					notify(Component.translatable("pretupid.friends.toast_invite", sender), Component.literal(message.invite().label().isEmpty() ? message.invite().address() : message.invite().label()), Icon.item("filled_map"));
				} else {
					notify(Component.literal(sender), Component.literal(message.text()), Icon.item("writable_book"));
				}
			}
		}
		cursor = Math.max(cursor, Json.number(response, "cursor", cursor));
		firstSync = false;
		loaded = true;
	}

	private void notify(Component title, Component body, Icon icon) {
		if (PretupidClient.get().settings().friendNotifications.enabled()) {
			PretupidClient.get().notifications().push(title, body, icon, null, 0xFF60A5FA, 5000L);
		}
	}

	private boolean store(UUID other, Message message) {
		synchronized (conversations) {
			List<Message> list = conversations.computeIfAbsent(other, key -> new ArrayList<>());
			for (Message existing : list) {
				if (existing.id() == message.id()) {
					return false;
				}
			}
			list.add(message);
			list.sort((a, b) -> Long.compare(a.id(), b.id()));
			while (list.size() > MAX_MESSAGES_PER_CHAT) {
				list.removeFirst();
			}
			return true;
		}
	}

	private void loadHistory(UUID friend) {
		session.withToken(token -> api.json("GET", "/social/messages/" + friend, null, token)).whenComplete((response, error) -> Minecraft.getInstance().execute(() -> {
			if (error != null) {
				return;
			}
			for (JsonElement element : Json.array(response, "messages")) {
				Message message = message(element);
				if (message != null) {
					store(friend, message);
				}
			}
			List<Friend> updated = new ArrayList<>();
			for (Friend entry : friends) {
				updated.add(entry.uuid().equals(friend) ? new Friend(entry.uuid(), entry.name(), entry.online(), entry.lastSeen(), entry.server(), entry.minecraft(), 0) : entry);
			}
			friends = Collections.unmodifiableList(updated);
		}));
	}

	public CompletableFuture<String> sendRequest(String name) {
		JsonObject body = new JsonObject();
		body.addProperty("name", name);
		return session.withToken(token -> api.json("POST", "/social/requests", body, token)).thenApply(response -> {
			syncSoon();
			return Json.string(response, "status");
		});
	}

	public CompletableFuture<Void> respond(UUID uuid, boolean accept) {
		JsonObject body = new JsonObject();
		body.addProperty("uuid", uuid.toString());
		body.addProperty("accept", accept);
		return session.withToken(token -> api.json("POST", "/social/respond", body, token)).thenAccept(response -> syncSoon());
	}

	public CompletableFuture<Void> remove(UUID uuid) {
		return session.withToken(token -> api.json("DELETE", "/social/friends/" + uuid, null, token)).thenAccept(response -> syncSoon());
	}

	public CompletableFuture<Void> block(UUID uuid, boolean blockedState) {
		JsonObject body = new JsonObject();
		body.addProperty("uuid", uuid.toString());
		body.addProperty("blocked", blockedState);
		return session.withToken(token -> api.json("POST", "/social/blocks", body, token)).thenAccept(response -> syncSoon());
	}

	public CompletableFuture<Void> sendMessage(UUID friend, String text) {
		JsonObject body = new JsonObject();
		body.addProperty("kind", "text");
		body.addProperty("text", text);
		return post(friend, body);
	}

	public CompletableFuture<Void> sendInvite(UUID friend, String address, String label, boolean lan) {
		JsonObject body = new JsonObject();
		body.addProperty("kind", "invite");
		body.addProperty("address", address);
		body.addProperty("label", label);
		body.addProperty("lan", lan);
		body.addProperty("minecraft", PretupidInfo.minecraftVersion());
		return post(friend, body);
	}

	private CompletableFuture<Void> post(UUID friend, JsonObject body) {
		return session.withToken(token -> api.json("POST", "/social/messages/" + friend, body, token)).thenAccept(response -> {
			Message message = message(response.get("message"));
			if (message != null) {
				Minecraft.getInstance().execute(() -> store(friend, message));
			}
		});
	}

	private static List<Person> people(JsonObject response, String key) {
		List<Person> result = new ArrayList<>();
		for (JsonElement element : Json.array(response, key)) {
			JsonObject object = element.isJsonObject() ? element.getAsJsonObject() : null;
			UUID uuid = uuid(Json.string(object, "uuid"));
			String name = Json.string(object, "name", 16);
			if (uuid != null) {
				result.add(new Person(uuid, name == null ? uuid.toString().substring(0, 8) : name, Json.number(object, "created_at", 0L)));
			}
		}
		return Collections.unmodifiableList(result);
	}

	private static Message message(JsonElement element) {
		if (element == null || !element.isJsonObject()) {
			return null;
		}
		JsonObject object = element.getAsJsonObject();
		UUID from = uuid(Json.string(object, "from"));
		UUID to = uuid(Json.string(object, "to"));
		long id = Json.number(object, "id", -1L);
		if (from == null || to == null || id < 0) {
			return null;
		}
		long at = Json.number(object, "at", 0L);
		if ("invite".equals(Json.string(object, "kind"))) {
			JsonObject invite = Json.object(object, "body");
			String address = Json.string(invite, "address", 253);
			if (address == null || !address.matches("[A-Za-z0-9.\\-:\\[\\]_]{1,253}")) {
				return null;
			}
			String label = Json.string(invite, "label", 48);
			return new Message(id, from, to, "", new Invite(address, label == null ? "" : label, Json.bool(invite, "lan", false), Json.string(invite, "minecraft", 16)), at);
		}
		String text = Json.string(object, "body", 300);
		if (text == null) {
			return null;
		}
		return new Message(id, from, to, sanitize(text), null, at);
	}

	public static String sanitize(String text) {
		StringBuilder builder = new StringBuilder(text.length());
		text.codePoints().filter(cp -> cp == '\n' || cp >= 0x20 && cp != 0x7F && cp != 0xA7 && !(cp >= 0x202A && cp <= 0x202E) && !(cp >= 0x2066 && cp <= 0x2069))
			.forEach(builder::appendCodePoint);
		return builder.toString();
	}

	private static UUID uuid(String value) {
		if (value == null || value.length() != 36) {
			return null;
		}
		try {
			return UUID.fromString(value);
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}
}

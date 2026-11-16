package net.pretupid.client.network;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.PretupidInfo;
import net.pretupid.client.cosmetic.CapeTextures;

public final class RealtimeClient {
	private static final long PING_INTERVAL_MS = 25_000L;
	private static final int MAX_FRAME_CHARS = 8192;

	private final AuthSession session;
	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
	private volatile WebSocket socket;
	private volatile boolean connecting;
	private volatile String joinedRoom;
	private String wantedRoom;
	private long nextAttempt;
	private long nextPing;
	private int failures;

	public RealtimeClient(AuthSession session) {
		this.session = session;
	}

	public boolean connected() {
		return socket != null;
	}

	public void tick(Minecraft minecraft) {
		long now = System.currentTimeMillis();
		if (!session.online()) {
			disconnect();
			return;
		}
		wantedRoom = roomFor(minecraft);
		WebSocket current = socket;
		if (current == null) {
			if (!connecting && now >= nextAttempt) {
				connect();
			}
			return;
		}
		if (!Objects.equals(wantedRoom, joinedRoom)) {
			JsonObject frame = new JsonObject();
			frame.addProperty("t", "join");
			if (wantedRoom == null) {
				frame.add("room", com.google.gson.JsonNull.INSTANCE);
			} else {
				frame.addProperty("room", wantedRoom);
			}
			joinedRoom = wantedRoom;
			send(frame);
		}
		if (now >= nextPing) {
			nextPing = now + PING_INTERVAL_MS;
			JsonObject ping = new JsonObject();
			ping.addProperty("t", "ping");
			send(ping);
		}
	}

	private static String roomFor(Minecraft minecraft) {
		if (minecraft.level == null) {
			return null;
		}
		ServerData current = minecraft.getCurrentServer();
		var tunnel = PretupidClient.get().hosting().tunnelRoom(current == null ? null : current.ip);
		if (tunnel.isPresent()) {
			return hash(tunnel.get());
		}
		var hosted = PretupidClient.get().hosting().lanAddress();
		if (hosted.isPresent()) {
			return hash("lan:" + hosted.get());
		}
		ServerData server = minecraft.getCurrentServer();
		if (server == null || minecraft.hasSingleplayerServer()) {
			return null;
		}
		return hash((server.isLan() ? "lan:" : "server:") + server.ip.trim().toLowerCase(Locale.ROOT));
	}

	private static String hash(String value) {
		return CapeTextures.sha256(value.getBytes(StandardCharsets.UTF_8));
	}

	private void connect() {
		String token = session.token();
		if (token == null) {
			return;
		}
		connecting = true;
		http.newWebSocketBuilder()
			.header("Authorization", "Bearer " + token)
			.header("User-Agent", PretupidInfo.userAgent())
			.connectTimeout(Duration.ofSeconds(10))
			.buildAsync(PretupidInfo.realtimeUri(), new Listener())
			.whenComplete((ws, error) -> Minecraft.getInstance().execute(() -> {
				connecting = false;
				if (error != null) {
					scheduleRetry();
					if (ApiClient.statusOf(error) == 401 || String.valueOf(ApiClient.unwrap(error).getMessage()).contains("401")) {
						session.invalidate();
					}
					return;
				}
				socket = ws;
				joinedRoom = null;
				failures = 0;
				nextPing = System.currentTimeMillis() + PING_INTERVAL_MS;
			}));
	}

	private void scheduleRetry() {
		failures++;
		nextAttempt = System.currentTimeMillis() + Math.min(120_000L, 2_000L * (1L << Math.min(6, failures)));
	}

	public void disconnect() {
		WebSocket current = socket;
		socket = null;
		joinedRoom = null;
		if (current != null) {
			current.sendClose(WebSocket.NORMAL_CLOSURE, "bye").exceptionally(error -> null);
		}
	}

	public void sendEmote(String id) {
		JsonObject frame = new JsonObject();
		frame.addProperty("t", "emote");
		if (id == null) {
			frame.add("id", com.google.gson.JsonNull.INSTANCE);
		} else {
			frame.addProperty("id", id);
		}
		send(frame);
	}

	public void sendProfileChanged() {
		JsonObject frame = new JsonObject();
		frame.addProperty("t", "profile");
		send(frame);
	}

	private void send(JsonObject frame) {
		WebSocket current = socket;
		if (current != null) {
			current.sendText(frame.toString(), true).exceptionally(error -> {
				Minecraft.getInstance().execute(this::onClosed);
				return null;
			});
		}
	}

	private void onClosed() {
		socket = null;
		joinedRoom = null;
		scheduleRetry();
	}

	private void handle(String text) {
		JsonElement element;
		try {
			element = JsonParser.parseString(text);
		} catch (RuntimeException exception) {
			return;
		}
		if (!element.isJsonObject()) {
			return;
		}
		JsonObject frame = element.getAsJsonObject();
		String type = Json.string(frame, "t");
		PretupidClient client = PretupidClient.get();
		switch (type == null ? "" : type) {
			case "hello" -> joinedRoom = null;
			case "emote" -> {
				UUID uuid = uuid(Json.string(frame, "u"));
				if (uuid != null) {
					String id = Json.string(frame, "id", 32);
					client.emotes().onRealtimeEmote(uuid, id == null || id.isEmpty() ? null : id);
				}
			}
			case "profile" -> {
				UUID uuid = uuid(Json.string(frame, "u"));
				if (uuid != null) {
					client.presence().invalidate(uuid);
				}
			}
			case "social" -> client.social().syncSoon();
			default -> {
			}
		}
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

	private final class Listener implements WebSocket.Listener {
		private final StringBuilder buffer = new StringBuilder();

		@Override
		public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
			if (buffer.length() + data.length() > MAX_FRAME_CHARS) {
				buffer.setLength(0);
				webSocket.abort();
				return CompletableFuture.completedFuture(null);
			}
			buffer.append(data);
			if (last) {
				String text = buffer.toString();
				buffer.setLength(0);
				Minecraft.getInstance().execute(() -> handle(text));
			}
			webSocket.request(1);
			return null;
		}

		@Override
		public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
			Minecraft.getInstance().execute(() -> {
				if (socket == webSocket) {
					onClosed();
				}
			});
			return null;
		}

		@Override
		public void onError(WebSocket webSocket, Throwable error) {
			Minecraft.getInstance().execute(() -> {
				if (socket == webSocket) {
					onClosed();
				}
			});
		}
	}
}

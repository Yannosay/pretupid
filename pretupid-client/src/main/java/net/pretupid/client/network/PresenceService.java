package net.pretupid.client.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.pretupid.client.cosmetic.Wearables;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.world.entity.player.Player;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.PretupidInfo;
import net.pretupid.client.config.ClientSettings;

public final class PresenceService {
	private static final long ACTIVE_REFRESH_MS = 20_000L;
	private static final long IDLE_LOOKUP_MS = 5_000L;
	private static final long POSITIVE_TTL_MS = 30_000L;
	private static final long NEGATIVE_TTL_MS = 90_000L;
	private static final long DEFAULT_HEARTBEAT_MS = 60_000L;
	private static final int MAX_LOOKUP_BATCH = 90;

	private final ApiClient api;
	private final AuthSession session;
	private final Map<UUID, CacheEntry> cache = new ConcurrentHashMap<>();
	private final Set<UUID> pending = ConcurrentHashMap.newKeySet();
	private final List<BiConsumer<UUID, RemoteProfile>> listeners = new ArrayList<>();
	private volatile int onlineUsers;
	private volatile boolean heartbeatInFlight;
	private volatile boolean lookupInFlight;
	private long nextHeartbeat;
	private long nextLookup;
	private String lastServer;
	private boolean profileChanged;

	public PresenceService(ApiClient api, AuthSession session) {
		this.api = api;
		this.session = session;
	}

	public void onProfile(BiConsumer<UUID, RemoteProfile> listener) {
		listeners.add(listener);
	}

	public int onlineUsers() {
		return onlineUsers;
	}

	public int knownUsersNearby() {
		long now = System.currentTimeMillis();
		int count = 0;
		for (CacheEntry entry : cache.values()) {
			if (entry.profile != null && entry.expiresAt > now) {
				count++;
			}
		}
		return count;
	}

	public Optional<RemoteProfile> profileOf(UUID uuid) {
		CacheEntry entry = uuid == null ? null : cache.get(uuid);
		return entry == null ? Optional.empty() : Optional.ofNullable(entry.profile);
	}

	public void requestHeartbeat() {
		nextHeartbeat = Math.min(nextHeartbeat, System.currentTimeMillis() + 1_200L);
		profileChanged = true;
	}

	public void invalidate(UUID uuid) {
		CacheEntry entry = cache.get(uuid);
		if (entry != null) {
			cache.put(uuid, new CacheEntry(entry.profile, 0L, 0L));
		}
		nextLookup = Math.min(nextLookup, System.currentTimeMillis() + 150L);
	}

	public void tick(Minecraft minecraft) {
		ClientSettings settings = PretupidClient.get().settings();
		long now = System.currentTimeMillis();
		String server = currentServer(minecraft);
		if (!java.util.Objects.equals(server, lastServer)) {
			lastServer = server;
			requestHeartbeat();
		}
		if (session.online() && now >= nextHeartbeat && !heartbeatInFlight) {
			heartbeat(settings, server);
		}
		if (settings.showOtherBadges.enabled() && minecraft.level != null && now >= nextLookup && !lookupInFlight) {
			lookupVisible(minecraft, now);
		}
	}

	private static String currentServer(Minecraft minecraft) {
		if (minecraft.level == null) {
			return null;
		}
		ServerData server = minecraft.getCurrentServer();
		if (server != null && !minecraft.hasSingleplayerServer() && !server.isLan()) {
			return server.ip;
		}
		return null;
	}

	private void heartbeat(ClientSettings settings, String server) {
		heartbeatInFlight = true;
		JsonObject body = new JsonObject();
		body.add("style", settings.nametagStyle().toJson());
		JsonObject equipped = new JsonObject();
		String cape = settings.cape.get();
		if (!cape.isEmpty()) {
			equipped.addProperty("cape", cape);
		}
		String trail = settings.trail.get();
		if (!trail.isEmpty()) {
			equipped.addProperty("trail", trail);
		}
		Wearables.Loadout wearables = Wearables.own();
		if (wearables.hat() != null) {
			equipped.addProperty("hat", wearables.hat());
		}
		if (wearables.boots() != null) {
			equipped.addProperty("boots", wearables.boots());
		}
		if (wearables.pet() != null) {
			equipped.addProperty("pet", wearables.pet());
		}
		body.add("equipped", equipped);
		body.addProperty("clientVersion", PretupidInfo.shortVersion());
		body.addProperty("allowRequests", settings.allowFriendRequests.enabled());
		body.addProperty("shareServer", settings.shareServerWithFriends.enabled() && server != null);
		if (settings.shareServerWithFriends.enabled() && server != null) {
			body.addProperty("server", server);
		}
		Minecraft minecraft = Minecraft.getInstance();
		session.withToken(token -> api.json("POST", "/presence", body, token)).whenComplete((response, error) -> minecraft.execute(() -> {
			heartbeatInFlight = false;
			if (error != null) {
				nextHeartbeat = System.currentTimeMillis() + 15_000L;
				return;
			}
			onlineUsers = (int) Math.max(0, Json.number(response, "online", 0));
			if (profileChanged) {
				profileChanged = false;
				PretupidClient.get().realtime().sendProfileChanged();
			}
			long interval = Json.number(response, "nextHeartbeatSeconds", DEFAULT_HEARTBEAT_MS / 1000L) * 1000L;
			nextHeartbeat = System.currentTimeMillis() + Math.clamp(interval, 20_000L, 300_000L);
		}));
	}

	public void sendEmote(String id) {
		if (!session.online()) {
			return;
		}
		JsonObject body = new JsonObject();
		if (id == null) {
			body.add("id", com.google.gson.JsonNull.INSTANCE);
		} else {
			body.addProperty("id", id);
		}
		session.withToken(token -> api.json("POST", "/emote", body, token)).exceptionally(error -> null);
	}

	private void lookupVisible(Minecraft minecraft, long now) {
		UUID self = minecraft.getUser().getProfileId();
		List<UUID> batch = new ArrayList<>();
		boolean anyActive = false;
		ClientPacketListener connection = minecraft.getConnection();
		if (minecraft.level != null) {
			for (Player player : minecraft.level.players()) {
				anyActive |= consider(player.getUUID(), self, now, batch, true);
			}
		}
		if (connection != null) {
			for (PlayerInfo info : connection.getOnlinePlayers()) {
				consider(info.getProfile().id(), self, now, batch, false);
			}
		}
		nextLookup = now + (anyActive ? ACTIVE_REFRESH_MS : IDLE_LOOKUP_MS);
		if (batch.isEmpty()) {
			return;
		}
		List<UUID> request = batch.size() > MAX_LOOKUP_BATCH ? new ArrayList<>(batch.subList(0, MAX_LOOKUP_BATCH)) : batch;
		pending.addAll(request);
		lookupInFlight = true;
		JsonObject body = new JsonObject();
		JsonArray uuids = new JsonArray();
		request.forEach(uuid -> uuids.add(uuid.toString()));
		body.add("uuids", uuids);
		api.json("POST", "/presence/lookup", body, null).whenComplete((response, error) -> minecraft.execute(() -> {
			long completed = System.currentTimeMillis();
			lookupInFlight = false;
			request.forEach(pending::remove);
			if (error != null) {
				nextLookup = completed + 15_000L;
				for (UUID uuid : request) {
					cache.putIfAbsent(uuid, new CacheEntry(null, completed + NEGATIVE_TTL_MS, completed));
				}
				return;
			}
			JsonObject users = Json.object(response, "users");
			for (UUID uuid : request) {
				Optional<RemoteProfile> profile = RemoteProfile.fromJson(users == null ? null : users.get(uuid.toString()), completed);
				if (profile.isPresent()) {
					cache.put(uuid, new CacheEntry(profile.get(), completed + POSITIVE_TTL_MS, completed));
					for (BiConsumer<UUID, RemoteProfile> listener : listeners) {
						listener.accept(uuid, profile.get());
					}
				} else {
					cache.put(uuid, new CacheEntry(null, completed + NEGATIVE_TTL_MS, completed));
				}
			}
		}));
	}

	private boolean consider(UUID uuid, UUID self, long now, List<UUID> batch, boolean inWorld) {
		if (uuid == null || uuid.equals(self) || uuid.version() != 4 || pending.contains(uuid) || batch.contains(uuid)) {
			return false;
		}
		CacheEntry entry = cache.get(uuid);
		boolean active = entry != null && entry.profile != null;
		if (entry == null || entry.expiresAt <= now || inWorld && active && now - entry.fetchedAt >= ACTIVE_REFRESH_MS) {
			batch.add(uuid);
		}
		return inWorld && active;
	}

	public void onWorldLeft() {
		long now = System.currentTimeMillis();
		cache.entrySet().removeIf(entry -> entry.getValue().expiresAt <= now);
	}

	public void shutdown() {
		String token = session.token();
		if (token == null) {
			return;
		}
		try {
			api.json("DELETE", "/presence", null, token).get(2, TimeUnit.SECONDS);
		} catch (Exception exception) {
			PretupidClient.LOGGER.debug("Presence sign-out did not complete before shutdown");
		}
	}

	public void signOut() {
		String token = session.token();
		if (token != null) {
			api.json("DELETE", "/presence", null, token).exceptionally(error -> null);
		}
	}

	private record CacheEntry(RemoteProfile profile, long expiresAt, long fetchedAt) {
	}
}

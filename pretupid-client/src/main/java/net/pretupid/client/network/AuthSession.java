package net.pretupid.client.network;

import com.google.gson.JsonObject;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.services.ProfileResult;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.util.Signer;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.ProfileKeyPair;
import net.minecraft.world.entity.player.ProfilePublicKey;
import net.pretupid.client.PretupidClient;

public final class AuthSession {
	public enum Status {
		DISABLED,
		CONNECTING,
		ONLINE,
		NO_ACCOUNT,
		AUTH_FAILED,
		UNREACHABLE,
		SUSPENDED
	}

	private static final long AUTH_BACKOFF_MS = 10 * 60_000L;

	private final ApiClient api;
	private volatile Status status = Status.DISABLED;
	private volatile String token;
	private volatile long tokenExpiresAt;
	private volatile boolean authInFlight;
	private volatile String lastError;
	private long backoffUntil;
	private int failures;

	public AuthSession(ApiClient api) {
		this.api = api;
	}

	public Status status() {
		return status;
	}

	public boolean online() {
		return status == Status.ONLINE && token != null;
	}

	public String lastError() {
		return lastError;
	}

	public String token() {
		return token;
	}

	public void tick(Minecraft minecraft, boolean enabled) {
		long now = System.currentTimeMillis();
		if (!enabled) {
			if (status != Status.DISABLED) {
				token = null;
				status = Status.DISABLED;
			}
			return;
		}
		if (status == Status.DISABLED) {
			status = Status.CONNECTING;
			backoffUntil = 0L;
		}
		if (now < backoffUntil || authInFlight) {
			return;
		}
		if (token == null || now > tokenExpiresAt - 60_000L) {
			authenticate(minecraft);
		}
	}

	public void retryNow() {
		backoffUntil = 0L;
		failures = 0;
	}

	public void invalidate() {
		token = null;
		if (status == Status.ONLINE) {
			status = Status.CONNECTING;
		}
	}

	public <T> CompletableFuture<T> withToken(Function<String, CompletableFuture<T>> call) {
		String current = token;
		if (current == null) {
			return CompletableFuture.failedFuture(new ApiClient.HttpStatusException(401, "Not connected to Pretupid services"));
		}
		return call.apply(current).whenComplete((result, error) -> {
			if (error != null && ApiClient.statusOf(error) == 401) {
				invalidate();
			}
			if (error != null && ApiClient.statusOf(error) == 403 && ApiClient.describe(error).contains("suspended")) {
				token = null;
				status = Status.SUSPENDED;
				backoffUntil = System.currentTimeMillis() + AUTH_BACKOFF_MS;
			}
		});
	}

	private void authenticate(Minecraft minecraft) {
		User user = minecraft.getUser();
		UUID uuid = user.getProfileId();
		String name = user.getName();
		String accessToken = user.getAccessToken();
		if (uuid == null || uuid.version() != 4 || accessToken == null || accessToken.length() < 16) {
			status = Status.NO_ACCOUNT;
			lastError = null;
			backoffUntil = System.currentTimeMillis() + AUTH_BACKOFF_MS;
			return;
		}
		authInFlight = true;
		if (status != Status.ONLINE) {
			status = Status.CONNECTING;
		}
		CompletableFuture<ProfileKeyPair> keyPair = minecraft.getProfileKeyPairManager().prepareKeyPair()
			.thenApply(optional -> optional.orElseThrow(() -> new AccountException("No Mojang profile key")));
		CompletableFuture<Property> textures = CompletableFuture.supplyAsync(() -> signedTextures(minecraft, uuid), Util.nonCriticalIoPool());
		JsonObject challengeBody = new JsonObject();
		challengeBody.addProperty("uuid", uuid.toString());
		challengeBody.addProperty("name", name);
		api.json("POST", "/auth/challenge", challengeBody, null)
			.thenCombine(keyPair, (challenge, pair) -> verifyBody(challenge, pair, uuid))
			.thenCombine(textures, (body, property) -> {
				JsonObject texturesJson = new JsonObject();
				texturesJson.addProperty("value", property.value());
				texturesJson.addProperty("signature", property.signature());
				body.add("textures", texturesJson);
				return body;
			})
			.thenCompose(body -> api.json("POST", "/auth/verify", body, null))
			.whenComplete((session, error) -> minecraft.execute(() -> {
				authInFlight = false;
				if (error != null) {
					handleFailure(error);
					return;
				}
				String sessionToken = Json.string(session, "token");
				long expiresIn = Json.number(session, "expiresIn", 3600L);
				if (sessionToken == null || sessionToken.length() > 1024) {
					handleFailure(new IllegalStateException("Malformed session"));
					return;
				}
				token = sessionToken;
				tokenExpiresAt = System.currentTimeMillis() + Math.clamp(expiresIn, 120L, 86_400L) * 1000L;
				failures = 0;
				lastError = null;
				status = Status.ONLINE;
			}));
	}

	private static JsonObject verifyBody(JsonObject challenge, ProfileKeyPair pair, UUID uuid) {
		String challengeId = Json.string(challenge, "challengeId");
		String serverId = Json.string(challenge, "serverId");
		if (challengeId == null || serverId == null || serverId.length() > 64) {
			throw new IllegalStateException("Malformed challenge");
		}
		ProfilePublicKey.Data key = pair.publicKey().data();
		byte[] payload = ("pretupid-auth:" + serverId + ":" + uuid).getBytes(StandardCharsets.UTF_8);
		byte[] signature = Signer.from(pair.privateKey(), "SHA256withRSA").sign(payload);
		if (signature == null) {
			throw new AccountException("Profile key could not sign");
		}
		Base64.Encoder base64 = Base64.getEncoder();
		JsonObject profileKey = new JsonObject();
		profileKey.addProperty("publicKey", base64.encodeToString(key.key().getEncoded()));
		profileKey.addProperty("expiresAt", key.expiresAt().toEpochMilli());
		profileKey.addProperty("keySignature", base64.encodeToString(key.keySignature()));
		profileKey.addProperty("signature", base64.encodeToString(signature));
		JsonObject body = new JsonObject();
		body.addProperty("challengeId", challengeId);
		body.add("profileKey", profileKey);
		return body;
	}

	private static Property signedTextures(Minecraft minecraft, UUID uuid) {
		ProfileResult result = minecraft.services().sessionService().fetchProfile(uuid, true);
		if (result == null) {
			throw new IllegalStateException("Profile lookup failed");
		}
		return result.profile().properties().get("textures").stream()
			.filter(Property::hasSignature)
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("Profile has no signed textures"));
	}

	private void handleFailure(Throwable error) {
		Throwable cause = ApiClient.unwrap(error);
		lastError = ApiClient.describe(cause);
		if (cause instanceof AccountException || ApiClient.statusOf(cause) == 403 && !lastError.contains("suspended")) {
			status = Status.AUTH_FAILED;
			backoffUntil = System.currentTimeMillis() + AUTH_BACKOFF_MS;
			PretupidClient.LOGGER.info("Pretupid services unavailable for this account: {}", lastError);
			return;
		}
		if (ApiClient.statusOf(cause) == 403) {
			status = Status.SUSPENDED;
			backoffUntil = System.currentTimeMillis() + AUTH_BACKOFF_MS;
			return;
		}
		failures++;
		status = Status.UNREACHABLE;
		backoffUntil = System.currentTimeMillis() + Math.min(300_000L, 5_000L * (1L << Math.min(6, failures)));
		if (failures == 1) {
			PretupidClient.LOGGER.warn("Pretupid services unreachable: {}", lastError);
		}
	}

	private static final class AccountException extends RuntimeException {
		private AccountException(String message) {
			super(message);
		}
	}
}

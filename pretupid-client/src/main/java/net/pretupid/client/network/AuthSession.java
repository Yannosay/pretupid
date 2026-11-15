package net.pretupid.client.network;

import com.google.gson.JsonObject;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.pretupid.client.PretupidClient;

public final class AuthSession {
	public enum Status {
		DISABLED,
		CONNECTING,
		ONLINE,
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
		String accessToken = user.getAccessToken();
		UUID uuid = user.getProfileId();
		String name = user.getName();
		if (uuid == null || accessToken == null || accessToken.length() < 16 || uuid.version() != 4) {
			status = Status.AUTH_FAILED;
			backoffUntil = System.currentTimeMillis() + AUTH_BACKOFF_MS;
			return;
		}
		authInFlight = true;
		if (status != Status.ONLINE) {
			status = Status.CONNECTING;
		}
		JsonObject challengeBody = new JsonObject();
		challengeBody.addProperty("uuid", uuid.toString());
		challengeBody.addProperty("name", name);
		api.json("POST", "/auth/challenge", challengeBody, null)
			.thenCompose(challenge -> {
				String challengeId = Json.string(challenge, "challengeId");
				String serverId = Json.string(challenge, "serverId");
				if (challengeId == null || serverId == null || serverId.length() > 64) {
					throw new IllegalStateException("Malformed challenge");
				}
				try {
					minecraft.services().sessionService().joinServer(uuid, accessToken, serverId);
				} catch (Exception exception) {
					throw new AccountException(exception);
				}
				JsonObject verifyBody = new JsonObject();
				verifyBody.addProperty("challengeId", challengeId);
				verifyBody.addProperty("name", name);
				return api.json("POST", "/auth/verify", verifyBody, null);
			})
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
		private AccountException(Throwable cause) {
			super(cause.getMessage(), cause);
		}
	}
}

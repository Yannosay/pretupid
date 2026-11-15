package net.pretupid.client.network;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import net.pretupid.client.PretupidInfo;

public final class ApiClient {
	private static final int MAX_JSON_BYTES = 512 * 1024;

	private final AtomicInteger threadCounter = new AtomicInteger();
	private final ExecutorService executor = Executors.newFixedThreadPool(3, runnable -> {
		Thread thread = new Thread(runnable, "Pretupid Network " + threadCounter.incrementAndGet());
		thread.setDaemon(true);
		return thread;
	});
	private final HttpClient http = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(6))
		.followRedirects(HttpClient.Redirect.NEVER)
		.executor(executor)
		.build();
	private final HttpClient downloads = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(8))
		.followRedirects(HttpClient.Redirect.NORMAL)
		.executor(executor)
		.build();

	public CompletableFuture<JsonObject> json(String method, String path, JsonObject body, String bearer) {
		byte[] payload = body == null ? null : body.toString().getBytes(StandardCharsets.UTF_8);
		return send(method, path, payload, body == null ? null : "application/json", bearer);
	}

	public CompletableFuture<JsonObject> send(String method, String path, byte[] body, String contentType, String bearer) {
		HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(PretupidInfo.apiBase() + path))
			.timeout(Duration.ofSeconds(12))
			.header("User-Agent", PretupidInfo.userAgent())
			.header("Accept", "application/json");
		if (bearer != null) {
			builder.header("Authorization", "Bearer " + bearer);
		}
		if (body != null) {
			builder.header("Content-Type", contentType);
			builder.method(method, HttpRequest.BodyPublishers.ofByteArray(body));
		} else {
			builder.method(method, HttpRequest.BodyPublishers.noBody());
		}
		return http.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofByteArray()).thenApply(ApiClient::parse);
	}

	public CompletableFuture<byte[]> download(URI uri, int maxBytes) {
		if (!isTrustedContentHost(uri)) {
			return CompletableFuture.failedFuture(new IllegalArgumentException("Refusing to download from " + uri.getHost()));
		}
		HttpRequest request = HttpRequest.newBuilder(uri)
			.timeout(Duration.ofSeconds(60))
			.header("User-Agent", PretupidInfo.userAgent())
			.GET()
			.build();
		return downloads.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream()).thenApply(response -> {
			if (response.statusCode() != 200) {
				throw new HttpStatusException(response.statusCode(), "Download failed");
			}
			try (var input = response.body()) {
				byte[] bytes = input.readNBytes(maxBytes + 1);
				if (bytes.length > maxBytes) {
					throw new IllegalStateException("Download is larger than expected");
				}
				return bytes;
			} catch (java.io.IOException exception) {
				throw new CompletionException(exception);
			}
		});
	}

	public static boolean isTrustedContentHost(URI uri) {
		if (uri == null || uri.getHost() == null) {
			return false;
		}
		String host = uri.getHost().toLowerCase(Locale.ROOT);
		URI api = PretupidInfo.apiBase();
		String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
		boolean secure = "https".equals(scheme);
		if (host.equalsIgnoreCase(api.getHost()) && (secure || scheme.equalsIgnoreCase(api.getScheme()))) {
			return true;
		}
		return secure && (host.equals("github.com") || host.endsWith(".githubusercontent.com"));
	}

	private static JsonObject parse(HttpResponse<byte[]> response) {
		byte[] bytes = response.body();
		if (bytes.length > MAX_JSON_BYTES) {
			throw new IllegalStateException("Response too large");
		}
		JsonObject object = new JsonObject();
		if (bytes.length > 0) {
			try {
				JsonElement element = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
				if (element.isJsonObject()) {
					object = element.getAsJsonObject();
				}
			} catch (JsonParseException exception) {
				if (response.statusCode() >= 200 && response.statusCode() < 300) {
					throw new IllegalStateException("Malformed response");
				}
			}
		}
		if (response.statusCode() < 200 || response.statusCode() >= 300) {
			String message = Json.string(object, "statusMessage");
			throw new HttpStatusException(response.statusCode(), message == null ? "HTTP " + response.statusCode() : message);
		}
		return object;
	}

	public static Throwable unwrap(Throwable error) {
		Throwable current = error;
		while ((current instanceof CompletionException || current instanceof ExecutionException) && current.getCause() != null) {
			current = current.getCause();
		}
		return current;
	}

	public static String describe(Throwable error) {
		Throwable cause = unwrap(error);
		if (cause instanceof HttpStatusException status) {
			return status.getMessage();
		}
		if (cause instanceof java.net.ConnectException || cause instanceof java.net.http.HttpConnectTimeoutException) {
			return "Pretupid services are unreachable";
		}
		String message = cause.getMessage();
		return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
	}

	public static int statusOf(Throwable error) {
		return unwrap(error) instanceof HttpStatusException status ? status.status() : -1;
	}

	public void shutdown() {
		executor.shutdownNow();
	}

	public static final class HttpStatusException extends RuntimeException {
		private final int status;

		public HttpStatusException(int status, String message) {
			super(message);
			this.status = status;
		}

		public int status() {
			return status;
		}
	}
}

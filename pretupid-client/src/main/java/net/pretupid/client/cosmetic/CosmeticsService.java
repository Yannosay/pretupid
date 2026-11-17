package net.pretupid.client.cosmetic;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.PretupidInfo;
import net.pretupid.client.config.JsonFiles;
import net.pretupid.client.network.ApiClient;
import net.pretupid.client.network.AuthSession;
import net.pretupid.client.network.Json;
import net.pretupid.client.network.RemoteProfile;

public final class CosmeticsService {
	private static final long CATALOG_REFRESH_MS = 10 * 60_000L;
	private static final long ON_DEMAND_REFRESH_MS = 15_000L;

	private final ApiClient api;
	private final AuthSession session;
	private final CapeTextures capes;
	private final Path catalogFile;
	private final HttpClient catalogHttp = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).followRedirects(HttpClient.Redirect.NEVER).build();
	private volatile Catalog catalog = Catalog.EMPTY;
	private volatile String catalogEtag;
	private volatile OwnCape ownCape;
	private volatile boolean ownCapeLoaded;
	private volatile boolean busy;
	private long nextCatalogRefresh;
	private long lastOnDemandRefresh;
	private String serverCatalogVersion;
	private boolean ownCapeRequested;

	public record OwnCape(RemoteProfile.CapeRef texture, String removalReason, boolean uploadsAllowed) {
	}

	public CosmeticsService(ApiClient api, AuthSession session, Path root) {
		this.api = api;
		this.session = session;
		this.capes = new CapeTextures(api, root.resolve("cache").resolve("capes"));
		this.catalogFile = root.resolve("cache").resolve("catalog.json");
		JsonFiles.read(catalogFile).ifPresent(json -> catalog = Catalog.fromJson(json));
	}

	public Catalog catalog() {
		return catalog;
	}

	public CapeTextures capes() {
		return capes;
	}

	public OwnCape ownCape() {
		return ownCape;
	}

	public boolean ownCapeLoaded() {
		return ownCapeLoaded;
	}

	public boolean busy() {
		return busy;
	}

	public void tick(Minecraft minecraft) {
		long now = System.currentTimeMillis();
		if (now >= nextCatalogRefresh) {
			nextCatalogRefresh = now + CATALOG_REFRESH_MS;
			refreshCatalog();
		}
		if (session.online() && !ownCapeRequested) {
			ownCapeRequested = true;
			refreshOwnCape(null);
		}
		if (!session.online() && session.status() != AuthSession.Status.CONNECTING) {
			ownCapeRequested = false;
		}
	}

	public void requestCatalogRefresh() {
		long now = System.currentTimeMillis();
		if (now - lastOnDemandRefresh < ON_DEMAND_REFRESH_MS) {
			return;
		}
		lastOnDemandRefresh = now;
		refreshCatalog();
	}

	public void onServerCatalogVersion(String version) {
		if (version == null || version.equals(serverCatalogVersion)) {
			return;
		}
		boolean first = serverCatalogVersion == null;
		serverCatalogVersion = version;
		if (!first || catalog.capes().isEmpty() && catalog.trails().isEmpty() && catalog.emotes().isEmpty()) {
			lastOnDemandRefresh = System.currentTimeMillis();
			refreshCatalog();
		}
	}

	public void refreshCatalog() {
		HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(PretupidInfo.apiBase() + "/cosmetics/catalog"))
			.timeout(Duration.ofSeconds(12))
			.header("User-Agent", PretupidInfo.userAgent())
			.header("Accept", "application/json");
		String etag = catalogEtag;
		if (etag != null) {
			builder.header("If-None-Match", etag);
		}
		catalogHttp.sendAsync(builder.GET().build(), HttpResponse.BodyHandlers.ofByteArray()).whenComplete((response, error) -> {
			if (error != null || response.statusCode() == 304 || response.statusCode() != 200 || response.body().length > 512 * 1024) {
				if (error != null) {
					nextCatalogRefresh = System.currentTimeMillis() + 60_000L;
				}
				return;
			}
			try {
				JsonObject json = com.google.gson.JsonParser.parseString(new String(response.body(), StandardCharsets.UTF_8)).getAsJsonObject();
				Catalog parsed = Catalog.fromJson(json);
				Minecraft.getInstance().execute(() -> {
					catalog = parsed;
					catalogEtag = response.headers().firstValue("ETag").orElse(null);
				});
				try {
					JsonFiles.write(catalogFile, json);
				} catch (IOException exception) {
					PretupidClient.LOGGER.debug("Could not cache cosmetics catalog: {}", exception.getMessage());
				}
			} catch (RuntimeException exception) {
				PretupidClient.LOGGER.debug("Ignoring malformed cosmetics catalog");
			}
		});
	}

	public RemoteProfile.CapeRef ownCapeTexture() {
		String choice = PretupidClient.get().settings().cape.get();
		if ("custom".equals(choice)) {
			OwnCape own = ownCape;
			return own == null ? null : own.texture();
		}
		if (choice.startsWith("catalog:")) {
			return catalog.cape(choice.substring(8)).map(Catalog.CapeEntry::texture).orElse(null);
		}
		return null;
	}

	public void refreshOwnCape(Consumer<String> onError) {
		session.withToken(token -> api.json("GET", "/cosmetics/cape", null, token)).whenComplete((response, error) -> Minecraft.getInstance().execute(() -> {
			if (error != null) {
				if (onError != null) {
					onError.accept(ApiClient.describe(error));
				}
				return;
			}
			ownCape = new OwnCape(RemoteProfile.cape(Json.object(response, "cape")), Json.string(response, "lastRemovalReason", 160),
				Json.bool(response, "uploadsAllowed", true));
			ownCapeLoaded = true;
		}));
	}

	public CompletableFuture<Void> uploadCape(Path file) {
		byte[] bytes;
		try {
			if (!Files.isRegularFile(file) || Files.size(file) > CapeTextures.MAX_BYTES) {
				return CompletableFuture.failedFuture(new IllegalArgumentException("Choose a PNG up to 128 KB"));
			}
			bytes = Files.readAllBytes(file);
		} catch (IOException exception) {
			return CompletableFuture.failedFuture(exception);
		}
		try (NativeImage image = NativeImage.read(bytes)) {
			if (!CapeTextures.isCapeSize(image.getWidth(), image.getHeight())) {
				return CompletableFuture.failedFuture(new IllegalArgumentException("Cape must be 64x32 (or 128x64, 256x128, 512x256)"));
			}
		} catch (IOException exception) {
			return CompletableFuture.failedFuture(new IllegalArgumentException("That file is not a valid PNG image"));
		}
		busy = true;
		return session.withToken(token -> api.send("POST", "/cosmetics/cape", bytes, "image/png", token)).thenAccept(response -> {
			RemoteProfile.CapeRef cape = RemoteProfile.cape(Json.object(response, "cape"));
			Minecraft.getInstance().execute(() -> {
				ownCape = new OwnCape(cape, null, true);
				ownCapeLoaded = true;
				PretupidClient.get().settings().cape.set("custom");
				PretupidClient.get().presence().requestHeartbeat();
			});
		}).whenComplete((ignored, error) -> busy = false);
	}

	public CompletableFuture<Void> deleteCape() {
		busy = true;
		return session.withToken(token -> api.json("DELETE", "/cosmetics/cape", null, token)).thenAccept(response -> Minecraft.getInstance().execute(() -> {
			OwnCape previous = ownCape;
			ownCape = new OwnCape(null, null, previous == null || previous.uploadsAllowed());
			if ("custom".equals(PretupidClient.get().settings().cape.get())) {
				PretupidClient.get().settings().cape.set("");
			}
			PretupidClient.get().presence().requestHeartbeat();
		})).whenComplete((ignored, error) -> busy = false);
	}

	public CompletableFuture<Void> reportCape(java.util.UUID player) {
		JsonObject body = new JsonObject();
		body.addProperty("uuid", player.toString());
		return session.withToken(token -> api.json("POST", "/cosmetics/report", body, token)).thenAccept(response -> {
		});
	}
}

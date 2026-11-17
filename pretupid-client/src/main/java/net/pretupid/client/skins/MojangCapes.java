package net.pretupid.client.skins;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.PretupidInfo;
import net.pretupid.client.cosmetic.CapeTextures;

public final class MojangCapes {
	public enum State {
		IDLE,
		LOADING,
		READY,
		NOT_SIGNED_IN,
		FAILED
	}

	public record Cape(String id, String alias, URI url, boolean active) {
	}

	private static final String PROFILE = "https://api.minecraftservices.com/minecraft/profile";
	private static final int MAX_TEXTURE_BYTES = 256 * 1024;

	private final HttpClient http = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(8))
		.followRedirects(HttpClient.Redirect.NEVER)
		.build();
	private final Map<String, Identifier> textures = new HashMap<>();
	private final Set<String> requestedTextures = new HashSet<>();
	private volatile List<Cape> capes = List.of();
	private volatile State state = State.IDLE;
	private volatile boolean busy;
	private boolean changedThisSession;
	private long generation;

	public List<Cape> capes() {
		return capes;
	}

	public State state() {
		return state;
	}

	public boolean busy() {
		return busy;
	}

	public Cape active() {
		for (Cape cape : capes) {
			if (cape.active()) {
				return cape;
			}
		}
		return null;
	}

	public boolean overridesOwnCape() {
		return changedThisSession && state == State.READY;
	}

	public Identifier texture(Cape cape) {
		if (cape == null) {
			return null;
		}
		Identifier id = textures.get(cape.id());
		if (id == null && requestedTextures.add(cape.id())) {
			download(cape);
		}
		return id;
	}

	public void refresh() {
		String token = token();
		if (token == null) {
			state = State.NOT_SIGNED_IN;
			return;
		}
		long request = ++generation;
		if (capes.isEmpty()) {
			state = State.LOADING;
		}
		send(HttpRequest.newBuilder(URI.create(PROFILE)).GET(), token).whenComplete((result, error) -> Minecraft.getInstance().execute(() -> {
			if (request == generation) {
				accept(result, error);
			}
		}));
	}

	public CompletableFuture<Void> equip(Cape cape) {
		JsonObject body = new JsonObject();
		body.addProperty("capeId", cape.id());
		return change(HttpRequest.newBuilder(URI.create(PROFILE + "/capes/active"))
			.header("Content-Type", "application/json")
			.PUT(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8)));
	}

	public CompletableFuture<Void> hide() {
		return change(HttpRequest.newBuilder(URI.create(PROFILE + "/capes/active")).DELETE());
	}

	private CompletableFuture<Void> change(HttpRequest.Builder builder) {
		String token = token();
		if (token == null) {
			state = State.NOT_SIGNED_IN;
			return CompletableFuture.failedFuture(new IllegalStateException("Sign in with a Microsoft account to change Minecraft capes"));
		}
		busy = true;
		long request = ++generation;
		CompletableFuture<Void> done = new CompletableFuture<>();
		send(builder, token).whenComplete((result, error) -> Minecraft.getInstance().execute(() -> {
			busy = false;
			if (request == generation) {
				accept(result, error);
			}
			if (error != null || result == null || result.status() != 200) {
				done.completeExceptionally(new IllegalStateException(describe(result, error)));
				return;
			}
			changedThisSession = true;
			done.complete(null);
		}));
		return done;
	}

	private void accept(Response result, Throwable error) {
		if (error != null || result == null) {
			state = capes.isEmpty() ? State.FAILED : State.READY;
			return;
		}
		if (result.status() == 401 || result.status() == 403) {
			state = State.NOT_SIGNED_IN;
			return;
		}
		List<Cape> parsed = result.status() == 200 ? parse(result.body()) : null;
		if (parsed == null) {
			state = capes.isEmpty() ? State.FAILED : State.READY;
			return;
		}
		capes = parsed;
		state = State.READY;
	}

	private static String describe(Response result, Throwable error) {
		if (error != null || result == null) {
			return "Minecraft services are unreachable right now";
		}
		return switch (result.status()) {
			case 401, 403 -> "Sign in with a Microsoft account to change Minecraft capes";
			case 429 -> "Too many cape changes, try again in a minute";
			default -> "Minecraft rejected the cape change (" + result.status() + ")";
		};
	}

	private static String token() {
		String token = Minecraft.getInstance().getUser().getAccessToken();
		return token == null || token.length() < 16 ? null : token;
	}

	private record Response(int status, String body) {
	}

	private CompletableFuture<Response> send(HttpRequest.Builder builder, String token) {
		HttpRequest request = builder
			.timeout(Duration.ofSeconds(15))
			.header("Authorization", "Bearer " + token)
			.header("User-Agent", PretupidInfo.userAgent())
			.header("Accept", "application/json")
			.build();
		return http.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
			.thenApply(response -> new Response(response.statusCode(), response.body()));
	}

	private static List<Cape> parse(String body) {
		try {
			JsonObject root = JsonParser.parseString(body).getAsJsonObject();
			List<Cape> result = new ArrayList<>();
			JsonArray array = root.has("capes") && root.get("capes").isJsonArray() ? root.getAsJsonArray("capes") : new JsonArray();
			for (JsonElement element : array) {
				if (!element.isJsonObject()) {
					continue;
				}
				JsonObject cape = element.getAsJsonObject();
				String id = string(cape, "id");
				String url = string(cape, "url");
				if (id == null || url == null || id.length() > 64) {
					continue;
				}
				URI uri = URI.create(url);
				if (!"textures.minecraft.net".equals(uri.getHost())) {
					continue;
				}
				String alias = string(cape, "alias");
				boolean active = "ACTIVE".equals(string(cape, "state"));
				result.add(new Cape(id, prettify(alias), URI.create("https://textures.minecraft.net" + uri.getRawPath()), active));
			}
			return List.copyOf(result);
		} catch (RuntimeException exception) {
			PretupidClient.LOGGER.debug("Could not parse Minecraft profile capes", exception);
			return null;
		}
	}

	private static String string(JsonObject object, String key) {
		JsonElement element = object.get(key);
		return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
	}

	private static String prettify(String alias) {
		String spaced = alias == null ? "" : alias.replace('_', ' ').trim();
		if (spaced.isEmpty()) {
			return "Cape";
		}
		if (!spaced.equals(spaced.toUpperCase(Locale.ROOT))) {
			return spaced;
		}
		StringBuilder builder = new StringBuilder();
		for (String word : spaced.toLowerCase(Locale.ROOT).split(" +")) {
			if (!builder.isEmpty()) {
				builder.append(' ');
			}
			builder.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
		}
		return builder.toString();
	}

	private void download(Cape cape) {
		HttpRequest request = HttpRequest.newBuilder(cape.url())
			.timeout(Duration.ofSeconds(15))
			.header("User-Agent", PretupidInfo.userAgent())
			.GET()
			.build();
		http.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).whenComplete((response, error) -> Minecraft.getInstance().execute(() -> {
			if (error != null || response.statusCode() != 200 || response.body().length == 0 || response.body().length > MAX_TEXTURE_BYTES) {
				return;
			}
			NativeImage image;
			try {
				image = NativeImage.read(response.body());
			} catch (IOException exception) {
				return;
			}
			if (!CapeTextures.isCapeSize(image.getWidth(), image.getHeight())) {
				image.close();
				return;
			}
			Identifier id = Identifier.fromNamespaceAndPath(PretupidClient.MOD_ID, "mojang_cape/" + cape.id().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_"));
			Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "Minecraft cape " + cape.id(), image));
			textures.put(cape.id(), id);
		}));
	}
}

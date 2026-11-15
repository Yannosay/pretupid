package net.pretupid.client.skins;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.PretupidInfo;

public final class AccountSkin {
	public record Current(Identifier texture, SkinLibrary.Variant variant, long contentHash, byte[] png) {
	}

	private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(PretupidClient.MOD_ID, "skin_preview/account_current");
	private static final int MAX_SKIN_BYTES = 256 * 1024;

	private final HttpClient http = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(8))
		.followRedirects(HttpClient.Redirect.NORMAL)
		.build();
	private Current current;
	private boolean loading;
	private long generation;

	public Current current() {
		return current;
	}

	public boolean loading() {
		return loading;
	}

	public void refresh() {
		UUID profile = Minecraft.getInstance().getUser().getProfileId();
		if (profile == null) {
			return;
		}
		long request = ++generation;
		loading = true;
		fetch(profile).whenComplete((result, error) -> Minecraft.getInstance().execute(() -> {
			if (request != generation) {
				return;
			}
			loading = false;
			if (error != null || result == null) {
				return;
			}
			install(result);
		}));
	}

	private void install(Download download) {
		NativeImage image;
		try {
			image = NativeImage.read(new ByteArrayInputStream(download.png()));
		} catch (IOException exception) {
			PretupidClient.LOGGER.debug("Account skin is not a readable PNG", exception);
			return;
		}
		if (image.getWidth() != 64 || (image.getHeight() != 64 && image.getHeight() != 32)) {
			image.close();
			return;
		}
		long hash = SkinLibrary.contentHash(image);
		Minecraft.getInstance().getTextureManager().register(TEXTURE, new DynamicTexture(() -> "Pretupid account skin", image));
		current = new Current(TEXTURE, download.variant(), hash, download.png());
	}

	public void release() {
		if (current != null) {
			Minecraft.getInstance().getTextureManager().release(TEXTURE);
			current = null;
		}
	}

	private record Download(byte[] png, SkinLibrary.Variant variant) {
	}

	private CompletableFuture<Download> fetch(UUID profile) {
		String id = profile.toString().replace("-", "");
		HttpRequest request = HttpRequest.newBuilder(URI.create("https://sessionserver.mojang.com/session/minecraft/profile/" + id))
			.timeout(Duration.ofSeconds(10))
			.header("User-Agent", PretupidInfo.userAgent())
			.GET()
			.build();
		return http.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).thenCompose(response -> {
			if (response.statusCode() != 200) {
				return CompletableFuture.completedFuture(null);
			}
			SkinRef ref = parse(response.body());
			if (ref == null) {
				return CompletableFuture.completedFuture(null);
			}
			HttpRequest texture = HttpRequest.newBuilder(ref.url())
				.timeout(Duration.ofSeconds(15))
				.header("User-Agent", PretupidInfo.userAgent())
				.GET()
				.build();
			return http.sendAsync(texture, HttpResponse.BodyHandlers.ofByteArray()).thenApply(textureResponse -> {
				byte[] body = textureResponse.body();
				if (textureResponse.statusCode() != 200 || body == null || body.length == 0 || body.length > MAX_SKIN_BYTES) {
					return null;
				}
				return new Download(body, ref.variant());
			});
		});
	}

	private record SkinRef(URI url, SkinLibrary.Variant variant) {
	}

	private static SkinRef parse(String body) {
		try {
			JsonObject root = JsonParser.parseString(body).getAsJsonObject();
			if (!root.has("properties") || !root.get("properties").isJsonArray()) {
				return null;
			}
			for (JsonElement property : root.getAsJsonArray("properties")) {
				JsonObject object = property.getAsJsonObject();
				if (!"textures".equals(object.get("name").getAsString())) {
					continue;
				}
				String decoded = new String(Base64.getDecoder().decode(object.get("value").getAsString()), StandardCharsets.UTF_8);
				JsonObject textures = JsonParser.parseString(decoded).getAsJsonObject().getAsJsonObject("textures");
				if (textures == null || !textures.has("SKIN")) {
					return null;
				}
				JsonObject skin = textures.getAsJsonObject("SKIN");
				URI url = URI.create(skin.get("url").getAsString());
				if (!"textures.minecraft.net".equals(url.getHost())) {
					return null;
				}
				URI secure = URI.create("https://textures.minecraft.net" + url.getRawPath());
				boolean slim = skin.has("metadata") && skin.getAsJsonObject("metadata").has("model")
					&& "slim".equals(skin.getAsJsonObject("metadata").get("model").getAsString().toLowerCase(Locale.ROOT));
				return new SkinRef(secure, slim ? SkinLibrary.Variant.SLIM : SkinLibrary.Variant.CLASSIC);
			}
		} catch (RuntimeException exception) {
			PretupidClient.LOGGER.debug("Could not parse account profile", exception);
		}
		return null;
	}
}

package net.pretupid.client.news;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.PretupidInfo;

public final class NewsImages {
	public enum State {
		LOADING,
		READY,
		FAILED
	}

	public record Image(State state, Identifier texture, int width, int height) {
	}

	private static final int MAX_BYTES = 4 * 1024 * 1024;
	private static final int MAX_DIMENSION = 2048;
	private static final int MAX_IMAGES = 24;
	private static final Image LOADING = new Image(State.LOADING, null, 0, 0);
	private static final Image FAILED = new Image(State.FAILED, null, 0, 0);

	private final HttpClient http = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(8))
		.followRedirects(HttpClient.Redirect.NORMAL)
		.build();
	private final Map<String, Image> images = new HashMap<>();
	private int counter;

	public Image get(String url) {
		Image existing = images.get(url);
		if (existing != null) {
			return existing;
		}
		if (images.size() >= MAX_IMAGES) {
			return FAILED;
		}
		images.put(url, LOADING);
		HttpRequest request;
		try {
			request = HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofSeconds(15))
				.header("User-Agent", PretupidInfo.userAgent())
				.header("Accept", "image/png")
				.GET()
				.build();
		} catch (IllegalArgumentException exception) {
			images.put(url, FAILED);
			return FAILED;
		}
		http.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).whenComplete((response, error) -> {
			byte[] body = error == null && response.statusCode() == 200 ? response.body() : null;
			Minecraft.getInstance().execute(() -> install(url, body));
		});
		return LOADING;
	}

	private void install(String url, byte[] body) {
		if (!images.containsKey(url)) {
			return;
		}
		if (body == null || body.length == 0 || body.length > MAX_BYTES) {
			images.put(url, FAILED);
			return;
		}
		NativeImage image;
		try {
			image = NativeImage.read(new ByteArrayInputStream(body));
		} catch (IOException | RuntimeException exception) {
			PretupidClient.LOGGER.debug("News image {} is not a readable PNG", url, exception);
			images.put(url, FAILED);
			return;
		}
		if (image.getWidth() > MAX_DIMENSION || image.getHeight() > MAX_DIMENSION) {
			image.close();
			images.put(url, FAILED);
			return;
		}
		Identifier texture = Identifier.fromNamespaceAndPath(PretupidClient.MOD_ID, "news_image/" + (counter++) + "_" + Integer.toHexString(url.toLowerCase(Locale.ROOT).hashCode()));
		Minecraft.getInstance().getTextureManager().register(texture, new DynamicTexture(() -> "Pretupid news image", image));
		images.put(url, new Image(State.READY, texture, image.getWidth(), image.getHeight()));
	}

	public void release() {
		for (Image image : images.values()) {
			if (image.texture() != null) {
				Minecraft.getInstance().getTextureManager().release(image.texture());
			}
		}
		images.clear();
	}
}

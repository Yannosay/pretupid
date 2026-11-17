package net.pretupid.client.cosmetic;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.network.ApiClient;
import net.pretupid.client.network.RemoteProfile;

public final class WearableTextures {
	private static final int MAX_BYTES = 128 * 1024;
	private static final long RETRY_MS = 5 * 60_000L;

	private final ApiClient api;
	private final Path cacheDirectory;
	private final Map<String, NativeImage> loaded = new ConcurrentHashMap<>();
	private final Map<String, Long> failed = new ConcurrentHashMap<>();
	private final Map<String, Boolean> loading = new ConcurrentHashMap<>();
	private Runnable onLoaded = () -> {
	};

	public WearableTextures(ApiClient api, Path cacheDirectory) {
		this.api = api;
		this.cacheDirectory = cacheDirectory;
	}

	public void onLoaded(Runnable listener) {
		this.onLoaded = listener;
	}

	public static boolean isTextureSize(int width, int height) {
		return isSide(width) && isSide(height);
	}

	private static boolean isSide(int value) {
		return value == 16 || value == 32 || value == 64 || value == 128 || value == 256;
	}

	public NativeImage get(RemoteProfile.CapeRef texture) {
		if (texture == null) {
			return null;
		}
		NativeImage image = loaded.get(texture.sha256());
		if (image != null) {
			return image;
		}
		Long failedAt = failed.get(texture.sha256());
		if (failedAt != null && System.currentTimeMillis() - failedAt < RETRY_MS) {
			return null;
		}
		if (loading.putIfAbsent(texture.sha256(), Boolean.TRUE) == null) {
			load(texture);
		}
		return null;
	}

	private void load(RemoteProfile.CapeRef texture) {
		Path cached = cacheDirectory.resolve(texture.sha256() + ".png");
		CompletableFuture.supplyAsync(() -> readCached(cached, texture.sha256()))
			.thenCompose(bytes -> bytes != null ? CompletableFuture.completedFuture(bytes) : api.download(texture.url(), MAX_BYTES).thenApply(downloaded -> {
				if (!CapeTextures.sha256(downloaded).equals(texture.sha256())) {
					throw new IllegalStateException("Texture checksum mismatch");
				}
				writeCache(cached, downloaded);
				return downloaded;
			}))
			.whenComplete((bytes, error) -> Minecraft.getInstance().execute(() -> {
				loading.remove(texture.sha256());
				NativeImage image = error == null ? decode(bytes) : null;
				if (image == null) {
					failed.put(texture.sha256(), System.currentTimeMillis());
					PretupidClient.LOGGER.debug("Wearable texture {} unavailable", texture.sha256());
					return;
				}
				failed.remove(texture.sha256());
				loaded.put(texture.sha256(), image);
				onLoaded.run();
			}));
	}

	private static NativeImage decode(byte[] bytes) {
		try {
			NativeImage image = NativeImage.read(bytes);
			if (!isTextureSize(image.getWidth(), image.getHeight())) {
				image.close();
				return null;
			}
			return image;
		} catch (IOException exception) {
			return null;
		}
	}

	private static byte[] readCached(Path path, String sha256) {
		try {
			if (!Files.isRegularFile(path) || Files.size(path) > MAX_BYTES) {
				return null;
			}
			byte[] bytes = Files.readAllBytes(path);
			return CapeTextures.sha256(bytes).equals(sha256) ? bytes : null;
		} catch (IOException exception) {
			return null;
		}
	}

	private static void writeCache(Path path, byte[] bytes) {
		try {
			Files.createDirectories(path.getParent());
			Path temp = path.resolveSibling(path.getFileName() + ".tmp");
			Files.write(temp, bytes);
			Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException exception) {
			PretupidClient.LOGGER.debug("Could not cache wearable texture: {}", exception.getMessage());
		}
	}
}

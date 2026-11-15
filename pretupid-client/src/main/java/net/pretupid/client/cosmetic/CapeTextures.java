package net.pretupid.client.cosmetic;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.network.ApiClient;
import net.pretupid.client.network.RemoteProfile;

public final class CapeTextures {
	public static final int MAX_BYTES = 128 * 1024;
	private static final long RETRY_MS = 5 * 60_000L;

	private final ApiClient api;
	private final Path cacheDirectory;
	private final Map<String, Identifier> loaded = new ConcurrentHashMap<>();
	private final Map<String, Long> failed = new ConcurrentHashMap<>();
	private final Map<String, Boolean> loading = new ConcurrentHashMap<>();

	public CapeTextures(ApiClient api, Path cacheDirectory) {
		this.api = api;
		this.cacheDirectory = cacheDirectory;
	}

	public static boolean isCapeSize(int width, int height) {
		return (width == 64 || width == 128 || width == 256 || width == 512) && height * 2 == width;
	}

	public Identifier get(RemoteProfile.CapeRef cape) {
		if (cape == null) {
			return null;
		}
		Identifier id = loaded.get(cape.sha256());
		if (id != null) {
			return id;
		}
		Long failedAt = failed.get(cape.sha256());
		if (failedAt != null && System.currentTimeMillis() - failedAt < RETRY_MS) {
			return null;
		}
		if (loading.putIfAbsent(cape.sha256(), Boolean.TRUE) == null) {
			load(cape);
		}
		return null;
	}

	private void load(RemoteProfile.CapeRef cape) {
		Path cached = cacheDirectory.resolve(cape.sha256() + ".png");
		CompletableFuture.supplyAsync(() -> readCached(cached, cape.sha256()))
			.thenCompose(bytes -> bytes != null ? CompletableFuture.completedFuture(bytes) : api.download(cape.url(), MAX_BYTES).thenApply(downloaded -> {
				if (!sha256(downloaded).equals(cape.sha256())) {
					throw new IllegalStateException("Cape checksum mismatch");
				}
				writeCache(cached, downloaded);
				return downloaded;
			}))
			.whenComplete((bytes, error) -> Minecraft.getInstance().execute(() -> {
				loading.remove(cape.sha256());
				if (error != null) {
					failed.put(cape.sha256(), System.currentTimeMillis());
					PretupidClient.LOGGER.debug("Cape {} unavailable: {}", cape.sha256(), ApiClient.describe(error));
					return;
				}
				register(cape.sha256(), bytes);
			}));
	}

	public Identifier register(String sha256, byte[] bytes) {
		NativeImage image;
		try {
			image = NativeImage.read(bytes);
		} catch (IOException exception) {
			failed.put(sha256, System.currentTimeMillis());
			return null;
		}
		if (!isCapeSize(image.getWidth(), image.getHeight())) {
			image.close();
			failed.put(sha256, System.currentTimeMillis());
			return null;
		}
		Identifier id = Identifier.fromNamespaceAndPath(PretupidClient.MOD_ID, "capes/" + sha256);
		Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "pretupid cape " + sha256, image));
		loaded.put(sha256, id);
		failed.remove(sha256);
		return id;
	}

	public void release(String sha256) {
		Identifier id = loaded.remove(sha256);
		if (id != null) {
			Minecraft.getInstance().getTextureManager().release(id);
		}
	}

	private static byte[] readCached(Path path, String sha256) {
		try {
			if (!Files.isRegularFile(path) || Files.size(path) > MAX_BYTES) {
				return null;
			}
			byte[] bytes = Files.readAllBytes(path);
			return sha256(bytes).equals(sha256) ? bytes : null;
		} catch (IOException exception) {
			return null;
		}
	}

	private static void writeCache(Path path, byte[] bytes) {
		try {
			Files.createDirectories(path.getParent());
			Path temp = path.resolveSibling(path.getFileName() + ".tmp");
			Files.write(temp, bytes);
			Files.move(temp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException exception) {
			PretupidClient.LOGGER.debug("Could not cache cape texture: {}", exception.getMessage());
		}
	}

	public static String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException(exception);
		}
	}
}

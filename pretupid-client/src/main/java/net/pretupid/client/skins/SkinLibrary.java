package net.pretupid.client.skins;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.config.JsonFiles;

public final class SkinLibrary {
	public enum Variant {
		CLASSIC,
		SLIM
	}

	public record Skin(String name, Path file, Variant variant, Identifier texture, int textureHeight) {
	}

	public enum ImportResult {
		IMPORTED,
		INVALID,
		FAILED
	}

	private static final long MAX_FILE_BYTES = 256 * 1024;
	private static final int MAX_SKINS = 128;

	private final Path directory;
	private final Path metadataFile;
	private final Map<String, Variant> variants = new HashMap<>();
	private final Map<String, Identifier> textures = new HashMap<>();
	private List<Skin> skins = List.of();
	private String activeSkin;

	public SkinLibrary(Path configDirectory) {
		this.directory = configDirectory.resolve("skins");
		this.metadataFile = directory.resolve("skins.json");
	}

	public Path directory() {
		return directory;
	}

	public List<Skin> skins() {
		return skins;
	}

	public String activeSkin() {
		return activeSkin;
	}

	public void refresh() {
		readMetadata();
		release();
		List<Skin> result = new ArrayList<>();
		if (Files.isDirectory(directory)) {
			try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.png")) {
				for (Path path : stream) {
					if (result.size() >= MAX_SKINS) {
						break;
					}
					load(path).ifPresent(result::add);
				}
			} catch (IOException exception) {
				PretupidClient.LOGGER.warn("Could not list skins", exception);
			}
		}
		result.sort(Comparator.comparing(skin -> skin.name().toLowerCase(Locale.ROOT)));
		skins = List.copyOf(result);
	}

	private Optional<Skin> load(Path path) {
		String name = baseName(path);
		try {
			if (Files.size(path) > MAX_FILE_BYTES) {
				return Optional.empty();
			}
			NativeImage image;
			try (InputStream input = Files.newInputStream(path)) {
				image = NativeImage.read(input);
			}
			if (!validSize(image.getWidth(), image.getHeight())) {
				image.close();
				return Optional.empty();
			}
			int height = image.getHeight();
			Identifier location = Identifier.fromNamespaceAndPath(PretupidClient.MOD_ID, "skin_preview/" + sanitize(name));
			Minecraft.getInstance().getTextureManager().register(location, new DynamicTexture(() -> "Pretupid skin " + name, image));
			textures.put(name, location);
			return Optional.of(new Skin(name, path, variants.getOrDefault(name, Variant.CLASSIC), location, height));
		} catch (IOException | RuntimeException exception) {
			PretupidClient.LOGGER.debug("Skipping unreadable skin {}", path, exception);
			return Optional.empty();
		}
	}

	public void release() {
		for (Identifier location : textures.values()) {
			Minecraft.getInstance().getTextureManager().release(location);
		}
		textures.clear();
	}

	public ImportResult importFile(Path source) {
		String fileName = source.getFileName().toString();
		if (!fileName.toLowerCase(Locale.ROOT).endsWith(".png") || !Files.isRegularFile(source)) {
			return ImportResult.INVALID;
		}
		try {
			if (Files.size(source) > MAX_FILE_BYTES) {
				return ImportResult.INVALID;
			}
			try (InputStream input = Files.newInputStream(source); NativeImage image = NativeImage.read(input)) {
				if (!validSize(image.getWidth(), image.getHeight())) {
					return ImportResult.INVALID;
				}
			}
			Files.createDirectories(directory);
			String base = sanitize(baseName(source));
			Path target = directory.resolve(base + ".png");
			int counter = 2;
			while (Files.exists(target)) {
				target = directory.resolve(base + "-" + counter++ + ".png");
			}
			Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
			return ImportResult.IMPORTED;
		} catch (IOException | RuntimeException exception) {
			PretupidClient.LOGGER.warn("Could not import skin {}", source, exception);
			return ImportResult.FAILED;
		}
	}

	public boolean delete(Skin skin) {
		try {
			Files.deleteIfExists(skin.file());
			variants.remove(skin.name());
			if (skin.name().equals(activeSkin)) {
				activeSkin = null;
			}
			writeMetadata();
			refresh();
			return true;
		} catch (IOException exception) {
			PretupidClient.LOGGER.warn("Could not delete skin {}", skin.file(), exception);
			return false;
		}
	}

	public void setVariant(Skin skin, Variant variant) {
		variants.put(skin.name(), variant);
		writeMetadata();
		List<Skin> updated = new ArrayList<>(skins.size());
		for (Skin existing : skins) {
			updated.add(existing.name().equals(skin.name())
				? new Skin(existing.name(), existing.file(), variant, existing.texture(), existing.textureHeight())
				: existing);
		}
		skins = List.copyOf(updated);
	}

	public void markActive(Skin skin) {
		activeSkin = skin.name();
		writeMetadata();
	}

	private void readMetadata() {
		variants.clear();
		activeSkin = null;
		JsonFiles.read(metadataFile).ifPresent(root -> {
			if (root.has("variants") && root.get("variants").isJsonObject()) {
				for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("variants").entrySet()) {
					if (entry.getValue().isJsonPrimitive()) {
						variants.put(entry.getKey(), "SLIM".equalsIgnoreCase(entry.getValue().getAsString()) ? Variant.SLIM : Variant.CLASSIC);
					}
				}
			}
			if (root.has("active") && root.get("active").isJsonPrimitive()) {
				activeSkin = root.get("active").getAsString();
			}
		});
	}

	private void writeMetadata() {
		JsonObject root = new JsonObject();
		JsonObject variantObject = new JsonObject();
		variants.forEach((name, variant) -> variantObject.addProperty(name, variant.name()));
		root.add("variants", variantObject);
		if (activeSkin != null) {
			root.addProperty("active", activeSkin);
		}
		try {
			JsonFiles.write(metadataFile, root);
		} catch (IOException exception) {
			PretupidClient.LOGGER.warn("Could not save skin metadata", exception);
		}
	}

	private static boolean validSize(int width, int height) {
		return width == 64 && (height == 64 || height == 32);
	}

	private static String baseName(Path path) {
		String name = path.getFileName().toString();
		int dot = name.lastIndexOf('.');
		return dot > 0 ? name.substring(0, dot) : name;
	}

	private static String sanitize(String name) {
		StringBuilder builder = new StringBuilder();
		for (char c : name.toLowerCase(Locale.ROOT).toCharArray()) {
			if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '_') {
				builder.append(c);
			} else if (c == ' ' || c == '.') {
				builder.append('_');
			}
			if (builder.length() >= 48) {
				break;
			}
		}
		return builder.isEmpty() ? "skin" : builder.toString();
	}
}

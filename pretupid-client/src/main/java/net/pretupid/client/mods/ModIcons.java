package net.pretupid.client.mods;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.pretupid.client.PretupidClient;

public final class ModIcons {
	private static final int MAX_DIMENSION = 512;

	private final Map<String, Optional<Icon>> cache = new HashMap<>();

	public Optional<Icon> icon(ModEntry entry) {
		return cache.computeIfAbsent(entry.id(), id -> load(entry));
	}

	private Optional<Icon> load(ModEntry entry) {
		Optional<byte[]> bytes = entry.icon().get();
		if (bytes.isEmpty()) {
			return Optional.empty();
		}
		try {
			NativeImage image = NativeImage.read(new ByteArrayInputStream(bytes.get()));
			if (image.getWidth() > MAX_DIMENSION || image.getHeight() > MAX_DIMENSION || image.getWidth() == 0 || image.getHeight() == 0) {
				image.close();
				return Optional.empty();
			}
			Identifier location = Identifier.fromNamespaceAndPath(PretupidClient.MOD_ID, "mod_icon/" + sanitize(entry.id()));
			int width = image.getWidth();
			int height = image.getHeight();
			DynamicTexture texture = new DynamicTexture(() -> "Pretupid mod icon " + entry.id(), image);
			Minecraft.getInstance().getTextureManager().register(location, texture);
			return Optional.of(new Icon(location, width, height));
		} catch (IOException | RuntimeException exception) {
			PretupidClient.LOGGER.debug("Could not load icon for {}", entry.id(), exception);
			return Optional.empty();
		}
	}

	public void release() {
		for (Optional<Icon> icon : cache.values()) {
			icon.ifPresent(value -> Minecraft.getInstance().getTextureManager().release(value.location()));
		}
		cache.clear();
	}

	private static String sanitize(String id) {
		StringBuilder builder = new StringBuilder();
		for (char c : id.toLowerCase(Locale.ROOT).toCharArray()) {
			builder.append((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.' ? c : '_');
		}
		return builder.toString();
	}

	public record Icon(Identifier location, int width, int height) {
	}
}

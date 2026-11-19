package net.pretupid.client.nametag;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.network.Json;

public final class NametagPresets {
	private static final String BUNDLED = "/assets/pretupid/nametags/defaults.json";
	private static List<Preset> presets;

	private NametagPresets() {
	}

	public record Preset(String id, String name, String description, NametagStyle style) {
		public boolean matches(NametagStyle other) {
			return style.animation() == other.animation()
				&& style.effect() == other.effect()
				&& style.bold() == other.bold()
				&& (!style.animation().usesPrimary() || (style.primary() | 0xFF000000) == (other.primary() | 0xFF000000))
				&& (!style.animation().usesSecondary() || (style.secondary() | 0xFF000000) == (other.secondary() | 0xFF000000))
				&& (!(style.animation().animates() || style.effect().moves()) || Math.abs(style.speed() - other.speed()) < 0.05f);
		}
	}

	public static synchronized List<Preset> all() {
		if (presets == null) {
			presets = load();
		}
		return presets;
	}

	public static Optional<Preset> find(String id) {
		return all().stream().filter(preset -> preset.id().equals(id)).findFirst();
	}

	public static Optional<Preset> matching(NametagStyle style) {
		return all().stream().filter(preset -> preset.matches(style)).findFirst();
	}

	private static List<Preset> load() {
		List<Preset> loaded = new ArrayList<>();
		try (InputStream stream = NametagPresets.class.getResourceAsStream(BUNDLED)) {
			if (stream == null) {
				return List.of();
			}
			JsonElement parsed = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
			if (!parsed.isJsonArray()) {
				return List.of();
			}
			for (JsonElement element : parsed.getAsJsonArray()) {
				if (!element.isJsonObject()) {
					continue;
				}
				JsonObject entry = element.getAsJsonObject();
				String id = Json.string(entry, "id");
				if (id == null || id.isBlank()) {
					continue;
				}
				String name = Json.string(entry, "name");
				String description = Json.string(entry, "description");
				NametagStyle.fromJson(entry).ifPresent(style -> loaded.add(new Preset(id, name == null ? id : name, description == null ? "" : description, style)));
			}
		} catch (IOException | RuntimeException exception) {
			PretupidClient.LOGGER.warn("Could not read the bundled nametag presets", exception);
		}
		return Collections.unmodifiableList(loaded);
	}
}

package net.pretupid.client.cosmetic;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.network.Json;
import net.pretupid.client.nametag.NametagStyle;
import net.pretupid.client.network.RemoteProfile;

public record Catalog(long version, List<CapeEntry> capes, List<TrailEntry> trails, List<EmoteEntry> emotes, List<WearableEntry> wearables, List<NametagEntry> nametags) {
	private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9_-]{0,31}");
	private static final String BUNDLED_WEARABLES = "/assets/pretupid/wearables/defaults.json";
	private static final String BUNDLED_NAMETAGS = "/assets/pretupid/nametags/defaults.json";
	public static final List<WearableEntry> DEFAULT_WEARABLES = loadDefaultWearables();
	public static final List<NametagEntry> DEFAULT_NAMETAGS = loadDefaultNametags();
	public static final Catalog EMPTY = new Catalog(0L, List.of(), List.of(), List.of(), DEFAULT_WEARABLES, DEFAULT_NAMETAGS);

	public enum WearableKind {
		HAT,
		BACK,
		PET;

		public String id() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}

		static WearableKind of(String value) {
			for (WearableKind kind : values()) {
				if (kind.id().equals(value)) {
					return kind;
				}
			}
			return null;
		}
	}

	public record WearableEntry(String id, WearableKind kind, String name, String description, JsonObject model, RemoteProfile.CapeRef texture) {
	}

	public record CapeEntry(String id, String name, String description, RemoteProfile.CapeRef texture) {
	}

	public record TrailEntry(String id, String name, String description, TrailDefinition definition) {
	}

	public record EmoteEntry(String id, String name, String description, EmoteAnimation animation) {
	}

	public record NametagEntry(String id, String name, String description, NametagStyle style) {
	}

	public static Catalog fromJson(JsonObject object) {
		List<CapeEntry> capes = new ArrayList<>();
		for (JsonElement element : Json.array(object, "capes")) {
			JsonObject entry = element.isJsonObject() ? element.getAsJsonObject() : null;
			String id = id(entry);
			RemoteProfile.CapeRef texture = RemoteProfile.cape(entry);
			if (id != null && texture != null) {
				capes.add(new CapeEntry(id, name(entry), description(entry), texture));
			}
		}
		List<TrailEntry> trails = new ArrayList<>();
		for (JsonElement element : Json.array(object, "trails")) {
			JsonObject entry = element.isJsonObject() ? element.getAsJsonObject() : null;
			String id = id(entry);
			if (id != null) {
				trails.add(new TrailEntry(id, name(entry), description(entry), TrailDefinition.fromJson(entry)));
			}
		}
		List<EmoteEntry> emotes = new ArrayList<>();
		for (JsonElement element : Json.array(object, "emotes")) {
			JsonObject entry = element.isJsonObject() ? element.getAsJsonObject() : null;
			String id = id(entry);
			JsonObject animation = Json.object(entry, "animation");
			if (id != null && animation != null) {
				emotes.add(new EmoteEntry(id, name(entry), description(entry), EmoteAnimation.fromJson(animation)));
			}
		}
		List<WearableEntry> wearables = object.has("wearables") ? wearables(Json.array(object, "wearables")) : DEFAULT_WEARABLES;
		List<NametagEntry> nametags = object.has("nametags") ? nametags(Json.array(object, "nametags")) : DEFAULT_NAMETAGS;
		return new Catalog(Json.number(object, "version", 0L), Collections.unmodifiableList(capes), Collections.unmodifiableList(trails), Collections.unmodifiableList(emotes),
			wearables, nametags);
	}

	private static List<NametagEntry> nametags(com.google.gson.JsonArray array) {
		List<NametagEntry> nametags = new ArrayList<>();
		for (JsonElement element : array) {
			JsonObject entry = element.isJsonObject() ? element.getAsJsonObject() : null;
			String id = id(entry);
			if (id == null) {
				continue;
			}
			NametagStyle.fromJson(entry)
				.filter(NametagStyle::decoratesName)
				.ifPresent(style -> nametags.add(new NametagEntry(id, name(entry), description(entry), style)));
		}
		return Collections.unmodifiableList(nametags);
	}

	private static List<NametagEntry> loadDefaultNametags() {
		try (var stream = Catalog.class.getResourceAsStream(BUNDLED_NAMETAGS)) {
			if (stream == null) {
				return List.of();
			}
			var parsed = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8));
			return parsed.isJsonArray() ? nametags(parsed.getAsJsonArray()) : List.of();
		} catch (java.io.IOException | RuntimeException exception) {
			PretupidClient.LOGGER.warn("Could not read the bundled nametag styles", exception);
			return List.of();
		}
	}

	private static List<WearableEntry> wearables(com.google.gson.JsonArray array) {
		List<WearableEntry> wearables = new ArrayList<>();
		for (JsonElement element : array) {
			JsonObject entry = element.isJsonObject() ? element.getAsJsonObject() : null;
			String id = id(entry);
			WearableKind kind = WearableKind.of(Json.string(entry, "kind"));
			JsonObject model = Json.object(entry, "model");
			if (id != null && kind != null && model != null) {
				wearables.add(new WearableEntry(id, kind, name(entry), description(entry), model, RemoteProfile.cape(Json.object(entry, "texture"))));
			}
		}
		return Collections.unmodifiableList(wearables);
	}

	private static List<WearableEntry> loadDefaultWearables() {
		try (var stream = Catalog.class.getResourceAsStream(BUNDLED_WEARABLES)) {
			if (stream == null) {
				return List.of();
			}
			var parsed = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8));
			return parsed.isJsonArray() ? wearables(parsed.getAsJsonArray()) : List.of();
		} catch (java.io.IOException | RuntimeException exception) {
			PretupidClient.LOGGER.warn("Could not read the bundled wearables", exception);
			return List.of();
		}
	}

	private static String id(JsonObject entry) {
		String id = Json.string(entry, "id");
		return id != null && ID.matcher(id).matches() ? id : null;
	}

	private static String name(JsonObject entry) {
		String name = Json.string(entry, "name", 40);
		return name == null || name.isBlank() ? Json.string(entry, "id") : name;
	}

	private static String description(JsonObject entry) {
		String description = Json.string(entry, "description", 160);
		return description == null ? "" : description;
	}

	public Optional<CapeEntry> cape(String id) {
		return capes.stream().filter(entry -> entry.id().equals(id)).findFirst();
	}

	public Optional<TrailEntry> trail(String id) {
		return trails.stream().filter(entry -> entry.id().equals(id)).findFirst();
	}

	public Optional<NametagEntry> nametag(String id) {
		return nametags.stream().filter(entry -> entry.id().equals(id)).findFirst();
	}

	public Optional<EmoteEntry> emote(String id) {
		return emotes.stream().filter(entry -> entry.id().equals(id)).findFirst();
	}

	public Optional<WearableEntry> wearable(WearableKind kind, String id) {
		if (id == null) {
			return Optional.empty();
		}
		return wearables.stream().filter(entry -> entry.kind() == kind && entry.id().equals(id)).findFirst();
	}

	public List<WearableEntry> wearables(WearableKind kind) {
		return wearables.stream().filter(entry -> entry.kind() == kind).toList();
	}
}

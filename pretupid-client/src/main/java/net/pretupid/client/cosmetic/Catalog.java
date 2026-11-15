package net.pretupid.client.cosmetic;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import net.pretupid.client.network.Json;
import net.pretupid.client.network.RemoteProfile;

public record Catalog(long version, List<CapeEntry> capes, List<TrailEntry> trails, List<EmoteEntry> emotes) {
	private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9_-]{0,31}");
	public static final Catalog EMPTY = new Catalog(0L, List.of(), List.of(), List.of());

	public record CapeEntry(String id, String name, String description, RemoteProfile.CapeRef texture) {
	}

	public record TrailEntry(String id, String name, String description, TrailDefinition definition) {
	}

	public record EmoteEntry(String id, String name, String description, EmoteAnimation animation) {
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
		return new Catalog(Json.number(object, "version", 0L), Collections.unmodifiableList(capes), Collections.unmodifiableList(trails), Collections.unmodifiableList(emotes));
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

	public Optional<EmoteEntry> emote(String id) {
		return emotes.stream().filter(entry -> entry.id().equals(id)).findFirst();
	}
}

package net.pretupid.client.music;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.pretupid.client.PretupidClient;

public final class MusicCatalog {
	private static final Identifier SOUNDS = Identifier.withDefaultNamespace("sounds.json");
	private static final Identifier TITLES = Identifier.fromNamespaceAndPath(PretupidClient.MOD_ID, "music/titles.json");

	private List<MusicTrack> tracks = List.of();
	private Map<Identifier, MusicTrack> byFile = Map.of();
	private Map<String, String> titles = Map.of();
	private boolean loaded;

	public void invalidate() {
		loaded = false;
	}

	public List<MusicTrack> tracks(Minecraft minecraft) {
		ensureLoaded(minecraft);
		return tracks;
	}

	public MusicTrack describe(Minecraft minecraft, Identifier file) {
		ensureLoaded(minecraft);
		MusicTrack known = byFile.get(file);
		if (known != null) {
			return known;
		}
		String path = file.getPath();
		if (!path.startsWith("music/") && !path.startsWith("records/")) {
			return null;
		}
		return create(file);
	}

	private void ensureLoaded(Minecraft minecraft) {
		if (loaded) {
			return;
		}
		loaded = true;
		titles = readTitles(minecraft);
		Map<Identifier, MusicTrack> found = new LinkedHashMap<>();
		for (Resource resource : minecraft.getResourceManager().getResourceStack(SOUNDS)) {
			try (BufferedReader reader = resource.openAsReader()) {
				JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
				for (Map.Entry<String, JsonElement> event : root.entrySet()) {
					if ((event.getKey().startsWith("music.") || event.getKey().startsWith("music_disc.")) && event.getValue().isJsonObject()) {
						collect(event.getValue().getAsJsonObject(), found);
					}
				}
			} catch (IOException | RuntimeException exception) {
				PretupidClient.LOGGER.warn("Could not read music list: {}", exception.getMessage());
			}
		}
		List<MusicTrack> sorted = new ArrayList<>(found.values());
		sorted.sort(Comparator.comparing((MusicTrack track) -> track.title().toLowerCase(Locale.ROOT)).thenComparing(MusicTrack::artist));
		tracks = List.copyOf(sorted);
		byFile = new HashMap<>(found);
	}

	private void collect(JsonObject event, Map<Identifier, MusicTrack> found) {
		JsonElement sounds = event.get("sounds");
		if (sounds == null || !sounds.isJsonArray()) {
			return;
		}
		for (JsonElement entry : sounds.getAsJsonArray()) {
			String name = null;
			if (entry.isJsonPrimitive()) {
				name = entry.getAsString();
			} else if (entry.isJsonObject()) {
				JsonObject object = entry.getAsJsonObject();
				boolean file = !object.has("type") || object.get("type").getAsString().equals("file");
				if (file && object.has("name")) {
					name = object.get("name").getAsString();
				}
			}
			if (name == null) {
				continue;
			}
			Identifier id = Identifier.tryParse(name);
			if (id != null) {
				found.computeIfAbsent(id, this::create);
			}
		}
	}

	private MusicTrack create(Identifier file) {
		String path = file.getPath();
		String base = path.substring(path.lastIndexOf('/') + 1);
		String label = titles.get(base);
		if (label != null) {
			int split = label.indexOf(" - ");
			if (split > 0) {
				return new MusicTrack(file, label.substring(split + 3), label.substring(0, split));
			}
			return new MusicTrack(file, label, "");
		}
		StringBuilder title = new StringBuilder();
		for (String word : base.split("_")) {
			if (word.isEmpty()) {
				continue;
			}
			if (!title.isEmpty()) {
				title.append(' ');
			}
			title.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
		}
		return new MusicTrack(file, title.toString(), "");
	}

	private static Map<String, String> readTitles(Minecraft minecraft) {
		Map<String, String> result = new HashMap<>();
		var resource = minecraft.getResourceManager().getResource(TITLES);
		if (resource.isEmpty()) {
			return result;
		}
		try (BufferedReader reader = resource.get().openAsReader()) {
			for (Map.Entry<String, JsonElement> entry : JsonParser.parseReader(reader).getAsJsonObject().entrySet()) {
				result.put(entry.getKey(), entry.getValue().getAsString());
			}
		} catch (IOException | RuntimeException exception) {
			PretupidClient.LOGGER.warn("Could not read music titles: {}", exception.getMessage());
		}
		return result;
	}
}

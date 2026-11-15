package net.pretupid.client.cosmetic;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.pretupid.client.network.Json;
import net.pretupid.client.util.Colors;

public record TrailDefinition(String particle, int[] colors, int density, Pattern pattern, float size) {
	public enum Pattern {
		STREAM,
		SPIRAL,
		RING,
		HELIX
	}

	public static TrailDefinition fromJson(JsonObject object) {
		String particle = Json.string(object, "particle", 32);
		List<Integer> colors = new ArrayList<>();
		for (JsonElement element : Json.array(object, "colors")) {
			if (element.isJsonPrimitive() && colors.size() < 4) {
				colors.add(Colors.parseHex(element.getAsString(), 0xFFFFFFFF) | 0xFF000000);
			}
		}
		if (colors.isEmpty()) {
			colors.add(0xFFFFFFFF);
		}
		Pattern pattern = Pattern.STREAM;
		String patternName = Json.string(object, "pattern", 16);
		if (patternName != null) {
			for (Pattern candidate : Pattern.values()) {
				if (candidate.name().equals(patternName.toUpperCase(Locale.ROOT))) {
					pattern = candidate;
				}
			}
		}
		return new TrailDefinition(
			particle == null ? "end_rod" : particle,
			colors.stream().mapToInt(Integer::intValue).toArray(),
			(int) Math.clamp(Json.number(object, "density", 2), 1, 5),
			pattern,
			(float) Math.clamp(Json.decimal(object, "size", 1.0), 0.5, 2.0)
		);
	}
}

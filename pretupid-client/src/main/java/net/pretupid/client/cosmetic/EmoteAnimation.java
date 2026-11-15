package net.pretupid.client.cosmetic;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.pretupid.client.network.Json;

public record EmoteAnimation(float durationSeconds, boolean loop, boolean smooth, Map<Part, Keyframe[]> parts) {
	public enum Part {
		HEAD("head"),
		BODY("body"),
		RIGHT_ARM("right_arm"),
		LEFT_ARM("left_arm"),
		RIGHT_LEG("right_leg"),
		LEFT_LEG("left_leg");

		private final String key;

		Part(String key) {
			this.key = key;
		}
	}

	public record Keyframe(float t, float x, float y, float z) {
	}

	public static EmoteAnimation fromJson(JsonObject object) {
		Map<Part, Keyframe[]> parts = new EnumMap<>(Part.class);
		JsonObject partsJson = Json.object(object, "parts");
		if (partsJson != null) {
			for (Part part : Part.values()) {
				List<Keyframe> frames = new ArrayList<>();
				for (JsonElement element : Json.array(partsJson, part.key)) {
					if (!element.isJsonObject() || frames.size() >= 64) {
						continue;
					}
					JsonObject frame = element.getAsJsonObject();
					frames.add(new Keyframe(
						(float) Math.clamp(Json.decimal(frame, "t", 0), 0, 1),
						(float) Math.toRadians(Math.clamp(Json.decimal(frame, "x", 0), -360, 360)),
						(float) Math.toRadians(Math.clamp(Json.decimal(frame, "y", 0), -360, 360)),
						(float) Math.toRadians(Math.clamp(Json.decimal(frame, "z", 0), -360, 360))
					));
				}
				if (!frames.isEmpty()) {
					frames.sort((a, b) -> Float.compare(a.t(), b.t()));
					parts.put(part, frames.toArray(Keyframe[]::new));
				}
			}
		}
		return new EmoteAnimation(
			(float) Math.clamp(Json.decimal(object, "duration", 1.5), 0.25, 12),
			Json.bool(object, "loop", false),
			!"linear".equals(Json.string(object, "ease")),
			parts
		);
	}

	public float[] sample(Part part, float progress) {
		Keyframe[] frames = parts.get(part);
		if (frames == null) {
			return null;
		}
		if (progress <= frames[0].t()) {
			return new float[] {frames[0].x(), frames[0].y(), frames[0].z()};
		}
		for (int i = 1; i < frames.length; i++) {
			Keyframe next = frames[i];
			if (progress <= next.t()) {
				Keyframe previous = frames[i - 1];
				float span = Math.max(1e-4f, next.t() - previous.t());
				float t = (progress - previous.t()) / span;
				if (smooth) {
					t = t * t * (3f - 2f * t);
				}
				return new float[] {
					previous.x() + (next.x() - previous.x()) * t,
					previous.y() + (next.y() - previous.y()) * t,
					previous.z() + (next.z() - previous.z()) * t
				};
			}
		}
		Keyframe last = frames[frames.length - 1];
		return new float[] {last.x(), last.y(), last.z()};
	}
}

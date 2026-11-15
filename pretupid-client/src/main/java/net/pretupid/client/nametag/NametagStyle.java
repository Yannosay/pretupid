package net.pretupid.client.nametag;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Optional;
import net.pretupid.client.network.Json;
import net.pretupid.client.util.Colors;

public record NametagStyle(
	boolean badge,
	NameAnimation animation,
	NameEffect effect,
	int primary,
	int secondary,
	float speed,
	boolean bold
) {
	public static final NametagStyle BADGE_ONLY = new NametagStyle(true, NameAnimation.NONE, NameEffect.NONE, 0xFFFFFFFF, 0xFFFFFFFF, 1f, false);

	public boolean decoratesName() {
		return animation.recolors() || effect.moves() || bold;
	}

	public JsonObject toJson() {
		JsonObject object = new JsonObject();
		object.addProperty("badge", badge);
		object.addProperty("animation", animation.name());
		object.addProperty("effect", effect.name());
		object.addProperty("primary", Colors.toRgbHex(primary));
		object.addProperty("secondary", Colors.toRgbHex(secondary));
		object.addProperty("speed", Math.round(speed * 100f) / 100f);
		object.addProperty("bold", bold);
		return object;
	}

	public static Optional<NametagStyle> fromJson(JsonElement element) {
		if (element == null || !element.isJsonObject()) {
			return Optional.empty();
		}
		JsonObject object = element.getAsJsonObject();
		float speed = (float) Math.clamp(Json.decimal(object, "speed", 1.0), 0.1, 3.0);
		return Optional.of(new NametagStyle(
			Json.bool(object, "badge", true),
			enumValue(NameAnimation.class, Json.string(object, "animation"), NameAnimation.NONE),
			enumValue(NameEffect.class, Json.string(object, "effect"), NameEffect.NONE),
			color(Json.string(object, "primary"), 0xFFFFFFFF),
			color(Json.string(object, "secondary"), 0xFFFFFFFF),
			speed,
			Json.bool(object, "bold", false)
		));
	}

	private static int color(String text, int fallback) {
		if (text == null || text.length() > 9) {
			return fallback;
		}
		return Colors.parseHex(text, fallback) | 0xFF000000;
	}

	private static <E extends Enum<E>> E enumValue(Class<E> type, String name, E fallback) {
		if (name == null) {
			return fallback;
		}
		for (E constant : type.getEnumConstants()) {
			if (constant.name().equals(name)) {
				return constant;
			}
		}
		return fallback;
	}
}

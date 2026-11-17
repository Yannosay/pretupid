package net.pretupid.client.cosmetic.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class WearableParser {
	private static final java.util.regex.Pattern NODE_NAME = java.util.regex.Pattern.compile("[a-z0-9_]{1,24}");
	private static final java.util.regex.Pattern HEX = java.util.regex.Pattern.compile("#[0-9A-Fa-f]{6}");
	private static final int MAX_NODES = 24;
	private static final int MAX_BOXES = 128;
	private static final int MAX_DEPTH = 4;
	private static final int MAX_CHANNELS = 8;
	private static final float MAX_COORDINATE = 64f;
	private static final float MAX_SIZE = 48f;
	private static final float DEGREES = (float) (Math.PI / 180.0);

	private WearableParser() {
	}

	public static final class InvalidModelException extends RuntimeException {
		InvalidModelException(String message) {
			super(message);
		}
	}

	private static final class Budget {
		int nodes;
		int boxes;
		final Set<String> names = new HashSet<>();
		final int textureWidth;
		final int textureHeight;

		Budget(int textureWidth, int textureHeight) {
			this.textureWidth = textureWidth;
			this.textureHeight = textureHeight;
		}
	}

	public static int textureWidth(JsonObject model) {
		return integer(model, "textureWidth", 0);
	}

	public static int textureHeight(JsonObject model) {
		return integer(model, "textureHeight", 0);
	}

	public static Shape parse(JsonObject model, Shape.Anchor anchor, boolean textured) {
		if (model == null) {
			throw new InvalidModelException("Missing model");
		}
		Budget budget = textured ? new Budget(textureWidth(model), textureHeight(model)) : new Budget(0, 0);
		Shape shape = Shape.on(anchor);
		for (JsonElement element : array(model, "nodes")) {
			JsonObject raw = object(element);
			float[] pivot = vector(raw, "pivot", MAX_COORDINATE, 0f);
			fill(shape.node(name(raw, budget), pivot[0], pivot[1], pivot[2]), raw, budget, 1);
		}
		if (budget.boxes == 0) {
			throw new InvalidModelException("Model has no boxes");
		}
		return shape;
	}

	private static void fill(Shape.Node node, JsonObject raw, Budget budget, int depth) {
		if (depth > MAX_DEPTH) {
			throw new InvalidModelException("Nodes are nested too deep");
		}
		if (++budget.nodes > MAX_NODES) {
			throw new InvalidModelException("Too many nodes");
		}
		float[] rotation = vector(raw, "rotation", 360f, 0f);
		node.rotated(rotation[0] * DEGREES, rotation[1] * DEGREES, rotation[2] * DEGREES);
		for (JsonElement element : array(raw, "boxes")) {
			if (++budget.boxes > MAX_BOXES) {
				throw new InvalidModelException("Too many boxes");
			}
			node.box(box(object(element), budget));
		}
		int channels = 0;
		for (JsonElement element : array(raw, "animations")) {
			if (++channels > MAX_CHANNELS) {
				break;
			}
			node.animate(channel(object(element)));
		}
		for (JsonElement element : array(raw, "children")) {
			JsonObject child = object(element);
			float[] pivot = vector(child, "pivot", MAX_COORDINATE, 0f);
			fill(node.child(name(child, budget), pivot[0], pivot[1], pivot[2]), child, budget, depth + 1);
		}
	}

	private static Shape.Box box(JsonObject raw, Budget budget) {
		float[] from = vector(raw, "from", MAX_COORDINATE, Float.NaN);
		float[] size = vector(raw, "size", MAX_SIZE, Float.NaN);
		for (float value : size) {
			if (value < 0f) {
				throw new InvalidModelException("Box sizes must not be negative");
			}
		}
		int color = color(raw, "color", 0xFFFFFFFF);
		int accent = color(raw, "accent", color);
		Pattern pattern = enumValue(Pattern.class, raw, "pattern", Pattern.SOLID);
		int u = -1;
		int v = -1;
		JsonElement uv = raw.get("uv");
		if (uv != null && uv.isJsonArray() && uv.getAsJsonArray().size() == 2 && budget.textureWidth > 0 && budget.textureHeight > 0) {
			u = Math.clamp(Math.round(number(uv.getAsJsonArray().get(0))), 0, budget.textureWidth - 1);
			v = Math.clamp(Math.round(number(uv.getAsJsonArray().get(1))), 0, budget.textureHeight - 1);
		}
		JsonElement mirror = raw.get("mirror");
		boolean mirrored = mirror != null && mirror.isJsonPrimitive() && mirror.getAsJsonPrimitive().isBoolean() && mirror.getAsBoolean();
		return new Shape.Box(from[0], from[1], from[2], size[0], size[1], size[2], color, accent, pattern, u, v, mirrored);
	}

	private static Shape.Channel channel(JsonObject raw) {
		Shape.Property property = switch (string(raw, "property", "rotY")) {
			case "rotX" -> Shape.Property.ROT_X;
			case "rotY" -> Shape.Property.ROT_Y;
			case "rotZ" -> Shape.Property.ROT_Z;
			case "x" -> Shape.Property.X;
			case "y" -> Shape.Property.Y;
			case "z" -> Shape.Property.Z;
			case "scale" -> Shape.Property.SCALE;
			case "scaleX" -> Shape.Property.SCALE_X;
			case "scaleY" -> Shape.Property.SCALE_Y;
			case "scaleZ" -> Shape.Property.SCALE_Z;
			default -> throw new InvalidModelException("Unknown animation property");
		};
		Shape.Wave wave = enumValue(Shape.Wave.class, raw, "wave", Shape.Wave.NONE);
		return new Shape.Channel(property, wave, clamped(raw, "amplitude", 720f), Math.clamp(decimal(raw, "speed"), 0f, 10f), clamped(raw, "phase", 100f),
			clamped(raw, "offset", 720f), clamped(raw, "walk", 10f), clamped(raw, "walkOffset", 720f), clamped(raw, "crouch", 720f));
	}

	private static String name(JsonObject raw, Budget budget) {
		String name = string(raw, "name", "");
		if (!NODE_NAME.matcher(name).matches() || !budget.names.add(name)) {
			throw new InvalidModelException("Invalid or duplicate node name");
		}
		return name;
	}

	private static JsonObject object(JsonElement element) {
		if (element == null || !element.isJsonObject()) {
			throw new InvalidModelException("Expected an object");
		}
		return element.getAsJsonObject();
	}

	private static JsonArray array(JsonObject object, String key) {
		JsonElement element = object.get(key);
		return element != null && element.isJsonArray() ? element.getAsJsonArray() : new JsonArray();
	}

	private static String string(JsonObject object, String key, String fallback) {
		JsonElement element = object.get(key);
		return element != null && element.isJsonPrimitive() ? element.getAsString() : fallback;
	}

	private static int integer(JsonObject object, String key, int fallback) {
		JsonElement element = object == null ? null : object.get(key);
		return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber() ? element.getAsInt() : fallback;
	}

	private static float number(JsonElement element) {
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
			throw new InvalidModelException("Expected a number");
		}
		float value = element.getAsFloat();
		if (!Float.isFinite(value)) {
			throw new InvalidModelException("Expected a finite number");
		}
		return value;
	}

	private static float decimal(JsonObject object, String key) {
		JsonElement element = object.get(key);
		return element == null ? 0f : number(element);
	}

	private static float clamped(JsonObject object, String key, float limit) {
		return Math.clamp(decimal(object, key), -limit, limit);
	}

	private static float[] vector(JsonObject object, String key, float limit, float fallback) {
		JsonElement element = object.get(key);
		if (element == null && !Float.isNaN(fallback)) {
			return new float[] {fallback, fallback, fallback};
		}
		if (element == null || !element.isJsonArray() || element.getAsJsonArray().size() != 3) {
			throw new InvalidModelException("Expected three numbers for " + key);
		}
		JsonArray array = element.getAsJsonArray();
		float[] result = new float[3];
		for (int i = 0; i < 3; i++) {
			result[i] = Math.clamp(number(array.get(i)), -limit, limit);
		}
		return result;
	}

	private static int color(JsonObject object, String key, int fallback) {
		String text = string(object, key, null);
		if (text == null || !HEX.matcher(text).matches()) {
			return fallback;
		}
		return 0xFF000000 | Integer.parseInt(text.substring(1), 16);
	}

	private static <E extends Enum<E>> E enumValue(Class<E> type, JsonObject object, String key, E fallback) {
		String text = string(object, key, null);
		if (text == null) {
			return fallback;
		}
		try {
			return Enum.valueOf(type, text.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException exception) {
			return fallback;
		}
	}
}

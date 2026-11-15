package net.pretupid.client.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public final class Json {
	private Json() {
	}

	public static String string(JsonObject object, String key) {
		if (object == null) {
			return null;
		}
		JsonElement element = object.get(key);
		return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
	}

	public static String string(JsonObject object, String key, int maxLength) {
		String value = string(object, key);
		if (value == null) {
			return null;
		}
		return value.length() > maxLength ? value.substring(0, maxLength) : value;
	}

	public static long number(JsonObject object, String key, long fallback) {
		if (object == null) {
			return fallback;
		}
		JsonElement element = object.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
			return fallback;
		}
		return element.getAsLong();
	}

	public static double decimal(JsonObject object, String key, double fallback) {
		if (object == null) {
			return fallback;
		}
		JsonElement element = object.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
			return fallback;
		}
		double value = element.getAsDouble();
		return Double.isFinite(value) ? value : fallback;
	}

	public static boolean bool(JsonObject object, String key, boolean fallback) {
		if (object == null) {
			return fallback;
		}
		JsonElement element = object.get(key);
		return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean() ? element.getAsBoolean() : fallback;
	}

	public static JsonObject object(JsonObject object, String key) {
		if (object == null) {
			return null;
		}
		JsonElement element = object.get(key);
		return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
	}

	public static JsonArray array(JsonObject object, String key) {
		if (object == null) {
			return new JsonArray();
		}
		JsonElement element = object.get(key);
		return element != null && element.isJsonArray() ? element.getAsJsonArray() : new JsonArray();
	}
}

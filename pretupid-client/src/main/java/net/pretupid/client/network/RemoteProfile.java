package net.pretupid.client.network;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.net.URI;
import java.util.Optional;
import java.util.regex.Pattern;
import net.pretupid.client.nametag.NametagStyle;

public record RemoteProfile(NametagStyle style, CapeRef cape, String trail, EmoteRef emote) {
	private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
	private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9_-]{0,31}");

	public record CapeRef(URI url, String sha256) {
	}

	public record EmoteRef(String id, long startedAtMillis) {
	}

	public static Optional<RemoteProfile> fromJson(JsonElement element, long receivedAt) {
		if (element == null || !element.isJsonObject()) {
			return Optional.empty();
		}
		JsonObject object = element.getAsJsonObject();
		Optional<NametagStyle> style = NametagStyle.fromJson(object.get("style"));
		if (style.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(new RemoteProfile(style.get(), cape(Json.object(object, "cape")), id(Json.string(object, "trail")), emote(Json.object(object, "emote"), receivedAt)));
	}

	public static CapeRef cape(JsonObject cape) {
		if (cape == null) {
			return null;
		}
		String sha = Json.string(cape, "sha256");
		String url = Json.string(cape, "url", 512);
		if (sha == null || url == null || !SHA256.matcher(sha).matches()) {
			return null;
		}
		try {
			URI uri = URI.create(url);
			return ApiClient.isTrustedContentHost(uri) ? new CapeRef(uri, sha) : null;
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}

	private static EmoteRef emote(JsonObject emote, long receivedAt) {
		if (emote == null) {
			return null;
		}
		String id = id(Json.string(emote, "id"));
		long age = Json.number(emote, "age", -1L);
		if (id == null || age < 0 || age > 60) {
			return null;
		}
		return new EmoteRef(id, receivedAt - age * 1000L);
	}

	private static String id(String value) {
		return value != null && ID.matcher(value).matches() ? value : null;
	}
}

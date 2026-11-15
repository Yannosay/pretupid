package net.pretupid.client.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public final class TextSetting extends Setting<String> {
	private final int maxLength;

	public TextSetting(String id, String defaultValue, int maxLength) {
		super(id, defaultValue);
		this.maxLength = maxLength;
	}

	public int maxLength() {
		return maxLength;
	}

	@Override
	protected String sanitize(String candidate) {
		StringBuilder builder = new StringBuilder(Math.min(candidate.length(), maxLength));
		candidate.codePoints().filter(cp -> cp >= 0x20 && cp != 0x7F && cp != 0xA7).limit(maxLength).forEach(builder::appendCodePoint);
		return builder.toString();
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(get());
	}

	@Override
	public void fromJson(JsonElement element) {
		if (element != null && element.isJsonPrimitive()) {
			set(element.getAsString());
		}
	}
}

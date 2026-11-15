package net.pretupid.client.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.pretupid.client.util.Colors;

public final class ColorSetting extends Setting<ColorSetting.Value> {
	private final boolean allowAlpha;

	public ColorSetting(String id, int argb, boolean allowAlpha) {
		super(id, new Value(allowAlpha ? argb : (argb | 0xFF000000), false));
		this.allowAlpha = allowAlpha;
	}

	public ColorSetting(String id, int argb) {
		this(id, argb, false);
	}

	public boolean allowsAlpha() {
		return allowAlpha;
	}

	public int argb() {
		return get().argb();
	}

	public boolean chroma() {
		return get().chroma();
	}

	public int resolve(float phase) {
		Value value = get();
		if (!value.chroma()) {
			return value.argb();
		}
		return Colors.withAlpha(Colors.chroma(phase), Colors.alpha(value.argb()));
	}

	public int resolve() {
		return resolve(0f);
	}

	public void setArgb(int argb) {
		set(new Value(argb, get().chroma()));
	}

	public void setChroma(boolean chroma) {
		set(new Value(get().argb(), chroma));
	}

	@Override
	protected Value sanitize(Value candidate) {
		return allowAlpha ? candidate : new Value(candidate.argb() | 0xFF000000, candidate.chroma());
	}

	@Override
	public JsonElement toJson() {
		JsonObject object = new JsonObject();
		object.addProperty("argb", Colors.toHex(argb()));
		object.addProperty("chroma", chroma());
		return object;
	}

	@Override
	public void fromJson(JsonElement element) {
		if (element == null || !element.isJsonObject()) {
			return;
		}
		JsonObject object = element.getAsJsonObject();
		int argb = argb();
		if (object.has("argb") && object.get("argb").isJsonPrimitive()) {
			argb = Colors.parseHex(object.get("argb").getAsString(), argb);
		}
		boolean chroma = object.has("chroma") && object.get("chroma").isJsonPrimitive() && object.get("chroma").getAsBoolean();
		set(new Value(argb, chroma));
	}

	public record Value(int argb, boolean chroma) {
	}
}

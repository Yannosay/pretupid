package net.pretupid.client.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.util.Colors;

public final class ColorSetting extends Setting<ColorSetting.Value> {
	private enum Source {
		NONE,
		ACCENT,
		MODULE_TEXT
	}

	private final boolean allowAlpha;
	private Source source = Source.NONE;
	private boolean allowChroma = true;

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

	public ColorSetting withoutChroma() {
		this.allowChroma = false;
		return this;
	}

	public boolean allowsChroma() {
		return allowChroma;
	}

	public int argb() {
		return get().argb();
	}

	public boolean chroma() {
		return get().chroma();
	}

	public ColorSetting followingAccent() {
		this.source = Source.ACCENT;
		return this;
	}

	public ColorSetting followingModuleText() {
		this.source = Source.MODULE_TEXT;
		return this;
	}

	public boolean canFollowAccent() {
		return source != Source.NONE;
	}

	public boolean followsAccent() {
		return source != Source.NONE && isDefault();
	}

	public String followLabelKey() {
		return source == Source.MODULE_TEXT ? "pretupid.ui.module_text_color" : "pretupid.ui.interface_color";
	}

	public int displayArgb() {
		return followsAccent() ? Colors.withAlpha(sourceArgb(0f), Colors.alpha(argb())) : argb();
	}

	private int sourceArgb(float phase) {
		PretupidClient client = PretupidClient.getOrNull();
		if (client == null) {
			return 0xFF4A9EFF;
		}
		ColorSetting parent = source == Source.MODULE_TEXT ? client.settings().moduleText : client.settings().accent;
		return parent == this ? client.settings().accent.resolve(phase) : parent.resolve(phase);
	}

	public int resolve(float phase) {
		if (followsAccent()) {
			return Colors.withAlpha(sourceArgb(phase), Colors.alpha(argb()));
		}
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
		boolean chroma = allowChroma && candidate.chroma();
		return allowAlpha ? new Value(candidate.argb(), chroma) : new Value(candidate.argb() | 0xFF000000, chroma);
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

package net.pretupid.client.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.network.chat.Component;

public final class KeySetting extends Setting<Integer> {
	public static final int UNBOUND = -1;

	public KeySetting(String id, int defaultKey) {
		super(id, defaultKey);
	}

	public boolean isBound() {
		return get() != UNBOUND;
	}

	public boolean matches(int key) {
		return isBound() && get() == key;
	}

	public Component keyLabel() {
		if (!isBound()) {
			return Component.translatable("pretupid.key.none");
		}
		return InputConstants.Type.KEYBOARD.getOrCreate(get()).getDisplayName();
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(get());
	}

	@Override
	public void fromJson(JsonElement element) {
		if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
			set(element.getAsInt());
		}
	}
}

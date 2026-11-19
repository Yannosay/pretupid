package net.pretupid.client.setting;

import net.pretupid.client.platform.Platform;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.network.chat.Component;

public final class KeySetting extends Setting<Integer> {
	public static final int UNBOUND = -1;
	private static final String NONE = "none";

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
		return Platform.keyboard().getOrCreate(get()).getDisplayName();
	}

	@Override
	public JsonElement toJson() {
		if (!isBound()) {
			return new JsonPrimitive(NONE);
		}
		return new JsonPrimitive(Platform.keyboard().getOrCreate(get()).getName());
	}

	@Override
	public void fromJson(JsonElement element) {
		if (element == null || !element.isJsonPrimitive()) {
			return;
		}
		JsonPrimitive primitive = element.getAsJsonPrimitive();
		if (primitive.isString()) {
			String name = primitive.getAsString();
			if (name.equals(NONE)) {
				set(UNBOUND);
				return;
			}
			try {
				InputConstants.Key key = InputConstants.getKey(name);
				if (key != InputConstants.UNKNOWN && key.getType() == Platform.keyboard()) {
					set(key.getValue());
				}
			} catch (IllegalArgumentException exception) {
				return;
			}
		} else if (primitive.isNumber() && Platform.SDL_INPUT) {
			set(primitive.getAsInt());
		}
	}
}

package net.pretupid.client.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.util.Locale;
import net.minecraft.network.chat.Component;

public final class EnumSetting<E extends Enum<E>> extends Setting<E> {
	private final E[] constants;

	public EnumSetting(String id, E defaultValue) {
		super(id, defaultValue);
		this.constants = defaultValue.getDeclaringClass().getEnumConstants();
	}

	public E[] constants() {
		return constants;
	}

	public void cycle(boolean forward) {
		int index = get().ordinal() + (forward ? 1 : -1);
		set(constants[Math.floorMod(index, constants.length)]);
	}

	public Component valueLabel(E value) {
		return Component.translatable("pretupid.enum." + value.getDeclaringClass().getSimpleName().toLowerCase(Locale.ROOT) + "." + value.name().toLowerCase(Locale.ROOT));
	}

	public Component valueLabel() {
		return valueLabel(get());
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(get().name());
	}

	@Override
	public void fromJson(JsonElement element) {
		if (element == null || !element.isJsonPrimitive()) {
			return;
		}
		String name = element.getAsString();
		for (E constant : constants) {
			if (constant.name().equalsIgnoreCase(name)) {
				set(constant);
				return;
			}
		}
	}
}

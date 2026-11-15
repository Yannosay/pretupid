package net.pretupid.client.setting;

import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;

public abstract class Setting<T> {
	private final String id;
	private final T defaultValue;
	private final List<Consumer<T>> listeners = new ArrayList<>();
	private String translationPrefix = "pretupid.setting";
	private BooleanSupplier visibility = () -> true;
	private T value;

	protected Setting(String id, T defaultValue) {
		this.id = Objects.requireNonNull(id);
		this.defaultValue = Objects.requireNonNull(defaultValue);
		this.value = defaultValue;
	}

	public final String id() {
		return id;
	}

	public final T get() {
		return value;
	}

	public final T defaultValue() {
		return defaultValue;
	}

	public final void set(T newValue) {
		T sanitized = sanitize(Objects.requireNonNull(newValue));
		if (Objects.equals(sanitized, value)) {
			return;
		}
		value = sanitized;
		for (Consumer<T> listener : listeners) {
			listener.accept(sanitized);
		}
		SettingChangeBus.fire(this);
	}

	public final void reset() {
		set(defaultValue);
	}

	public final boolean isDefault() {
		return Objects.equals(value, defaultValue);
	}

	public final Setting<T> onChange(Consumer<T> listener) {
		listeners.add(listener);
		return this;
	}

	public final Setting<T> visibleWhen(BooleanSupplier condition) {
		this.visibility = condition;
		return this;
	}

	public final boolean isVisible() {
		return visibility.getAsBoolean();
	}

	public final void bindTranslation(String prefix) {
		this.translationPrefix = prefix;
	}

	public final String translationKey() {
		return translationPrefix + "." + id;
	}

	public Component label() {
		return Component.translatable(translationKey());
	}

	protected T sanitize(T candidate) {
		return candidate;
	}

	public abstract JsonElement toJson();

	public abstract void fromJson(JsonElement element);
}

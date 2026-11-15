package net.pretupid.client.module;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.pretupid.client.setting.BooleanSetting;
import net.pretupid.client.setting.KeySetting;
import net.pretupid.client.setting.Setting;
import net.pretupid.client.ui.Icon;

public abstract class Module {
	private static boolean suppressCallbacks;

	private final String id;
	private final Category category;
	private final Icon icon;
	private final BooleanSetting enabled;
	private final KeySetting toggleKey;
	private final List<Setting<?>> settings = new ArrayList<>();
	private final List<Setting<?>> view = Collections.unmodifiableList(settings);
	private Component pauseReason;

	protected Module(String id, Category category, String icon, boolean enabledByDefault) {
		this.id = id;
		this.category = category;
		this.icon = Icon.item(icon);
		this.enabled = new BooleanSetting("enabled", enabledByDefault);
		this.enabled.bindTranslation("pretupid.common");
		this.enabled.onChange(active -> {
			if (suppressCallbacks || pauseReason != null) {
				return;
			}
			if (active) {
				onEnable();
			} else {
				onDisable();
			}
		});
		this.toggleKey = new KeySetting("toggle_key", KeySetting.UNBOUND);
		this.toggleKey.bindTranslation("pretupid.common");
	}

	public static void withoutCallbacks(Runnable action) {
		boolean previous = suppressCallbacks;
		suppressCallbacks = true;
		try {
			action.run();
		} finally {
			suppressCallbacks = previous;
		}
	}

	protected final <S extends Setting<?>> S add(S setting) {
		setting.bindTranslation("pretupid.module." + id);
		settings.add(setting);
		return setting;
	}

	public final String id() {
		return id;
	}

	public final Category category() {
		return category;
	}

	public final Icon icon() {
		return icon;
	}

	public final boolean isEnabled() {
		return enabled.get() && pauseReason == null;
	}

	public final boolean isPaused() {
		return pauseReason != null;
	}

	public final Component pauseReason() {
		return pauseReason;
	}

	public final void pause(Component reason) {
		boolean wasActive = isEnabled();
		pauseReason = reason;
		if (wasActive) {
			onDisable();
		}
	}

	public final void resume() {
		if (pauseReason == null) {
			return;
		}
		pauseReason = null;
		if (enabled.get()) {
			onEnable();
		}
	}

	public final void setEnabled(boolean value) {
		enabled.set(value);
	}

	public final void toggle() {
		enabled.toggle();
	}

	public final BooleanSetting enabledSetting() {
		return enabled;
	}

	public final KeySetting toggleKey() {
		return toggleKey;
	}

	public final List<Setting<?>> settings() {
		return view;
	}

	public Component name() {
		return Component.translatable("pretupid.module." + id + ".name");
	}

	public Component description() {
		return Component.translatable("pretupid.module." + id + ".description");
	}

	public final boolean matches(String query) {
		if (query.isEmpty()) {
			return true;
		}
		String needle = query.toLowerCase(Locale.ROOT);
		return name().getString().toLowerCase(Locale.ROOT).contains(needle)
			|| description().getString().toLowerCase(Locale.ROOT).contains(needle)
			|| category.label().getString().toLowerCase(Locale.ROOT).contains(needle);
	}

	public final void resetSettings() {
		for (Setting<?> setting : settings) {
			setting.reset();
		}
		onReset();
	}

	protected void onReset() {
	}

	protected void onEnable() {
	}

	public void onProfileApplied() {
	}

	protected void onDisable() {
	}

	public void onTick(Minecraft minecraft) {
	}

	public JsonObject save() {
		JsonObject object = new JsonObject();
		object.add(enabled.id(), enabled.toJson());
		object.add(toggleKey.id(), toggleKey.toJson());
		JsonObject values = new JsonObject();
		for (Setting<?> setting : settings) {
			values.add(setting.id(), setting.toJson());
		}
		object.add("settings", values);
		return object;
	}

	public void load(JsonObject object) {
		JsonElement values = object.get("settings");
		if (values != null && values.isJsonObject()) {
			JsonObject map = values.getAsJsonObject();
			for (Setting<?> setting : settings) {
				JsonElement value = map.get(setting.id());
				if (value != null) {
					setting.fromJson(value);
				}
			}
		}
		toggleKey.fromJson(object.get(toggleKey.id()));
		enabled.fromJson(object.get(enabled.id()));
	}

	public void loadDefaults() {
		for (Setting<?> setting : settings) {
			setting.reset();
		}
		toggleKey.reset();
		enabled.reset();
	}
}

package net.pretupid.client.module;

import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.pretupid.client.ui.Icon;

public enum Category {
	HUD("item_frame"),
	PERFORMANCE("experience_bottle"),
	VISUAL("spyglass"),
	UTILITY("compass_00");

	private final Icon icon;

	Category(String icon) {
		this.icon = Icon.item(icon);
	}

	public Icon icon() {
		return icon;
	}

	public String key() {
		return name().toLowerCase(Locale.ROOT);
	}

	public Component label() {
		return Component.translatable("pretupid.category." + key());
	}
}

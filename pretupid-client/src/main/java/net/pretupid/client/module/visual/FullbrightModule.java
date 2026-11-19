package net.pretupid.client.module.visual;

import net.pretupid.client.module.Category;
import net.pretupid.client.module.Module;
import net.pretupid.client.setting.NumberSetting;

public final class FullbrightModule extends Module {
	private final NumberSetting intensity = add(new NumberSetting("intensity", 100, 10, 100, 5, "%"));
	private boolean wasActive;

	public FullbrightModule() {
		super("fullbright", Category.VISUAL, "glowstone_dust", false);
	}

	public float nightVision(float original) {
		return isEnabled() ? Math.max(original, intensity.floatValue() / 100f) : original;
	}

	public boolean needsLightUpdate() {
		boolean active = isEnabled();
		boolean changed = active || wasActive;
		wasActive = active;
		return changed;
	}
}

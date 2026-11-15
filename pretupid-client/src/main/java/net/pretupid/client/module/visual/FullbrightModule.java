package net.pretupid.client.module.visual;

import net.minecraft.client.renderer.state.LightmapRenderState;
import net.pretupid.client.module.Category;
import net.pretupid.client.module.Module;
import net.pretupid.client.setting.NumberSetting;

public final class FullbrightModule extends Module {
	private final NumberSetting intensity = add(new NumberSetting("intensity", 100, 10, 100, 5, "%"));
	private boolean wasActive;

	public FullbrightModule() {
		super("fullbright", Category.VISUAL, "glowstone_dust", false);
	}

	public void apply(LightmapRenderState state) {
		boolean active = isEnabled();
		if (active) {
			state.nightVisionEffectIntensity = Math.max(state.nightVisionEffectIntensity, intensity.floatValue() / 100f);
			state.needsUpdate = true;
		} else if (wasActive) {
			state.needsUpdate = true;
		}
		wasActive = active;
	}
}

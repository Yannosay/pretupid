package net.pretupid.client.module.visual;

import net.pretupid.client.module.Category;
import net.pretupid.client.module.Module;
import net.pretupid.client.setting.BooleanSetting;

public final class ClearWeatherModule extends Module {
	private final BooleanSetting hideRain = add(new BooleanSetting("hide_rain", true));
	private final BooleanSetting hideThunder = add(new BooleanSetting("hide_thunder", true));

	public ClearWeatherModule() {
		super("clear_weather", Category.VISUAL, "lantern", false);
	}

	public boolean hideRain() {
		return isEnabled() && hideRain.enabled();
	}

	public boolean hideThunder() {
		return isEnabled() && hideThunder.enabled();
	}
}

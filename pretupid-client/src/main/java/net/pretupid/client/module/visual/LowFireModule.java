package net.pretupid.client.module.visual;

import net.pretupid.client.module.Category;
import net.pretupid.client.module.Module;
import net.pretupid.client.setting.NumberSetting;

public final class LowFireModule extends Module {
	private final NumberSetting offset = add(new NumberSetting("offset", 0.3, 0.05, 0.6, 0.05));

	public LowFireModule() {
		super("low_fire", Category.VISUAL, "campfire", false);
	}

	public float offset() {
		return offset.floatValue();
	}
}

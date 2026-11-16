package net.pretupid.client.module.visual;

import net.pretupid.client.module.Category;
import net.pretupid.client.module.Module;
import net.pretupid.client.setting.ColorSetting;
import net.pretupid.client.setting.NumberSetting;

public final class BlockOutlineModule extends Module {
	private final ColorSetting color = add(new ColorSetting("color", 0xCC4A9EFF, true));
	private final NumberSetting thickness = add(new NumberSetting("thickness", 2.0, 0.5, 6.0, 0.25, "x"));

	public BlockOutlineModule() {
		super("block_outline", Category.VISUAL, "brush", false);
	}

	public int color() {
		return color.resolve();
	}

	public float width(float vanillaWidth) {
		return vanillaWidth * thickness.floatValue();
	}
}

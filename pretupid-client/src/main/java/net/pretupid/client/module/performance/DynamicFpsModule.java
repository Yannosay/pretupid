package net.pretupid.client.module.performance;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.pretupid.client.module.Category;
import net.pretupid.client.module.Module;
import net.pretupid.client.setting.BooleanSetting;
import net.pretupid.client.setting.NumberSetting;

public final class DynamicFpsModule extends Module {
	private final NumberSetting unfocusedFps = add(new NumberSetting("unfocused_fps", 20, 1, 120, 1, " FPS"));
	private final NumberSetting minimizedFps = add(new NumberSetting("minimized_fps", 1, 1, 30, 1, " FPS"));
	private final BooleanSetting idleLimit = add(new BooleanSetting("idle_limit", true));
	private final NumberSetting idleSeconds = add(new NumberSetting("idle_seconds", 120, 15, 900, 15, "s"));
	private final NumberSetting idleFps = add(new NumberSetting("idle_fps", 15, 5, 60, 1, " FPS"));
	private volatile long lastInput = System.currentTimeMillis();

	public DynamicFpsModule() {
		super("dynamic_fps", Category.PERFORMANCE, "redstone", false);
	}

	public void recordInput() {
		lastInput = System.currentTimeMillis();
	}

	public int limit(Minecraft minecraft, int original) {
		Window window = minecraft.getWindow();
		if (window.isIconified()) {
			return Math.min(original, minimizedFps.intValue());
		}
		if (!window.isFocused()) {
			return Math.min(original, unfocusedFps.intValue());
		}
		if (idleLimit.enabled() && minecraft.level != null && System.currentTimeMillis() - lastInput > idleSeconds.intValue() * 1000L) {
			return Math.min(original, idleFps.intValue());
		}
		return original;
	}
}

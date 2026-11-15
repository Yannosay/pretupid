package net.pretupid.client.module.hud;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.pretupid.client.hud.HudPosition;
import net.pretupid.client.hud.TextHudModule;
import net.pretupid.client.setting.BooleanSetting;
import net.pretupid.client.ui.Theme;

public final class FpsHud extends TextHudModule {
	private final BooleanSetting frameTime = add(new BooleanSetting("frame_time", false));
	private final BooleanSetting dynamicColor = add(new BooleanSetting("dynamic_color", false));

	public FpsHud() {
		super("fps", "ender_eye", false, new HudPosition(HudPosition.Anchor.START, 4, HudPosition.Anchor.START, 4));
	}

	@Override
	public boolean visible(Minecraft minecraft) {
		return true;
	}

	@Override
	protected String value(Minecraft minecraft, boolean preview) {
		int fps = minecraft.getFps();
		if (frameTime.enabled()) {
			double ms = minecraft.getFrameTimeNs() / 1_000_000.0;
			return String.format(Locale.ROOT, "%d (%.1f ms)", fps, ms);
		}
		return Integer.toString(fps);
	}

	@Override
	protected int valueColor(Minecraft minecraft, boolean preview) {
		if (!dynamicColor.enabled()) {
			return super.valueColor(minecraft, preview);
		}
		int fps = minecraft.getFps();
		return fps >= 60 ? Theme.SUCCESS : fps >= 30 ? Theme.WARNING : Theme.DANGER;
	}
}

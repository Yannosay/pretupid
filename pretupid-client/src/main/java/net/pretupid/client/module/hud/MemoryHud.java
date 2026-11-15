package net.pretupid.client.module.hud;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.pretupid.client.hud.HudPosition;
import net.pretupid.client.hud.TextHudModule;
import net.pretupid.client.setting.BooleanSetting;
import net.pretupid.client.ui.Theme;

public final class MemoryHud extends TextHudModule {
	private final BooleanSetting showValues = add(new BooleanSetting("show_values", true));
	private final BooleanSetting dynamicColor = add(new BooleanSetting("dynamic_color", true));

	public MemoryHud() {
		super("memory", "amethyst_shard", false, new HudPosition(HudPosition.Anchor.START, 4, HudPosition.Anchor.START, 112));
	}

	@Override
	public boolean visible(Minecraft minecraft) {
		return true;
	}

	private static double usedRatio() {
		Runtime runtime = Runtime.getRuntime();
		long used = runtime.totalMemory() - runtime.freeMemory();
		return used / (double) runtime.maxMemory();
	}

	@Override
	protected String value(Minecraft minecraft, boolean preview) {
		Runtime runtime = Runtime.getRuntime();
		long used = runtime.totalMemory() - runtime.freeMemory();
		long max = runtime.maxMemory();
		int percent = (int) Math.round(used * 100.0 / max);
		if (!showValues.enabled()) {
			return percent + "%";
		}
		return String.format(Locale.ROOT, "%d%% (%d / %d MB)", percent, used >> 20, max >> 20);
	}

	@Override
	protected int valueColor(Minecraft minecraft, boolean preview) {
		if (!dynamicColor.enabled()) {
			return super.valueColor(minecraft, preview);
		}
		double ratio = usedRatio();
		return ratio < 0.7 ? super.valueColor(minecraft, preview) : ratio < 0.88 ? Theme.WARNING : Theme.DANGER;
	}
}

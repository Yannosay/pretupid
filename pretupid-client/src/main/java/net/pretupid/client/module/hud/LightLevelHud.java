package net.pretupid.client.module.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import net.pretupid.client.hud.HudPosition;
import net.pretupid.client.hud.TextHudModule;
import net.pretupid.client.setting.BooleanSetting;
import net.pretupid.client.ui.Theme;

public final class LightLevelHud extends TextHudModule {
	private final BooleanSetting showSky = add(new BooleanSetting("show_sky", false));
	private final BooleanSetting warnSpawnable = add(new BooleanSetting("warn_spawnable", true));

	public LightLevelHud() {
		super("light_level", "torch", false, new HudPosition(HudPosition.Anchor.START, 4, HudPosition.Anchor.START, 172));
	}

	private int blockLight(Minecraft minecraft) {
		if (minecraft.level == null || minecraft.player == null) {
			return 0;
		}
		return minecraft.level.getBrightness(LightLayer.BLOCK, minecraft.player.blockPosition());
	}

	@Override
	protected String value(Minecraft minecraft, boolean preview) {
		if (minecraft.level == null || minecraft.player == null) {
			return preview ? "14" : "0";
		}
		BlockPos pos = minecraft.player.blockPosition();
		int block = blockLight(minecraft);
		if (!showSky.enabled()) {
			return Integer.toString(block);
		}
		return block + " / " + minecraft.level.getBrightness(LightLayer.SKY, pos);
	}

	@Override
	protected int valueColor(Minecraft minecraft, boolean preview) {
		if (warnSpawnable.enabled() && minecraft.level != null && blockLight(minecraft) == 0) {
			return Theme.DANGER;
		}
		return super.valueColor(minecraft, preview);
	}
}

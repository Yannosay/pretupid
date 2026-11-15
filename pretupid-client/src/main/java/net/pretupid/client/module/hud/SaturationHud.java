package net.pretupid.client.module.hud;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.world.food.FoodData;
import net.pretupid.client.hud.HudPosition;
import net.pretupid.client.hud.TextHudModule;
import net.pretupid.client.setting.BooleanSetting;
import net.pretupid.client.ui.Theme;

public final class SaturationHud extends TextHudModule {
	private final BooleanSetting showHunger = add(new BooleanSetting("show_hunger", true));
	private final BooleanSetting warnLow = add(new BooleanSetting("warn_low", true));

	public SaturationHud() {
		super("saturation", "cooked_beef", false, new HudPosition(HudPosition.Anchor.START, 4, HudPosition.Anchor.START, 148));
	}

	@Override
	protected String value(Minecraft minecraft, boolean preview) {
		if (minecraft.player == null) {
			return showHunger.enabled() ? "20 / 5.0" : "5.0";
		}
		FoodData food = minecraft.player.getFoodData();
		String saturation = String.format(Locale.ROOT, "%.1f", food.getSaturationLevel());
		return showHunger.enabled() ? food.getFoodLevel() + " / " + saturation : saturation;
	}

	@Override
	protected int valueColor(Minecraft minecraft, boolean preview) {
		if (warnLow.enabled() && minecraft.player != null) {
			FoodData food = minecraft.player.getFoodData();
			if (food.getFoodLevel() <= 6) {
				return Theme.DANGER;
			}
			if (food.getSaturationLevel() <= 0f) {
				return Theme.WARNING;
			}
		}
		return super.valueColor(minecraft, preview);
	}
}

package net.pretupid.client.module.hud;

import net.minecraft.client.Minecraft;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.hud.HudPosition;
import net.pretupid.client.hud.TextHudModule;
import net.pretupid.client.setting.BooleanSetting;

public final class ComboHud extends TextHudModule {
	private final BooleanSetting hideWhenZero = add(new BooleanSetting("hide_zero", false));

	public ComboHud() {
		super("combo", "rabbit_foot", false, new HudPosition(HudPosition.Anchor.CENTER, -46, HudPosition.Anchor.CENTER, 14));
	}

	@Override
	public boolean visible(Minecraft minecraft) {
		return super.visible(minecraft) && !(hideWhenZero.enabled() && PretupidClient.get().combat().combo() == 0);
	}

	@Override
	protected String value(Minecraft minecraft, boolean preview) {
		int combo = PretupidClient.get().combat().combo();
		return preview && combo == 0 ? "4" : Integer.toString(combo);
	}
}

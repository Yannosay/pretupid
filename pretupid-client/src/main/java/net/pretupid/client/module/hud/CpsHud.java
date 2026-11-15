package net.pretupid.client.module.hud;

import net.minecraft.client.Minecraft;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.hud.HudPosition;
import net.pretupid.client.hud.TextHudModule;
import net.pretupid.client.input.ClickTracker;
import net.pretupid.client.setting.BooleanSetting;

public final class CpsHud extends TextHudModule {
	private final BooleanSetting showRight = add(new BooleanSetting("show_right", true));

	public CpsHud() {
		super("cps", "repeater", false, new HudPosition(HudPosition.Anchor.START, 4, HudPosition.Anchor.START, 22));
	}

	@Override
	protected String value(Minecraft minecraft, boolean preview) {
		ClickTracker clicks = PretupidClient.get().clicks();
		int left = clicks.leftCps();
		if (!showRight.enabled()) {
			return Integer.toString(left);
		}
		return left + " | " + clicks.rightCps();
	}
}

package net.pretupid.client.module.hud;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.hud.HudPosition;
import net.pretupid.client.hud.TextHudModule;
import net.pretupid.client.input.CombatTracker;
import net.pretupid.client.setting.NumberSetting;

public final class ReachHud extends TextHudModule {
	private final NumberSetting decimals = add(new NumberSetting("decimals", 2, 0, 3, 1));
	private final NumberSetting resetAfter = add(new NumberSetting("reset_after", 3, 1, 15, 1, "s"));

	public ReachHud() {
		super("reach", "iron_sword", false, new HudPosition(HudPosition.Anchor.CENTER, 46, HudPosition.Anchor.CENTER, 14));
	}

	@Override
	protected String value(Minecraft minecraft, boolean preview) {
		CombatTracker combat = PretupidClient.get().combat();
		boolean stale = System.currentTimeMillis() - combat.lastReachAt() > resetAfter.intValue() * 1000L;
		if (preview && (combat.lastReach() < 0 || stale)) {
			return String.format(Locale.ROOT, "%." + decimals.intValue() + "f", 2.87);
		}
		if (combat.lastReach() < 0 || stale) {
			return "-";
		}
		return String.format(Locale.ROOT, "%." + decimals.intValue() + "f", combat.lastReach());
	}
}

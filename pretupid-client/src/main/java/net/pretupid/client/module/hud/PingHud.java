package net.pretupid.client.module.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.pretupid.client.hud.HudPosition;
import net.pretupid.client.hud.TextHudModule;
import net.pretupid.client.setting.BooleanSetting;
import net.pretupid.client.ui.Theme;

public final class PingHud extends TextHudModule {
	private final BooleanSetting dynamicColor = add(new BooleanSetting("dynamic_color", true));
	private final BooleanSetting hideInSingleplayer = add(new BooleanSetting("hide_singleplayer", true));

	public PingHud() {
		super("ping", "ender_pearl", false, new HudPosition(HudPosition.Anchor.START, 4, HudPosition.Anchor.START, 40));
	}

	@Override
	public boolean visible(Minecraft minecraft) {
		return super.visible(minecraft) && !(hideInSingleplayer.enabled() && minecraft.hasSingleplayerServer());
	}

	private int latency(Minecraft minecraft) {
		ClientPacketListener connection = minecraft.getConnection();
		if (connection == null || minecraft.player == null) {
			return 0;
		}
		PlayerInfo info = connection.getPlayerInfo(minecraft.player.getUUID());
		return info == null ? 0 : info.getLatency();
	}

	@Override
	protected String value(Minecraft minecraft, boolean preview) {
		return latency(minecraft) + " ms";
	}

	@Override
	protected int valueColor(Minecraft minecraft, boolean preview) {
		if (!dynamicColor.enabled()) {
			return super.valueColor(minecraft, preview);
		}
		int ping = latency(minecraft);
		return ping <= 80 ? Theme.SUCCESS : ping <= 180 ? Theme.WARNING : Theme.DANGER;
	}
}

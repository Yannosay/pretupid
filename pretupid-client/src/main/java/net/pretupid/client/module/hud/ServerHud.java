package net.pretupid.client.module.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.resources.language.I18n;
import net.pretupid.client.hud.HudPosition;
import net.pretupid.client.hud.TextHudModule;
import net.pretupid.client.setting.BooleanSetting;

public final class ServerHud extends TextHudModule {
	private final BooleanSetting streamerMode = add(new BooleanSetting("streamer_mode", false));
	private final BooleanSetting preferName = add(new BooleanSetting("prefer_name", true));

	public ServerHud() {
		super("server", "bell", false, new HudPosition(HudPosition.Anchor.START, 4, HudPosition.Anchor.START, 130));
	}

	@Override
	protected String value(Minecraft minecraft, boolean preview) {
		if (minecraft.hasSingleplayerServer()) {
			return I18n.get("pretupid.hud.singleplayer");
		}
		ServerData server = minecraft.getCurrentServer();
		if (server == null) {
			return preview ? "play.example.net" : "-";
		}
		if (preferName.enabled() && server.name != null && !server.name.isBlank() && !server.name.equals(I18n.get("selectServer.defaultName"))) {
			return server.name;
		}
		if (streamerMode.enabled()) {
			return I18n.get("pretupid.hud.hidden");
		}
		return server.ip;
	}
}

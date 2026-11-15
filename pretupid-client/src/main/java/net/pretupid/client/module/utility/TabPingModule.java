package net.pretupid.client.module.utility;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.pretupid.client.module.Category;
import net.pretupid.client.module.Module;
import net.pretupid.client.setting.BooleanSetting;
import net.pretupid.client.ui.Draw;
import net.pretupid.client.ui.Theme;

public final class TabPingModule extends Module {
	private final BooleanSetting showUnit = add(new BooleanSetting("show_unit", false));

	public TabPingModule() {
		super("tab_ping", Category.UTILITY, "spectral_arrow", false);
	}

	public void render(GuiGraphicsExtractor graphics, int width, int x, int y, PlayerInfo info) {
		int latency = info.getLatency();
		String text = latency < 0 ? "?" : showUnit.enabled() ? latency + "ms" : Integer.toString(latency);
		int color = latency < 0 ? Theme.TEXT_DIM : latency <= 80 ? Theme.SUCCESS : latency <= 180 ? Theme.WARNING : Theme.DANGER;
		float scale = 0.75f;
		float textWidth = Minecraft.getInstance().font.width(text) * scale;
		Draw.scaled(graphics, Minecraft.getInstance().font, Component.literal(text), x + width - 1 - textWidth, y + 1.5f, scale, color, true);
	}
}

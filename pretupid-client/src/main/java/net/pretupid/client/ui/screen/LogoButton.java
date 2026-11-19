package net.pretupid.client.ui.screen;

import net.pretupid.client.platform.Gfx;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.pretupid.client.platform.PlatformButton;
import net.pretupid.client.ui.UiIcon;

final class LogoButton extends PlatformButton {
	private static final int LOGO_SIZE = 16;

	LogoButton(int size, Component message, OnPress onPress) {
		super(0, 0, size, size, message, onPress);
		setTooltip(Tooltip.create(message));
	}

	@Override
	protected void drawContents(Gfx graphics, int mouseX, int mouseY, float partialTick) {
		drawDefaultSprite(graphics);
		int x = getX() + (getWidth() - LOGO_SIZE) / 2;
		int y = getY() + (getHeight() - LOGO_SIZE) / 2;
		UiIcon.drawLogo(graphics, x, y, LOGO_SIZE, ARGB.white(alpha));
	}
}

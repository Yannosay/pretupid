package net.pretupid.client.platform;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public abstract class PlatformButton extends Button {
	protected PlatformButton(int x, int y, int width, int height, Component message, OnPress onPress) {
		super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
	}

	@Override
	protected final void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		drawContents(Gfx.of(graphics), mouseX, mouseY, partialTick);
	}

	protected abstract void drawContents(Gfx graphics, int mouseX, int mouseY, float partialTick);

	protected void drawDefaultSprite(Gfx graphics) {
		renderDefaultSprite(graphics.raw());
	}
}

package net.pretupid.client.platform;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public abstract class PlatformScreen extends Screen {
	protected PlatformScreen(Component title) {
		super(title);
	}

	@Override
	public final void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		renderScreen(Gfx.of(graphics), mouseX, mouseY, partialTick);
	}

	@Override
	public final void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		renderScreenBackground(Gfx.of(graphics), mouseX, mouseY, partialTick);
	}

	protected abstract void renderScreen(Gfx graphics, int mouseX, int mouseY, float partialTick);

	protected void renderScreenBackground(Gfx graphics, int mouseX, int mouseY, float partialTick) {
		super.renderBackground(graphics.raw(), mouseX, mouseY, partialTick);
	}

	protected void drawPanorama(Gfx graphics, float partialTick) {
		renderPanorama(graphics.raw(), partialTick);
	}

	protected void drawBlurredBackground(Gfx graphics) {
		renderBlurredBackground(graphics.raw());
	}
}

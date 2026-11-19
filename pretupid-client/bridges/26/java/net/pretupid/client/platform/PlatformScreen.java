package net.pretupid.client.platform;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public abstract class PlatformScreen extends Screen {
	protected PlatformScreen(Component title) {
		super(title);
	}

	@Override
	public final void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		renderScreen(Gfx.of(graphics), mouseX, mouseY, partialTick);
	}

	@Override
	public final void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		renderScreenBackground(Gfx.of(graphics), mouseX, mouseY, partialTick);
	}

	protected abstract void renderScreen(Gfx graphics, int mouseX, int mouseY, float partialTick);

	protected void renderScreenBackground(Gfx graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics.raw(), mouseX, mouseY, partialTick);
	}

	protected void drawPanorama(Gfx graphics, float partialTick) {
		extractPanorama(graphics.raw(), partialTick);
	}

	protected void drawBlurredBackground(Gfx graphics) {
		extractBlurredBackground(graphics.raw());
	}
}

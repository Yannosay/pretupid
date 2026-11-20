package net.pretupid.client.platform;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

public abstract class PlatformButton extends Button {
	private static final WidgetSprites BUTTON_SPRITES = new WidgetSprites(Identifier.withDefaultNamespace("widget/button"),
		Identifier.withDefaultNamespace("widget/button_disabled"), Identifier.withDefaultNamespace("widget/button_highlighted"));

	protected PlatformButton(int x, int y, int width, int height, Component message, OnPress onPress) {
		super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
	}

	@Override
	protected final void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		drawContents(Gfx.of(graphics), mouseX, mouseY, partialTick);
	}

	protected abstract void drawContents(Gfx graphics, int mouseX, int mouseY, float partialTick);

	protected void drawDefaultSprite(Gfx graphics) {
		graphics.raw().blitSprite(RenderType::guiTextured, BUTTON_SPRITES.get(active, isHoveredOrFocused()), getX(), getY(), getWidth(), getHeight(), ARGB.white(alpha));
	}
}

package net.pretupid.client.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public record Icon(Identifier texture) {
	public static Icon item(String name) {
		return new Icon(Identifier.withDefaultNamespace("textures/item/" + name + ".png"));
	}

	public void draw(GuiGraphicsExtractor graphics, int x, int y) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0f, 0f, 16, 16, 16, 16);
	}

	public void draw(GuiGraphicsExtractor graphics, int x, int y, int tint) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0f, 0f, 16, 16, 16, 16, tint);
	}
}

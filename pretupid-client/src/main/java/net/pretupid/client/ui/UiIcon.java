package net.pretupid.client.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.pretupid.client.PretupidClient;

public enum UiIcon {
	GEAR,
	STAR,
	CHEVRON_RIGHT,
	CHEVRON_LEFT,
	SEARCH,
	CLOSE,
	PLUS,
	FOLDER,
	CHECK,
	RESIZE;

	public static final int SIZE = 9;
	private static final Identifier ATLAS = Identifier.fromNamespaceAndPath(PretupidClient.MOD_ID, "textures/gui/icons.png");
	private static final Identifier LOGO = Identifier.fromNamespaceAndPath(PretupidClient.MOD_ID, "textures/gui/logo.png");

	public void draw(GuiGraphicsExtractor graphics, int x, int y, int color) {
		int atlasWidth = SIZE * values().length;
		graphics.blit(RenderPipelines.GUI_TEXTURED, ATLAS, x, y, ordinal() * SIZE, 0f, SIZE, SIZE, atlasWidth, SIZE, color);
	}

	public static void drawLogo(GuiGraphicsExtractor graphics, int x, int y, int size) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, LOGO, x, y, 0f, 0f, size, size, 64, 64, 64, 64);
	}
}

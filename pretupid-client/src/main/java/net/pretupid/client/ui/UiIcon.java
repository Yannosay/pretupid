package net.pretupid.client.ui;

import net.pretupid.client.platform.Gfx;
import net.minecraft.client.Minecraft;
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
	private static final int[] LOGO_SIZES = {16, 24, 32, 48, 64, 96, 128, 192, 256};
	private static final Identifier[] LOGOS = new Identifier[LOGO_SIZES.length];

	static {
		for (int i = 0; i < LOGO_SIZES.length; i++) {
			LOGOS[i] = Identifier.fromNamespaceAndPath(PretupidClient.MOD_ID, "textures/gui/logo/" + LOGO_SIZES[i] + ".png");
		}
	}

	public void draw(Gfx graphics, int x, int y, int color) {
		int atlasWidth = SIZE * values().length;
		graphics.blit(ATLAS, x, y, ordinal() * SIZE, 0f, SIZE, SIZE, atlasWidth, SIZE, color);
	}

	public static void drawLogo(Gfx graphics, int x, int y, int size) {
		drawLogo(graphics, x, y, size, -1);
	}

	public static void drawLogo(Gfx graphics, int x, int y, int size, int color) {
		int pixels = size * Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
		int index = LOGO_SIZES.length - 1;
		for (int i = 0; i < LOGO_SIZES.length; i++) {
			if (LOGO_SIZES[i] >= pixels) {
				index = i;
				break;
			}
		}
		int texture = LOGO_SIZES[index];
		graphics.blit(LOGOS[index], x, y, 0f, 0f, size, size, texture, texture, texture, texture, color);
	}
}

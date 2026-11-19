package net.pretupid.client.ui;

import net.pretupid.client.platform.Gfx;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.pretupid.client.util.Colors;

public final class Draw {
	private static final int[][] ROUND_INSETS = new int[9][];

	static {
		for (int radius = 0; radius < ROUND_INSETS.length; radius++) {
			int[] insets = new int[radius];
			for (int row = 0; row < radius; row++) {
				double dy = radius - row - 0.5;
				insets[row] = (int) Math.round(radius - Math.sqrt(Math.max(0.0, radius * radius - dy * dy)));
			}
			ROUND_INSETS[radius] = insets;
		}
	}

	private Draw() {
	}

	public static void rect(Gfx g, int x, int y, int w, int h, int color) {
		if (w <= 0 || h <= 0 || Colors.alpha(color) == 0) {
			return;
		}
		g.fill(x, y, x + w, y + h, color);
	}

	public static void chamfer(Gfx g, int x, int y, int w, int h, int color) {
		if (w < 3 || h < 3) {
			rect(g, x, y, w, h, color);
			return;
		}
		rect(g, x + 1, y, w - 2, 1, color);
		rect(g, x, y + 1, w, h - 2, color);
		rect(g, x + 1, y + h - 1, w - 2, 1, color);
	}

	public static void rounded(Gfx g, int x, int y, int w, int h, int radius, int color) {
		int r = Math.clamp(radius, 0, Math.min(Math.min(w, h) / 2, ROUND_INSETS.length - 1));
		if (r <= 1) {
			if (r == 1) {
				chamfer(g, x, y, w, h, color);
			} else {
				rect(g, x, y, w, h, color);
			}
			return;
		}
		int[] insets = ROUND_INSETS[r];
		for (int row = 0; row < r; row++) {
			int inset = insets[row];
			rect(g, x + inset, y + row, w - inset * 2, 1, color);
			rect(g, x + inset, y + h - 1 - row, w - inset * 2, 1, color);
		}
		rect(g, x, y + r, w, h - r * 2, color);
	}

	public static void border(Gfx g, int x, int y, int w, int h, int color) {
		rect(g, x + 1, y, w - 2, 1, color);
		rect(g, x + 1, y + h - 1, w - 2, 1, color);
		rect(g, x, y + 1, 1, h - 2, color);
		rect(g, x + w - 1, y + 1, 1, h - 2, color);
	}

	public static void glass(Gfx g, int x, int y, int w, int h, int fill, int border) {
		chamfer(g, x, y, w, h, fill);
		border(g, x, y, w, h, border);
		rect(g, x + 1, y + 1, w - 2, 1, Theme.WINDOW_HIGHLIGHT);
	}

	public static void shadow(Gfx g, int x, int y, int w, int h, int layers, float strength) {
		for (int i = layers; i >= 1; i--) {
			int alpha = Math.round(strength * 40f / i);
			chamfer(g, x - i, y - i + 1, w + i * 2, h + i * 2, Colors.withAlpha(0, alpha));
		}
	}

	public static void verticalGradient(Gfx g, int x, int y, int w, int h, int top, int bottom) {
		if (w > 0 && h > 0) {
			g.fillGradient(x, y, x + w, y + h, top, bottom);
		}
	}

	public static void text(Gfx g, Font font, String text, int x, int y, int color) {
		if (Colors.alpha(color) > 3) {
			g.text(font, text, x, y, color, false);
		}
	}

	public static void text(Gfx g, Font font, Component text, int x, int y, int color) {
		if (Colors.alpha(color) > 3) {
			g.text(font, text, x, y, color, false);
		}
	}

	public static void textShadow(Gfx g, Font font, Component text, int x, int y, int color) {
		if (Colors.alpha(color) > 3) {
			g.text(font, text, x, y, color, true);
		}
	}

	public static void text(Gfx g, Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow) {
		if (Colors.alpha(color) > 3) {
			g.text(font, text, x, y, color, shadow);
		}
	}

	public static void centered(Gfx g, Font font, Component text, int centerX, int y, int color) {
		text(g, font, text, centerX - font.width(text) / 2, y, color);
	}

	public static void scaled(Gfx g, Font font, Component text, float x, float y, float scale, int color, boolean shadow) {
		if (Colors.alpha(color) <= 3) {
			return;
		}
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		g.text(font, text, 0, 0, color, shadow);
		g.pose().popMatrix();
	}

	public static String ellipsize(Font font, String text, int maxWidth) {
		if (font.width(text) <= maxWidth) {
			return text;
		}
		String ellipsis = "...";
		int available = maxWidth - font.width(ellipsis);
		if (available <= 0) {
			return "";
		}
		return font.plainSubstrByWidth(text, available) + ellipsis;
	}

	public static Component ellipsize(Font font, Component text, int maxWidth) {
		if (font.width(text) <= maxWidth) {
			return text;
		}
		return Component.literal(ellipsize(font, text.getString(), maxWidth)).withStyle(text.getStyle());
	}

	public static void trash(Gfx g, int x, int y, int color) {
		rect(g, x + 2, y, 3, 1, color);
		rect(g, x, y + 1, 7, 1, color);
		rect(g, x + 1, y + 3, 5, 1, color);
		rect(g, x + 1, y + 3, 1, 5, color);
		rect(g, x + 5, y + 3, 1, 5, color);
		rect(g, x + 1, y + 7, 5, 1, color);
		rect(g, x + 3, y + 4, 1, 3, color);
	}
}

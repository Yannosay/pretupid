package net.pretupid.client.ui;

import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.pretupid.client.setting.ColorSetting;
import net.pretupid.client.util.Colors;

public final class ColorPickerPopup implements Popup {
	private static final int[] PRESETS = {
		0xFFFFFFFF, 0xFF4A9EFF, 0xFF7C5CFF, 0xFFE879F9, 0xFFF87171,
		0xFFFB923C, 0xFFFACC15, 0xFF4ADE80, 0xFF2DD4BF, 0xFF1F2937
	};
	private static final int WIDTH = 148;
	private static final int SQUARE = 96;
	private static final int SQUARE_HEIGHT = 64;

	private final PretupidScreen screen;
	private final ColorSetting setting;
	private final int anchorRight;
	private final int anchorTop;
	private final TextInput hexInput;
	private float hue;
	private float saturation;
	private float value;
	private int alpha;

	public ColorPickerPopup(PretupidScreen screen, ColorSetting setting, int anchorRight, int anchorTop) {
		this.screen = screen;
		this.setting = setting;
		this.anchorRight = anchorRight;
		this.anchorTop = anchorTop;
		float[] hsv = Colors.toHsv(setting.displayArgb());
		this.hue = hsv[0];
		this.saturation = hsv[1];
		this.value = hsv[2];
		this.alpha = Colors.alpha(setting.displayArgb());
		this.hexInput = new TextInput(9, cp -> cp == '#' || Character.digit(cp, 16) >= 0);
		this.hexInput.setValueSilently(hexText());
		this.hexInput.onChange(text -> {
			int parsed = Colors.parseHex(text, Integer.MIN_VALUE);
			if (parsed != Integer.MIN_VALUE && (text.replace("#", "").length() == 6 || text.replace("#", "").length() == 8)) {
				float[] next = Colors.toHsv(parsed);
				hue = next[0];
				saturation = next[1];
				value = next[2];
				if (setting.allowsAlpha() && text.replace("#", "").length() == 8) {
					alpha = Colors.alpha(parsed);
				}
				commit(false);
			}
		});
	}

	private String hexText() {
		return setting.allowsAlpha() ? Colors.toHex(setting.displayArgb()) : Colors.toRgbHex(setting.displayArgb());
	}

	private void commit(boolean refreshHex) {
		int rgb = Colors.hsv(hue, saturation, value);
		setting.setArgb(Colors.withAlpha(rgb, setting.allowsAlpha() ? alpha : 255));
		if (refreshHex) {
			hexInput.setValueSilently(hexText());
		}
	}

	private int height() {
		return 8 + SQUARE_HEIGHT + 6 + 14 + 6 + 12 + 8 + (setting.canFollowAccent() ? 18 : 0);
	}

	@Override
	public void draw(UiContext ui, int screenWidth, int screenHeight) {
		int h = height();
		int x = Math.clamp(anchorRight - WIDTH, 6, screenWidth - WIDTH - 6);
		int y = anchorTop + h > screenHeight - 6 ? anchorTop - h - 20 : anchorTop;
		y = Math.clamp(y, 6, Math.max(6, screenHeight - h - 6));
		float open = Easing.outBack(ui.animateFrom("color.open." + System.identityHashCode(this), 0f, 1f, 14f));
		var g = ui.g();
		g.pose().pushMatrix();
		g.pose().translate(x + WIDTH / 2f, y);
		g.pose().scale(0.9f + 0.1f * open, 0.9f + 0.1f * open);
		g.pose().translate(-(x + WIDTH / 2f), -y);
		Draw.shadow(g, x, y, WIDTH, h, 4, 1f);
		Draw.glass(g, x, y, WIDTH, h, Theme.POPUP, Theme.WINDOW_BORDER);
		ui.hit(x, y, WIDTH, h, new UiContext.Handler() {
			@Override
			public boolean click(double mouseX, double mouseY, int button) {
				return true;
			}

			@Override
			public boolean pointer() {
				return false;
			}

			@Override
			public boolean clickSound() {
				return false;
			}
		});
		int sx = x + 8;
		int sy = y + 8;
		drawSaturationValue(ui, sx, sy);
		int hueX = sx + SQUARE + 6;
		drawHue(ui, hueX, sy);
		if (setting.allowsAlpha()) {
			drawAlpha(ui, hueX + 16, sy);
		}
		int rowY = sy + SQUARE_HEIGHT + 6;
		hexInput.draw(ui, sx, rowY, 64, 14, Component.empty());
		Draw.text(g, ui.font(), Component.translatable("pretupid.ui.chroma"), sx + 72, rowY + 3, Theme.TEXT_MUTED);
		Widgets.toggle(ui, "color.chroma", x + WIDTH - 28, rowY + 2, setting.chroma(), () -> setting.setChroma(!setting.chroma()));
		int presetY = rowY + 20;
		int size = 10;
		int gap = (WIDTH - 16 - PRESETS.length * size) / (PRESETS.length - 1);
		for (int i = 0; i < PRESETS.length; i++) {
			int preset = PRESETS[i];
			int px = sx + i * (size + gap);
			boolean hover = ui.interactive(px, presetY, size, size, UiContext.Handler.onClick(() -> {
				float[] hsv = Colors.toHsv(preset);
				hue = hsv[0];
				saturation = hsv[1];
				value = hsv[2];
				commit(true);
			}));
			Draw.chamfer(g, px, presetY, size, size, preset);
			Draw.border(g, px, presetY, size, size, hover ? 0xFFFFFFFF : 0x40FFFFFF);
		}
		if (setting.canFollowAccent()) {
			int followY = presetY + size + 8;
			Draw.text(g, ui.font(), Component.translatable(setting.followLabelKey()), sx, followY + 3, Theme.TEXT_MUTED);
			Widgets.toggle(ui, "color.follow", x + WIDTH - 28, followY + 2, setting.followsAccent(), () -> {
				if (setting.followsAccent()) {
					setting.setArgb(setting.displayArgb());
				} else {
					setting.reset();
				}
				float[] hsv = Colors.toHsv(setting.displayArgb());
				hue = hsv[0];
				saturation = hsv[1];
				value = hsv[2];
				hexInput.setValueSilently(hexText());
			});
		}
		g.pose().popMatrix();
	}

	private void drawSaturationValue(UiContext ui, int x, int y) {
		var g = ui.g();
		for (int column = 0; column < SQUARE; column++) {
			float s = column / (float) (SQUARE - 1);
			g.fillGradient(x + column, y, x + column + 1, y + SQUARE_HEIGHT, Colors.hsv(hue, s, 1f), 0xFF000000);
		}
		Draw.border(g, x - 1, y - 1, SQUARE + 2, SQUARE_HEIGHT + 2, 0x40FFFFFF);
		ui.interactive(x, y, SQUARE, SQUARE_HEIGHT, dragHandler((mx, my) -> {
			saturation = (float) Math.clamp((mx - x) / (SQUARE - 1), 0.0, 1.0);
			value = 1f - (float) Math.clamp((my - y) / (SQUARE_HEIGHT - 1), 0.0, 1.0);
			commit(true);
		}));
		int cx = x + Math.round(saturation * (SQUARE - 1));
		int cy = y + Math.round((1f - value) * (SQUARE_HEIGHT - 1));
		Draw.border(g, cx - 2, cy - 2, 5, 5, 0xFFFFFFFF);
		Draw.border(g, cx - 3, cy - 3, 7, 7, 0x80000000);
	}

	private void drawHue(UiContext ui, int x, int y) {
		var g = ui.g();
		int segments = 6;
		for (int i = 0; i < segments; i++) {
			int top = y + i * SQUARE_HEIGHT / segments;
			int bottom = y + (i + 1) * SQUARE_HEIGHT / segments;
			g.fillGradient(x, top, x + 10, bottom, Colors.hsv(i / (float) segments, 1f, 1f), Colors.hsv((i + 1) / (float) segments, 1f, 1f));
		}
		Draw.border(g, x - 1, y - 1, 12, SQUARE_HEIGHT + 2, 0x40FFFFFF);
		ui.interactive(x - 2, y, 14, SQUARE_HEIGHT, dragHandler((mx, my) -> {
			hue = (float) Math.clamp((my - y) / SQUARE_HEIGHT, 0.0, 0.999);
			commit(true);
		}));
		int marker = y + Math.round(hue * SQUARE_HEIGHT);
		Draw.rect(g, x - 2, marker - 1, 14, 3, 0xFFFFFFFF);
		Draw.rect(g, x - 1, marker, 12, 1, 0xFF000000);
	}

	private void drawAlpha(UiContext ui, int x, int y) {
		var g = ui.g();
		Widgets.checker(ui, x, y, 10, SQUARE_HEIGHT);
		int rgb = Colors.hsv(hue, saturation, value);
		g.fillGradient(x, y, x + 10, y + SQUARE_HEIGHT, Colors.withAlpha(rgb, 255), Colors.withAlpha(rgb, 0));
		Draw.border(g, x - 1, y - 1, 12, SQUARE_HEIGHT + 2, 0x40FFFFFF);
		ui.interactive(x - 2, y, 14, SQUARE_HEIGHT, dragHandler((mx, my) -> {
			alpha = Math.round(255f * (1f - (float) Math.clamp((my - y) / SQUARE_HEIGHT, 0.0, 1.0)));
			commit(true);
		}));
		int marker = y + Math.round((1f - alpha / 255f) * SQUARE_HEIGHT);
		Draw.rect(g, x - 2, marker - 1, 14, 3, 0xFFFFFFFF);
		Draw.rect(g, x - 1, marker, 12, 1, 0xFF000000);
	}

	private static UiContext.Handler dragHandler(java.util.function.BiConsumer<Double, Double> update) {
		return new UiContext.Handler() {
			@Override
			public boolean click(double mouseX, double mouseY, int button) {
				if (button != 0) {
					return false;
				}
				update.accept(mouseX, mouseY);
				return true;
			}

			@Override
			public void drag(double mouseX, double mouseY) {
				update.accept(mouseX, mouseY);
			}

			@Override
			public boolean clickSound() {
				return false;
			}
		};
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isConfirmation()) {
			screen.closePopup();
			return true;
		}
		return false;
	}
}

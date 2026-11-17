package net.pretupid.client.ui;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.pretupid.client.setting.BooleanSetting;
import net.pretupid.client.setting.ColorSetting;
import net.pretupid.client.setting.EnumSetting;
import net.pretupid.client.setting.KeySetting;
import net.pretupid.client.setting.NumberSetting;
import net.pretupid.client.setting.Setting;
import net.pretupid.client.setting.TextSetting;
import net.pretupid.client.util.Colors;

public final class Widgets {
	public static final int ROW_HEIGHT = 22;
	public static final int CONTROL_WIDTH = 118;
	private static final Map<TextSetting, TextInput> TEXT_INPUTS = new WeakHashMap<>();

	private Widgets() {
	}

	public enum ButtonStyle {
		PRIMARY,
		SECONDARY,
		GHOST,
		DANGER
	}

	public static boolean button(UiContext ui, String id, int x, int y, int w, int h, Component label, ButtonStyle style, Runnable action) {
		boolean hover = ui.interactive(x, y, w, h, UiContext.Handler.onClick(action));
		float t = ui.animate("button." + id, hover ? 1f : 0f, 16f);
		int base;
		int hoverColor;
		int textColor = Theme.TEXT;
		switch (style) {
			case PRIMARY -> {
				base = Theme.accent(220);
				hoverColor = Colors.brighten(Theme.accent(255), 0.15f);
			}
			case DANGER -> {
				base = Colors.withAlpha(Theme.DANGER, 60);
				hoverColor = Colors.withAlpha(Theme.DANGER, 120);
				textColor = Colors.brighten(Theme.DANGER, 0.3f);
			}
			case GHOST -> {
				base = 0x00FFFFFF;
				hoverColor = Theme.SURFACE_HOVER;
				textColor = Theme.TEXT_MUTED;
			}
			default -> {
				base = Theme.SURFACE;
				hoverColor = Theme.SURFACE_HOVER;
			}
		}
		Draw.chamfer(ui.g(), x, y, w, h, Colors.lerp(base, hoverColor, t));
		if (style == ButtonStyle.SECONDARY) {
			Draw.border(ui.g(), x, y, w, h, Theme.SURFACE_BORDER);
		}
		if (style == ButtonStyle.PRIMARY) {
			Draw.rect(ui.g(), x + 1, y + h - 1, w - 2, 1, Colors.withAlpha(0, 70));
		}
		fittedLabel(ui, label, x, y, w, h, Colors.lerp(textColor, Theme.TEXT, t));
		return hover;
	}

	private static final float MIN_LABEL_SCALE = 0.7f;

	public static void fittedLabel(UiContext ui, Component label, int x, int y, int w, int h, int color) {
		int available = Math.max(1, w - 6);
		int natural = ui.font().width(label);
		if (natural <= available) {
			Draw.text(ui.g(), ui.font(), label, x + (w - natural) / 2, y + (h - 8) / 2, color);
			return;
		}
		float scale = Math.max(MIN_LABEL_SCALE, available / (float) natural);
		Component fitted = scale > MIN_LABEL_SCALE ? label : Draw.ellipsize(ui.font(), label, Math.round(available / scale));
		float width = ui.font().width(fitted) * scale;
		Draw.scaled(ui.g(), ui.font(), fitted, x + (w - width) / 2f, y + (h - 8 * scale) / 2f, scale, color, false);
	}

	public static boolean iconButton(UiContext ui, String id, int x, int y, int w, int h, UiIcon icon, Runnable action) {
		boolean hover = ui.interactive(x, y, w, h, UiContext.Handler.onClick(action));
		float t = ui.animate("icon_button." + id, hover ? 1f : 0f, 16f);
		Draw.chamfer(ui.g(), x, y, w, h, Colors.lerp(Theme.SURFACE, Theme.SURFACE_HOVER, t));
		Draw.border(ui.g(), x, y, w, h, Theme.SURFACE_BORDER);
		icon.draw(ui.g(), x + (w - UiIcon.SIZE) / 2, y + (h - UiIcon.SIZE) / 2, Colors.lerp(Theme.TEXT_MUTED, Theme.TEXT, t));
		return hover;
	}

	public static void toggle(UiContext ui, String id, int x, int y, boolean value, Runnable action) {
		int w = 20;
		int h = 10;
		boolean hover = ui.interactive(x - 2, y - 2, w + 4, h + 4, UiContext.Handler.onClick(action));
		float t = ui.animate("toggle." + id, value ? 1f : 0f, 18f);
		int track = Colors.lerp(Theme.TOGGLE_OFF, Theme.accent(), t);
		if (hover) {
			track = Colors.brighten(track, 0.12f);
		}
		Draw.rounded(ui.g(), x, y, w, h, 3, track);
		int knobX = Math.round(x + 1 + t * (w - 10));
		Draw.chamfer(ui.g(), knobX, y + 1, 8, 8, 0xFFFFFFFF);
		Draw.rect(ui.g(), knobX + 1, y + 7, 6, 1, 0x30000000);
	}

	public static void slider(UiContext ui, String id, int x, int y, int w, NumberSetting setting) {
		int h = 12;
		UiContext.Handler handler = new UiContext.Handler() {
			@Override
			public boolean click(double mouseX, double mouseY, int button) {
				if (button == 1) {
					setting.reset();
					return true;
				}
				update(mouseX);
				return true;
			}

			@Override
			public void drag(double mouseX, double mouseY) {
				update(mouseX);
			}

			@Override
			public boolean scroll(double amount) {
				setting.nudge(amount > 0 ? 1 : -1);
				return true;
			}

			@Override
			public boolean clickSound() {
				return false;
			}

			private void update(double mouseX) {
				setting.setProgress((mouseX - x - 2) / (double) (w - 4));
			}
		};
		boolean hover = ui.interactive(x, y, w, h, handler);
		float progress = ui.animate("slider." + id, (float) setting.progress(), 24f);
		float emphasis = ui.animate("slider.hover." + id, hover ? 1f : 0f, 14f);
		int trackY = y + h / 2 - 1;
		Draw.rect(ui.g(), x, trackY, w, 3, 0x30FFFFFF);
		int filled = Math.round((w - 4) * progress);
		Draw.rect(ui.g(), x, trackY, filled + 2, 3, Theme.accent());
		int knobX = x + filled;
		Draw.chamfer(ui.g(), knobX, y + 1, 4, h - 2, Colors.lerp(0xFFE6E9EF, 0xFFFFFFFF, emphasis));
		if (hover) {
			ui.setCursor(com.mojang.blaze3d.platform.cursor.CursorTypes.RESIZE_EW);
		}
	}

	public static <E extends Enum<E>> void cycler(UiContext ui, String id, int x, int y, int w, EnumSetting<E> setting) {
		int h = 14;
		boolean hover = ui.interactive(x, y, w, h, new UiContext.Handler() {
			@Override
			public boolean click(double mouseX, double mouseY, int button) {
				boolean forward = button == 0 && mouseX >= x + w / 3.0;
				setting.cycle(forward);
				return true;
			}

			@Override
			public boolean scroll(double amount) {
				setting.cycle(amount < 0);
				return true;
			}
		});
		float t = ui.animate("cycler." + id, hover ? 1f : 0f, 16f);
		Draw.chamfer(ui.g(), x, y, w, h, Colors.lerp(Theme.SURFACE, Theme.SURFACE_HOVER, t));
		Draw.border(ui.g(), x, y, w, h, Theme.SURFACE_BORDER);
		UiIcon.CHEVRON_LEFT.draw(ui.g(), x + 2, y + 3, Colors.lerp(Theme.TEXT_DIM, Theme.accentText(), t));
		UiIcon.CHEVRON_RIGHT.draw(ui.g(), x + w - 11, y + 3, Colors.lerp(Theme.TEXT_DIM, Theme.accentText(), t));
		Component label = Draw.ellipsize(ui.font(), setting.valueLabel(), w - 24);
		Draw.centered(ui.g(), ui.font(), label, x + w / 2, y + 3, Theme.TEXT);
	}

	public static void keyButton(UiContext ui, PretupidScreen screen, String id, int x, int y, int w, KeySetting setting) {
		int h = 14;
		boolean capturing = screen.isCapturing(setting);
		boolean hover = ui.interactive(x, y, w, h, new UiContext.Handler() {
			@Override
			public boolean click(double mouseX, double mouseY, int button) {
				if (button == 1) {
					setting.set(KeySetting.UNBOUND);
					return true;
				}
				screen.captureKey(setting);
				return true;
			}
		});
		float t = ui.animate("key." + id, hover || capturing ? 1f : 0f, 16f);
		Draw.chamfer(ui.g(), x, y, w, h, Colors.lerp(Theme.SURFACE, Theme.SURFACE_HOVER, t));
		Draw.border(ui.g(), x, y, w, h, capturing ? Theme.accent() : Theme.SURFACE_BORDER);
		Component label;
		int color;
		if (capturing) {
			boolean blink = System.currentTimeMillis() / 400 % 2 == 0;
			label = Component.translatable("pretupid.ui.press_key");
			color = blink ? Theme.accentText() : Theme.TEXT;
		} else {
			label = setting.keyLabel();
			color = setting.isBound() ? Theme.TEXT : Theme.TEXT_DIM;
		}
		Draw.centered(ui.g(), ui.font(), Draw.ellipsize(ui.font(), label, w - 6), x + w / 2, y + 3, color);
	}

	public static void colorSwatch(UiContext ui, PretupidScreen screen, String id, int x, int y, int w, ColorSetting setting) {
		int h = 14;
		boolean hover = ui.interactive(x, y, w, h, UiContext.Handler.onClick(() -> screen.openPopup(new ColorPickerPopup(screen, setting, x + w, y + h + 2))));
		float t = ui.animate("swatch." + id, hover ? 1f : 0f, 16f);
		Draw.chamfer(ui.g(), x, y, w, h, Colors.lerp(Theme.SURFACE, Theme.SURFACE_HOVER, t));
		Draw.border(ui.g(), x, y, w, h, Theme.SURFACE_BORDER);
		int sx = x + 3;
		int sy = y + 3;
		int sw = 16;
		int sh = h - 6;
		checker(ui, sx, sy, sw, sh);
		if (setting.chroma()) {
			for (int i = 0; i < sw; i++) {
				Draw.rect(ui.g(), sx + i, sy, 1, sh, Colors.withAlpha(Colors.chroma(i / (float) sw), Colors.alpha(setting.argb())));
			}
		} else {
			Draw.rect(ui.g(), sx, sy, sw, sh, setting.displayArgb());
		}
		String hex = setting.followsAccent() ? I18n.get(setting.followLabelKey())
			: setting.chroma() ? I18n.get("pretupid.ui.chroma") : (setting.allowsAlpha() ? Colors.toHex(setting.argb()) : Colors.toRgbHex(setting.argb()));
		Draw.text(ui.g(), ui.font(), Draw.ellipsize(ui.font(), hex, w - sw - 10), sx + sw + 4, y + 3, Theme.TEXT_MUTED);
	}

	public static void checker(UiContext ui, int x, int y, int w, int h) {
		Draw.rect(ui.g(), x, y, w, h, 0xFFBFBFBF);
		for (int cy = 0; cy < h; cy += 3) {
			for (int cx = ((cy / 3) % 2) * 3; cx < w; cx += 6) {
				Draw.rect(ui.g(), x + cx, y + cy, Math.min(3, w - cx), Math.min(3, h - cy), 0xFF8A8A8A);
			}
		}
	}

	public static TextInput inputFor(TextSetting setting) {
		return TEXT_INPUTS.computeIfAbsent(setting, key -> {
			TextInput input = new TextInput(key.maxLength());
			input.setValueSilently(key.get());
			input.onChange(key::set);
			return input;
		});
	}

	public static int settingRow(UiContext ui, PretupidScreen screen, Setting<?> setting, int x, int y, int w) {
		String id = setting.translationKey();
		boolean rowHover = ui.hovered(x, y, w, ROW_HEIGHT);
		float t = ui.animate("row." + id, rowHover ? 1f : 0f, 14f);
		Draw.chamfer(ui.g(), x, y, w, ROW_HEIGHT, Colors.lerp(0x00FFFFFF, 0x0FFFFFFF, t));
		int controlWidth = Math.min(CONTROL_WIDTH, Math.max(60, w / 2 - 6));
		int controlX = x + w - controlWidth - 6;
		int labelWidth = controlX - x - 14;
		Component label = Draw.ellipsize(ui.font(), setting.label(), labelWidth);
		Draw.text(ui.g(), ui.font(), label, x + 6, y + 7, Theme.TEXT);
		if (!setting.isDefault()) {
			Draw.rect(ui.g(), x + 2, y + 7, 1, 8, Theme.accent(200));
		}
		String tooltipKey = id + ".tooltip";
		if (rowHover && ui.mouseX() < controlX && Language.getInstance().has(tooltipKey)) {
			ui.tooltip(Component.translatable(tooltipKey));
		}
		int cy = y + (ROW_HEIGHT - 14) / 2;
		switch (setting) {
			case BooleanSetting bool -> toggle(ui, id, x + w - 26, y + 6, bool.enabled(), bool::toggle);
			case NumberSetting number -> {
				String value = number.format();
				int valueWidth = ui.font().width(value);
				int sliderWidth = controlWidth - valueWidth - 6;
				slider(ui, id, controlX, cy + 1, sliderWidth, number);
				Draw.text(ui.g(), ui.font(), value, controlX + controlWidth - valueWidth, y + 7, Theme.TEXT_MUTED);
			}
			case ColorSetting color -> colorSwatch(ui, screen, id, controlX, cy, controlWidth, color);
			case EnumSetting<?> choice -> cycler(ui, id, controlX, cy, controlWidth, choice);
			case KeySetting key -> keyButton(ui, screen, id, controlX, cy, controlWidth, key);
			case TextSetting text -> {
				TextInput input = inputFor(text);
				if (!input.focused() && !input.value().equals(text.get())) {
					input.setValueSilently(text.get());
				}
				input.draw(ui, controlX, cy, controlWidth, 14, Component.empty());
			}
			default -> {
			}
		}
		return ROW_HEIGHT;
	}

	public static void sectionHeader(UiContext ui, Component title, int x, int y, int w) {
		Draw.text(ui.g(), ui.font(), title.copy().withStyle(style -> style.withBold(true)), x + 2, y, Theme.accentText());
		int textWidth = ui.font().width(title) + 8;
		Draw.rect(ui.g(), x + textWidth, y + 4, w - textWidth - 2, 1, Theme.DIVIDER);
	}
}

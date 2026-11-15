package net.pretupid.client.module.hud;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.hud.HudModule;
import net.pretupid.client.hud.HudPosition;
import net.pretupid.client.setting.BooleanSetting;
import net.pretupid.client.setting.ColorSetting;
import net.pretupid.client.setting.NumberSetting;
import net.pretupid.client.ui.Draw;
import net.pretupid.client.ui.Easing;
import net.pretupid.client.util.Colors;

public final class KeystrokesHud extends HudModule {
	private final BooleanSetting showMouse = add(new BooleanSetting("show_mouse", true));
	private final BooleanSetting showCps = add(new BooleanSetting("show_cps", true));
	private final BooleanSetting showSpace = add(new BooleanSetting("show_space", true));
	private final NumberSetting keySize = add(new NumberSetting("key_size", 22, 16, 32, 1, "px"));
	private final NumberSetting gap = add(new NumberSetting("gap", 2, 0, 6, 1, "px"));
	private final NumberSetting fadeSpeed = add(new NumberSetting("fade_speed", 1.0, 0.2, 3.0, 0.1, "x"));
	private final ColorSetting keyColor = add(new ColorSetting("key_color", 0x6A0B0E14, true));
	private final ColorSetting pressedColor = add(new ColorSetting("pressed_color", 0xD0F2F4F8, true));
	private final ColorSetting textColor = add(new ColorSetting("text_color", 0xFFFFFFFF));
	private final ColorSetting pressedTextColor = add(new ColorSetting("pressed_text_color", 0xFF0B0E14));
	private final float[] press = new float[7];
	private long lastFrame;

	public KeystrokesHud() {
		super("keystrokes", "honeycomb", false, new HudPosition(HudPosition.Anchor.END, 4, HudPosition.Anchor.END, 4));
	}

	@Override
	protected boolean backgroundByDefault() {
		return false;
	}

	private int size() {
		return keySize.intValue();
	}

	private int spacing() {
		return gap.intValue();
	}

	@Override
	public int width(Font font, Minecraft minecraft, boolean preview) {
		return size() * 3 + spacing() * 2;
	}

	@Override
	public int height(Font font, Minecraft minecraft, boolean preview) {
		int height = size() * 2 + spacing();
		if (showMouse.enabled()) {
			height += spacing() + size();
		}
		if (showSpace.enabled()) {
			height += spacing() + Math.max(8, size() / 2);
		}
		return height;
	}

	@Override
	public void render(GuiGraphicsExtractor graphics, Font font, Minecraft minecraft, boolean preview) {
		long now = System.nanoTime();
		float delta = lastFrame == 0L ? 0f : Math.min(0.1f, (now - lastFrame) / 1_000_000_000f);
		lastFrame = now;
		Options options = minecraft.options;
		int s = size();
		int g = spacing();
		int full = width(font, minecraft, preview);
		drawKey(graphics, font, 0, options.keyUp, s + g, 0, s, s, null, delta);
		drawKey(graphics, font, 1, options.keyLeft, 0, s + g, s, s, null, delta);
		drawKey(graphics, font, 2, options.keyDown, s + g, s + g, s, s, null, delta);
		drawKey(graphics, font, 3, options.keyRight, (s + g) * 2, s + g, s, s, null, delta);
		int y = (s + g) * 2;
		if (showMouse.enabled()) {
			int half = (full - g) / 2;
			var clicks = PretupidClient.get().clicks();
			drawKey(graphics, font, 4, options.keyAttack, 0, y, half, s, showCps.enabled() ? clicks.leftCps() + " CPS" : null, delta);
			drawKey(graphics, font, 5, options.keyUse, half + g, y, full - half - g, s, showCps.enabled() ? clicks.rightCps() + " CPS" : null, delta);
			y += s + g;
		}
		if (showSpace.enabled()) {
			int height = Math.max(8, s / 2);
			float t = animate(6, options.keyJump.isDown(), delta);
			Draw.chamfer(graphics, 0, y, full, height, Colors.lerp(keyColor.resolve(), pressedColor.resolve(), t));
			int barColor = Colors.lerp(textColor.resolve(), pressedTextColor.resolve(), t);
			int barWidth = Math.max(6, full / 3);
			Draw.rect(graphics, (full - barWidth) / 2, y + height / 2, barWidth, 1, barColor);
		}
	}

	private float animate(int index, boolean down, float delta) {
		float target = down ? 1f : 0f;
		float rate = (down ? 40f : 9f) * fadeSpeed.floatValue();
		press[index] = Easing.approach(press[index], target, rate, delta);
		return press[index];
	}

	private void drawKey(GuiGraphicsExtractor graphics, Font font, int index, KeyMapping mapping, int x, int y, int w, int h, String subtitle, float delta) {
		float t = animate(index, mapping.isDown(), delta);
		Draw.chamfer(graphics, x, y, w, h, Colors.lerp(keyColor.resolve(index * 0.05f), pressedColor.resolve(index * 0.05f), t));
		int color = Colors.lerp(textColor.resolve(index * 0.05f), pressedTextColor.resolve(index * 0.05f), t);
		boolean shadow = textShadow.enabled() && t < 0.5f;
		String label = keyLabel(mapping, index);
		label = Draw.ellipsize(font, label, w - 4);
		if (subtitle == null) {
			graphics.text(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2, color, shadow);
			return;
		}
		int top = y + Math.max(2, (h - 17) / 2);
		graphics.text(font, label, x + (w - font.width(label)) / 2, top, color, shadow);
		Draw.scaled(graphics, font, Component.literal(subtitle), x + (w - font.width(subtitle) * 0.75f) / 2f, top + 10, 0.75f, Colors.multiplyAlpha(color, 0.85f), shadow);
	}

	private static String keyLabel(KeyMapping mapping, int index) {
		if (index == 4) {
			return "LMB";
		}
		if (index == 5) {
			return "RMB";
		}
		String text = mapping.getTranslatedKeyMessage().getString();
		return text.length() > 3 ? text.substring(0, 3) : text;
	}
}

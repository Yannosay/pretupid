package net.pretupid.client.module.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.language.I18n;
import net.pretupid.client.hud.HudModule;
import net.pretupid.client.hud.HudPosition;
import net.pretupid.client.setting.BooleanSetting;
import net.pretupid.client.setting.ColorSetting;
import net.pretupid.client.setting.NumberSetting;
import net.pretupid.client.ui.Draw;
import net.pretupid.client.util.Colors;

public final class DirectionHud extends HudModule {
	private static final String[] CARDINALS = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};

	private final NumberSetting stripWidth = add(new NumberSetting("strip_width", 140, 60, 260, 2, "px"));
	private final NumberSetting fieldOfView = add(new NumberSetting("field_of_view", 120, 60, 240, 5, "°"));
	private final BooleanSetting showDegrees = add(new BooleanSetting("show_degrees", true));
	private final ColorSetting markerColor = shared(new ColorSetting("label_color", 0xFF4A9EFF).followingModuleText());
	private final ColorSetting textColor = shared(new ColorSetting("value_color", 0xFFFFFFFF));

	public DirectionHud() {
		super("direction", "recovery_compass_00", false, new HudPosition(HudPosition.Anchor.CENTER, 0, HudPosition.Anchor.START, 2));
	}

	@Override
	public int width(Font font, Minecraft minecraft, boolean preview) {
		return stripWidth.intValue();
	}

	@Override
	public int height(Font font, Minecraft minecraft, boolean preview) {
		return showDegrees.enabled() ? 26 : 16;
	}

	@Override
	public void render(GuiGraphicsExtractor graphics, Font font, Minecraft minecraft, boolean preview) {
		int width = stripWidth.intValue();
		int stripHeight = 16;
		drawBackground(graphics, width, stripHeight);
		float yaw = minecraft.player == null ? 0f : minecraft.player.getYRot();
		float heading = ((yaw + 180f) % 360f + 360f) % 360f;
		float fov = fieldOfView.floatValue();
		float pixelsPerDegree = width / fov;
		int center = width / 2;
		boolean shadow = textShadow.enabled();
		int text = textColor.resolve();
		for (int degree = 0; degree < 360; degree += 15) {
			float delta = degree - heading;
			delta = ((delta + 540f) % 360f) - 180f;
			if (Math.abs(delta) > fov / 2f) {
				continue;
			}
			int x = Math.round(center + delta * pixelsPerDegree);
			float fade = 1f - Math.abs(delta) / (fov / 2f);
			int color = Colors.multiplyAlpha(text, 0.25f + 0.75f * fade);
			if (degree % 45 == 0) {
				String label = I18n.get("pretupid.direction.short." + CARDINALS[degree / 45]);
				int labelColor = degree == 0 ? Colors.multiplyAlpha(markerColor.resolve(), 0.35f + 0.65f * fade) : color;
				graphics.text(font, label, x - font.width(label) / 2, 4, labelColor, shadow);
			} else {
				Draw.rect(graphics, x, 6, 1, 4, Colors.multiplyAlpha(color, 0.7f));
			}
		}
		int marker = markerColor.resolve();
		Draw.rect(graphics, center, 0, 1, 3, marker);
		Draw.rect(graphics, center, stripHeight - 3, 1, 3, marker);
		if (showDegrees.enabled()) {
			String degrees = Math.round(heading) % 360 + "°";
			int boxWidth = font.width(degrees) + 6;
			int boxX = center - boxWidth / 2;
			if (background.enabled()) {
				Draw.chamfer(graphics, boxX, stripHeight + 1, boxWidth, 9, backgroundColor.resolve());
			}
			graphics.text(font, degrees, boxX + 3, stripHeight + 2, marker, shadow);
		}
	}
}

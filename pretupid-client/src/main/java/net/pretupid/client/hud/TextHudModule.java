package net.pretupid.client.hud;

import net.pretupid.client.platform.Gfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.resources.language.I18n;
import net.pretupid.client.module.Category;
import net.pretupid.client.setting.BooleanSetting;
import net.pretupid.client.setting.ColorSetting;
import net.pretupid.client.setting.EnumSetting;

public abstract class TextHudModule extends HudModule {
	public enum Brackets {
		NONE("", ""),
		SQUARE("[", "]"),
		ROUND("(", ")"),
		ANGLE("<", ">");

		private final String open;
		private final String close;

		Brackets(String open, String close) {
			this.open = open;
			this.close = close;
		}
	}

	protected final BooleanSetting showLabel;
	protected final EnumSetting<Brackets> brackets;
	protected final ColorSetting labelColor;
	protected final ColorSetting valueColor;
	private int stableWidth;
	private long stableWidthUntil;

	protected TextHudModule(String id, Category category, String icon, boolean enabledByDefault, HudPosition position) {
		super(id, category, icon, enabledByDefault, position);
		this.showLabel = shared(new BooleanSetting("show_label", true));
		this.brackets = shared(new EnumSetting<>("brackets", Brackets.NONE));
		this.labelColor = shared(new ColorSetting("label_color", 0xFF8FC4FF).followingModuleText());
		this.valueColor = shared(new ColorSetting("value_color", 0xFFFFFFFF));
	}

	protected TextHudModule(String id, String icon, boolean enabledByDefault, HudPosition position) {
		this(id, Category.HUD, icon, enabledByDefault, position);
	}

	protected String label() {
		return I18n.get("pretupid.hud.label." + id());
	}

	protected abstract String value(Minecraft minecraft, boolean preview);

	protected int valueColor(Minecraft minecraft, boolean preview) {
		return valueColor.resolve(0.15f);
	}

	private String prefix() {
		String open = brackets.get().open;
		return showLabel.enabled() ? open + label() + ": " : open;
	}

	private String suffix() {
		return brackets.get().close;
	}

	@Override
	public int width(Font font, Minecraft minecraft, boolean preview) {
		int textWidth = font.width(prefix()) + font.width(value(minecraft, preview)) + font.width(suffix());
		long now = System.currentTimeMillis();
		if (textWidth >= stableWidth || now > stableWidthUntil) {
			if (textWidth != stableWidth) {
				stableWidthUntil = now + 1500L;
			}
			stableWidth = textWidth;
		}
		return stableWidth + padding() * 2;
	}

	@Override
	public int height(Font font, Minecraft minecraft, boolean preview) {
		return font.lineHeight - 1 + padding() * 2;
	}

	@Override
	public void render(Gfx graphics, Font font, Minecraft minecraft, boolean preview) {
		int width = width(font, minecraft, preview);
		int height = height(font, minecraft, preview);
		drawBackground(graphics, width, height);
		String prefix = prefix();
		String value = value(minecraft, preview);
		String suffix = suffix();
		int content = font.width(prefix) + font.width(value) + font.width(suffix);
		int x = padding() + (width - padding() * 2 - content) / 2;
		int y = padding();
		boolean shadow = textShadow.enabled();
		int labelArgb = labelColor.resolve(0f);
		graphics.text(font, prefix, x, y, labelArgb, shadow);
		x += font.width(prefix);
		graphics.text(font, value, x, y, valueColor(minecraft, preview), shadow);
		x += font.width(value);
		graphics.text(font, suffix, x, y, labelArgb, shadow);
	}
}

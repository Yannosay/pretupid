package net.pretupid.client.module.hud;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.locale.Language;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.pretupid.client.hud.HudModule;
import net.pretupid.client.hud.HudPosition;
import net.pretupid.client.setting.BooleanSetting;
import net.pretupid.client.setting.ColorSetting;
import net.pretupid.client.util.Colors;

public final class PotionHud extends HudModule {
	private static final int ICON = 18;

	private final BooleanSetting showIcons = add(new BooleanSetting("show_icons", true));
	private final BooleanSetting effectColors = add(new BooleanSetting("effect_colors", true));
	private final BooleanSetting blinkEnding = add(new BooleanSetting("blink_ending", true));
	private final BooleanSetting hideInfinite = add(new BooleanSetting("hide_infinite", false));
	private final ColorSetting nameColor = shared(new ColorSetting("label_color", 0xFFFFFFFF));
	private final ColorSetting durationColor = shared(new ColorSetting("value_color", 0xFFA3ABB9));

	public PotionHud() {
		super("potions", "potion", false, new HudPosition(HudPosition.Anchor.START, 4, HudPosition.Anchor.CENTER, 0));
	}

	@Override
	protected boolean backgroundByDefault() {
		return false;
	}

	private List<MobEffectInstance> effects(Minecraft minecraft, boolean preview) {
		List<MobEffectInstance> effects = new ArrayList<>();
		if (minecraft.player != null) {
			for (MobEffectInstance effect : minecraft.player.getActiveEffects()) {
				if (!(hideInfinite.enabled() && effect.isInfiniteDuration())) {
					effects.add(effect);
				}
			}
		}
		if (effects.isEmpty() && preview) {
			effects.add(new MobEffectInstance(MobEffects.SPEED, 20 * 95, 1));
			effects.add(new MobEffectInstance(MobEffects.STRENGTH, 20 * 42, 0));
		}
		effects.sort(Comparator.comparingInt((MobEffectInstance effect) -> effect.isInfiniteDuration() ? Integer.MAX_VALUE : effect.getDuration()).reversed());
		return effects;
	}

	@Override
	public boolean visible(Minecraft minecraft) {
		return super.visible(minecraft) && !effects(minecraft, false).isEmpty();
	}

	private static String name(MobEffectInstance effect) {
		String name = effect.getEffect().value().getDisplayName().getString();
		int level = effect.getAmplifier() + 1;
		if (level > 1) {
			String key = "enchantment.level." + level;
			name += " " + (Language.getInstance().has(key) ? I18n.get(key) : Integer.toString(level));
		}
		return name;
	}

	private static String duration(MobEffectInstance effect) {
		if (effect.isInfiniteDuration()) {
			return "∞";
		}
		int seconds = effect.getDuration() / 20;
		return seconds >= 3600
			? String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60)
			: String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
	}

	private int rowHeight() {
		return showIcons.enabled() ? ICON + 2 : 10;
	}

	@Override
	public int width(Font font, Minecraft minecraft, boolean preview) {
		int max = 0;
		for (MobEffectInstance effect : effects(minecraft, preview)) {
			int textWidth = showIcons.enabled()
				? Math.max(font.width(name(effect)), font.width(duration(effect)))
				: font.width(name(effect) + "  " + duration(effect));
			max = Math.max(max, textWidth);
		}
		return max + (showIcons.enabled() ? ICON + 3 : 0) + padding() * 2;
	}

	@Override
	public int height(Font font, Minecraft minecraft, boolean preview) {
		int count = Math.max(1, effects(minecraft, preview).size());
		return count * rowHeight() - 2 + padding() * 2;
	}

	@Override
	public void render(GuiGraphicsExtractor graphics, Font font, Minecraft minecraft, boolean preview) {
		drawBackground(graphics, width(font, minecraft, preview), height(font, minecraft, preview));
		int p = padding();
		int y = p;
		boolean shadow = textShadow.enabled();
		for (MobEffectInstance effect : effects(minecraft, preview)) {
			float alpha = 1f;
			if (blinkEnding.enabled() && !effect.isInfiniteDuration() && effect.endsWithin(200)) {
				alpha = 0.45f + 0.55f * (0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 120.0));
			}
			int nameArgb = effectColors.enabled() ? Colors.brighten(0xFF000000 | effect.getEffect().value().getColor(), 0.25f) : nameColor.resolve();
			if (showIcons.enabled()) {
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, Hud.getMobEffectSprite(effect.getEffect()), p, y, ICON, ICON, Colors.withAlpha(0xFFFFFF, Math.round(alpha * 255)));
				int textX = p + ICON + 3;
				graphics.text(font, name(effect), textX, y + 1, Colors.multiplyAlpha(nameArgb, alpha), shadow);
				graphics.text(font, duration(effect), textX, y + 10, Colors.multiplyAlpha(durationColor.resolve(), alpha), shadow);
			} else {
				String name = name(effect) + "  ";
				graphics.text(font, name, p, y, Colors.multiplyAlpha(nameArgb, alpha), shadow);
				graphics.text(font, duration(effect), p + font.width(name), y, Colors.multiplyAlpha(durationColor.resolve(), alpha), shadow);
			}
			y += rowHeight();
		}
	}
}

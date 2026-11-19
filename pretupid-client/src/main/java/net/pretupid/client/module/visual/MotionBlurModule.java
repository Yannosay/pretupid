package net.pretupid.client.module.visual;

import net.pretupid.client.platform.Platform;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.module.Category;
import net.pretupid.client.module.Module;
import net.pretupid.client.setting.BooleanSetting;
import net.pretupid.client.setting.EnumSetting;

public final class MotionBlurModule extends Module {
	public enum Strength {
		LOW,
		MEDIUM,
		HIGH;

		private final Identifier effect = Identifier.fromNamespaceAndPath(PretupidClient.MOD_ID, "motion_blur_" + name().toLowerCase(Locale.ROOT));
	}

	private final EnumSetting<Strength> strength = add(new EnumSetting<>("strength", Strength.MEDIUM));
	private final BooleanSetting pauseInMenus = add(new BooleanSetting("pause_in_menus", true));

	public MotionBlurModule() {
		super("motion_blur", Category.VISUAL, "phantom_membrane", false);
	}

	public Identifier activeEffect(Minecraft minecraft) {
		if (!isEnabled() || minecraft.level == null || minecraft.player == null) {
			return null;
		}
		if (pauseInMenus.enabled() && Platform.screen() != null) {
			return null;
		}
		return strength.get().effect;
	}
}

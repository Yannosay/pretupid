package net.pretupid.client.module.visual;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.module.Category;
import net.pretupid.client.module.Module;
import net.pretupid.client.setting.BooleanSetting;
import net.pretupid.client.setting.NumberSetting;
import net.pretupid.client.ui.Easing;

public final class ZoomModule extends Module {
	private static final float MIN_LEVEL = 1.5f;
	private static final float MAX_LEVEL = 100f;

	private final NumberSetting factor = add(new NumberSetting("factor", 4.0, 2.0, 50.0, 0.5, "x"));
	private final BooleanSetting smooth = add(new BooleanSetting("smooth", true));
	private final NumberSetting smoothness = add(new NumberSetting("smoothness", 1.0, 0.3, 3.0, 0.1, "x"));
	private final BooleanSetting scroll = add(new BooleanSetting("scroll", true));
	private final BooleanSetting cinematic = add(new BooleanSetting("cinematic", false));
	private final BooleanSetting reduceSensitivity = add(new BooleanSetting("reduce_sensitivity", true));
	private final BooleanSetting toggleMode = add(new BooleanSetting("toggle_mode", false));
	private final BooleanSetting hideHand = add(new BooleanSetting("hide_hand", true));
	private float level = 4f;
	private float current = 1f;
	private boolean toggled;
	private boolean wasActive;
	private long lastFrame;

	public ZoomModule() {
		super("zoom", Category.VISUAL, "spyglass", true);
	}

	private KeyMapping key() {
		return PretupidClient.get().keybinds().zoom();
	}

	@Override
	public void onTick(Minecraft minecraft) {
		if (toggleMode.enabled()) {
			while (key().consumeClick()) {
				toggled = !toggled;
			}
		} else {
			toggled = false;
			while (key().consumeClick()) {
				continue;
			}
		}
	}

	@Override
	protected void onDisable() {
		toggled = false;
		current = 1f;
	}

	public boolean isZooming() {
		if (!isEnabled()) {
			return false;
		}
		boolean active = toggleMode.enabled() ? toggled : key().isDown();
		if (active && !wasActive) {
			level = factor.floatValue();
		}
		wasActive = active;
		return active;
	}

	public float fovMultiplier() {
		long now = System.nanoTime();
		float delta = lastFrame == 0L ? 0f : Math.min(0.1f, (now - lastFrame) / 1_000_000_000f);
		lastFrame = now;
		float target = isZooming() ? level : 1f;
		if (smooth.enabled()) {
			current = Easing.approach(current, target, 14f / smoothness.floatValue(), delta);
		} else {
			current = target;
		}
		return 1f / Math.max(1f, current);
	}

	public boolean handleScroll(double amount) {
		if (!scroll.enabled() || !isZooming() || amount == 0) {
			return false;
		}
		level = Math.clamp(level * (amount > 0 ? 1.18f : 1f / 1.18f), MIN_LEVEL, MAX_LEVEL);
		return true;
	}

	public double sensitivityMultiplier() {
		if (!reduceSensitivity.enabled() || current <= 1.01f) {
			return 1.0;
		}
		return 1.0 / current;
	}

	public boolean forceCinematic() {
		return cinematic.enabled() && isZooming();
	}

	public boolean hideHand() {
		return hideHand.enabled() && current > 1.05f;
	}
}

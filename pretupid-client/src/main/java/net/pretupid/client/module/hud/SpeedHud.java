package net.pretupid.client.module.hud;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.pretupid.client.hud.HudPosition;
import net.pretupid.client.hud.TextHudModule;
import net.pretupid.client.setting.BooleanSetting;
import net.pretupid.client.setting.EnumSetting;

public final class SpeedHud extends TextHudModule {
	public enum SpeedUnit {
		BLOCKS_PER_SECOND,
		KILOMETERS_PER_HOUR
	}

	private static final int SAMPLES = 10;

	private final EnumSetting<SpeedUnit> unit = add(new EnumSetting<>("unit", SpeedUnit.BLOCKS_PER_SECOND));
	private final BooleanSetting includeVertical = add(new BooleanSetting("include_vertical", false));
	private final double[] samples = new double[SAMPLES];
	private int sampleIndex;
	private double lastX;
	private double lastY;
	private double lastZ;
	private boolean hasLast;

	public SpeedHud() {
		super("speed", "feather", false, new HudPosition(HudPosition.Anchor.START, 4, HudPosition.Anchor.START, 94));
	}

	@Override
	public void onTick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null) {
			hasLast = false;
			return;
		}
		double x = player.getX();
		double y = player.getY();
		double z = player.getZ();
		if (hasLast) {
			double dx = x - lastX;
			double dz = z - lastZ;
			double dy = includeVertical.enabled() ? y - lastY : 0.0;
			double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
			samples[sampleIndex] = distance > 20.0 ? 0.0 : distance * 20.0;
			sampleIndex = (sampleIndex + 1) % SAMPLES;
		}
		lastX = x;
		lastY = y;
		lastZ = z;
		hasLast = true;
	}

	@Override
	protected void onDisable() {
		hasLast = false;
	}

	@Override
	protected String value(Minecraft minecraft, boolean preview) {
		double sum = 0.0;
		for (double sample : samples) {
			sum += sample;
		}
		double speed = sum / SAMPLES;
		if (unit.get() == SpeedUnit.KILOMETERS_PER_HOUR) {
			return String.format(Locale.ROOT, "%.1f km/h", speed * 3.6);
		}
		return String.format(Locale.ROOT, "%.2f b/s", speed);
	}
}

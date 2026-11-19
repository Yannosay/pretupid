package net.pretupid.client.module.utility;

import net.pretupid.client.platform.Gfx;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.pretupid.client.hud.OverlayRenderer;
import net.pretupid.client.module.Category;
import net.pretupid.client.module.Module;
import net.pretupid.client.setting.BooleanSetting;
import net.pretupid.client.setting.ColorSetting;
import net.pretupid.client.setting.NumberSetting;
import net.pretupid.client.ui.Draw;
import net.pretupid.client.util.Colors;

public final class LowHealthWarningModule extends Module implements OverlayRenderer {
	private final NumberSetting threshold = add(new NumberSetting("threshold", 3, 0.5, 10, 0.5, " ❤"));
	private final NumberSetting intensity = add(new NumberSetting("intensity", 60, 10, 100, 5, "%"));
	private final BooleanSetting pulse = add(new BooleanSetting("pulse", true));
	private final BooleanSetting heartbeat = add(new BooleanSetting("heartbeat", false));
	private final ColorSetting color = add(new ColorSetting("color", 0xFFE11D48));
	private int heartbeatTicks;

	public LowHealthWarningModule() {
		super("low_health_warning", Category.UTILITY, "totem_of_undying", false);
	}

	private boolean critical(LocalPlayer player) {
		return player != null && !player.isCreative() && !player.isSpectator() && player.isAlive() && player.getHealth() <= threshold.floatValue() * 2f;
	}

	@Override
	public void onTick(Minecraft minecraft) {
		if (!heartbeat.enabled() || !critical(minecraft.player)) {
			heartbeatTicks = 0;
			return;
		}
		if (heartbeatTicks-- <= 0) {
			heartbeatTicks = 20;
			minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASEDRUM.value(), 0.6f, 0.5f));
		}
	}

	@Override
	public void renderOverlay(Gfx graphics, Minecraft minecraft, DeltaTracker deltaTracker) {
		LocalPlayer player = minecraft.player;
		if (!critical(player)) {
			return;
		}
		float severity = 1f - Math.clamp(player.getHealth() / (threshold.floatValue() * 2f), 0f, 1f);
		float strength = intensity.floatValue() / 100f * (0.55f + 0.45f * severity);
		if (pulse.enabled()) {
			strength *= 0.7f + 0.3f * (float) Math.sin(System.currentTimeMillis() / 180.0);
		}
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		int edge = Colors.withAlpha(color.resolve(), Math.round(170 * strength));
		int clear = Colors.withAlpha(color.resolve(), 0);
		int band = Math.max(24, height / 4);
		Draw.verticalGradient(graphics, 0, 0, width, band, edge, clear);
		Draw.verticalGradient(graphics, 0, height - band, width, band, clear, edge);
		int sideBand = Math.max(24, width / 6);
		int steps = 16;
		for (int i = 0; i < steps; i++) {
			float k = 1f - i / (float) steps;
			int stripColor = Colors.withAlpha(color.resolve(), Math.round(170 * strength * k * k / steps * 3f));
			int x = i * sideBand / steps;
			int stripWidth = Math.max(1, sideBand / steps);
			Draw.rect(graphics, x, 0, stripWidth, height, stripColor);
			Draw.rect(graphics, width - x - stripWidth, 0, stripWidth, height, stripColor);
		}
	}
}

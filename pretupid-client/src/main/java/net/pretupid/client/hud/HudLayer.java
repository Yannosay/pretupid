package net.pretupid.client.hud;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.module.Module;
import net.pretupid.client.module.performance.FpsBenchmark;
import net.pretupid.client.module.visual.CrosshairModule;
import net.pretupid.client.cinematic.CinematicOverlay;
import net.pretupid.client.module.visual.CinematicModule;
import net.pretupid.client.module.visual.FreecamModule;
import net.pretupid.client.ui.Draw;
import net.pretupid.client.ui.screen.HudEditorScreen;

public final class HudLayer implements HudElement {
	private static final Identifier ID = Identifier.fromNamespaceAndPath(PretupidClient.MOD_ID, "hud");
	private static final Identifier NOTIFICATIONS_ID = Identifier.fromNamespaceAndPath(PretupidClient.MOD_ID, "notifications");
	private static final Identifier CINEMATIC_ID = Identifier.fromNamespaceAndPath(PretupidClient.MOD_ID, "cinematic_path");

	private final PretupidClient client;

	public HudLayer(PretupidClient client) {
		this.client = client;
	}

	public void register() {
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, ID, this);
		HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, original -> (graphics, deltaTracker) -> {
			Minecraft minecraft = Minecraft.getInstance();
			if (client.modules().get(FreecamModule.class).active()) {
				return;
			}
			CrosshairModule crosshair = client.modules().get(CrosshairModule.class);
			if (crosshair.replacesVanilla(minecraft)) {
				crosshair.render(graphics, minecraft);
			} else {
				original.extractRenderState(graphics, deltaTracker);
			}
		});
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, CINEMATIC_ID, (graphics, deltaTracker) -> {
			CinematicModule cinematic = client.modules().get(CinematicModule.class);
			if (cinematic.isEnabled() && cinematic.showsPath() && client.modules().get(FreecamModule.class).active()) {
				Minecraft minecraft = Minecraft.getInstance();
				var director = client.cinematic();
				CinematicOverlay.drawPath(graphics, minecraft.font, director.current(), -1, director.playhead(), graphics.guiWidth(), graphics.guiHeight());
			}
		});
		HudElementRegistry.addLast(NOTIFICATIONS_ID, (graphics, deltaTracker) -> client.notifications().render(graphics, Minecraft.getInstance()));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.gui.hud.isHidden() || minecraft.gui.screen() instanceof HudEditorScreen) {
			return;
		}
		if (client.benchmark().running()) {
			drawBenchmark(graphics, minecraft);
		}
		for (Module module : client.modules().all()) {
			if (module.isEnabled() && module instanceof OverlayRenderer overlay) {
				overlay.renderOverlay(graphics, minecraft, deltaTracker);
			}
		}
		if (client.settings().hideHudInDebug.enabled() && minecraft.getDebugOverlay().showDebugScreen()) {
			return;
		}
		Font font = minecraft.font;
		int screenWidth = graphics.guiWidth();
		int screenHeight = graphics.guiHeight();
		for (Module module : client.modules().all()) {
			if (module.isEnabled() && module instanceof HudModule hud && hud.visible(minecraft)) {
				draw(graphics, font, minecraft, hud, screenWidth, screenHeight, false);
			}
		}
	}

	public static Bounds bounds(HudModule hud, Font font, Minecraft minecraft, int screenWidth, int screenHeight, boolean preview) {
		float scale = hud.scale();
		int width = Math.max(1, Math.round(hud.width(font, minecraft, preview) * scale));
		int height = Math.max(1, Math.round(hud.height(font, minecraft, preview) * scale));
		int x = hud.position().resolveX(screenWidth, width);
		int y = hud.position().resolveY(screenHeight, height);
		return new Bounds(x, y, width, height);
	}

	public static Bounds draw(GuiGraphicsExtractor graphics, Font font, Minecraft minecraft, HudModule hud, int screenWidth, int screenHeight, boolean preview) {
		Bounds bounds = bounds(hud, font, minecraft, screenWidth, screenHeight, preview);
		float scale = hud.scale();
		graphics.pose().pushMatrix();
		graphics.pose().translate(bounds.x(), bounds.y());
		graphics.pose().scale(scale, scale);
		try {
			hud.render(graphics, font, minecraft, preview);
		} catch (RuntimeException exception) {
			PretupidClient.LOGGER.error("HUD module {} failed to render and was disabled", hud.id(), exception);
			hud.setEnabled(false);
		} finally {
			graphics.pose().popMatrix();
		}
		return bounds;
	}

	public record Bounds(int x, int y, int width, int height) {
		public boolean contains(double px, double py) {
			return px >= x && py >= y && px < x + width && py < y + height;
		}

		public int right() {
			return x + width;
		}

		public int bottom() {
			return y + height;
		}
	}

	private void drawBenchmark(GuiGraphicsExtractor graphics, Minecraft minecraft) {
		FpsBenchmark benchmark = client.benchmark();
		Font font = minecraft.font;
		Component label = Component.translatable(benchmark.measuringOptimized() ? "pretupid.benchmark.phase.optimized" : "pretupid.benchmark.phase.vanilla");
		int width = Math.max(140, font.width(label) + 16);
		int x = (graphics.guiWidth() - width) / 2;
		int y = 48;
		Draw.glass(graphics, x, y, width, 24, 0xCC0E1020, 0x665D5BF5);
		Draw.text(graphics, font, label, x + (width - font.width(label)) / 2, y + 4, 0xFFFFFFFF);
		int barWidth = width - 16;
		Draw.rect(graphics, x + 8, y + 16, barWidth, 3, 0x33FFFFFF);
		Draw.rect(graphics, x + 8, y + 16, Math.round(barWidth * benchmark.progress()), 3, 0xFF8F94F5);
	}
}

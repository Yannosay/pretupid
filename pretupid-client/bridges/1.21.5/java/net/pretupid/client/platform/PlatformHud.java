package net.pretupid.client.platform;

import net.fabricmc.fabric.api.client.rendering.v1.HudLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.IdentifiedLayer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;

public final class PlatformHud {
	public interface Layer {
		void draw(Gfx graphics, DeltaTracker deltaTracker);
	}

	public interface Replacement {
		void draw(Gfx graphics, DeltaTracker deltaTracker, Runnable original);
	}

	public interface ScreenLayer {
		void draw(Gfx graphics, Screen screen);
	}

	private static final Map<Screen, List<ScreenLayer>> BACKGROUND_LAYERS = new WeakHashMap<>();

	static {
		ScreenEvents.BEFORE_INIT.register((client, screen, width, height) -> BACKGROUND_LAYERS.remove(screen));
	}

	private PlatformHud() {
	}

	public static void drawAfterBackground(Screen screen, GuiGraphics graphics) {
		List<ScreenLayer> layers = BACKGROUND_LAYERS.get(screen);
		if (layers == null) {
			return;
		}
		Gfx gfx = Gfx.of(graphics);
		for (ScreenLayer layer : List.copyOf(layers)) {
			layer.draw(gfx, screen);
		}
	}

	public static void beforeChat(Identifier id, Layer layer) {
		HudLayerRegistrationCallback.EVENT.register(drawer -> drawer.attachLayerBefore(IdentifiedLayer.CHAT, id, (graphics, deltaTracker) -> layer.draw(Gfx.of(graphics), deltaTracker)));
	}

	public static void last(Identifier id, Layer layer) {
		HudLayerRegistrationCallback.EVENT.register(drawer -> drawer.addLayer(IdentifiedLayer.of(id, (graphics, deltaTracker) -> layer.draw(Gfx.of(graphics), deltaTracker))));
	}

	public static void replaceCrosshair(Replacement replacement) {
		HudLayerRegistrationCallback.EVENT.register(drawer -> drawer.replaceLayer(IdentifiedLayer.CROSSHAIR, original -> IdentifiedLayer.of(IdentifiedLayer.CROSSHAIR, (graphics, deltaTracker) ->
			replacement.draw(Gfx.of(graphics), deltaTracker, () -> original.render(graphics, deltaTracker)))));
	}

	public static void beforeScreen(Screen screen, Runnable action) {
		ScreenEvents.beforeRender(screen).register((current, graphics, mouseX, mouseY, partialTick) -> action.run());
	}

	public static void afterScreenBackground(Screen screen, ScreenLayer layer) {
		BACKGROUND_LAYERS.computeIfAbsent(screen, key -> new ArrayList<>()).add(layer);
	}

	public static void afterScreen(Screen screen, ScreenLayer layer) {
		ScreenEvents.afterRender(screen).register((current, graphics, mouseX, mouseY, partialTick) -> layer.draw(Gfx.of(graphics), current));
	}
}

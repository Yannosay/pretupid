package net.pretupid.client.platform;

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
	private static final List<Layer> BEFORE_CHAT = new ArrayList<>();
	private static final List<Layer> LAST = new ArrayList<>();
	private static Replacement crosshair;

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
		BEFORE_CHAT.add(layer);
	}

	public static void last(Identifier id, Layer layer) {
		LAST.add(layer);
	}

	public static void replaceCrosshair(Replacement replacement) {
		crosshair = replacement;
	}

	public static void drawBeforeChat(GuiGraphics graphics, DeltaTracker deltaTracker) {
		draw(BEFORE_CHAT, graphics, deltaTracker);
	}

	public static void drawLast(GuiGraphics graphics, DeltaTracker deltaTracker) {
		draw(LAST, graphics, deltaTracker);
	}

	private static void draw(List<Layer> layers, GuiGraphics graphics, DeltaTracker deltaTracker) {
		if (layers.isEmpty()) {
			return;
		}
		Gfx gfx = Gfx.of(graphics);
		for (Layer layer : layers) {
			layer.draw(gfx, deltaTracker);
		}
	}

	public static Replacement crosshair() {
		return crosshair;
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

package net.pretupid.client.platform;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.DeltaTracker;
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

	private PlatformHud() {
	}

	public static void beforeChat(Identifier id, Layer layer) {
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, id, (graphics, deltaTracker) -> layer.draw(Gfx.of(graphics), deltaTracker));
	}

	public static void last(Identifier id, Layer layer) {
		HudElementRegistry.addLast(id, (graphics, deltaTracker) -> layer.draw(Gfx.of(graphics), deltaTracker));
	}

	public static void replaceCrosshair(Replacement replacement) {
		HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, original -> (graphics, deltaTracker) ->
			replacement.draw(Gfx.of(graphics), deltaTracker, () -> original.extractRenderState(graphics, deltaTracker)));
	}

	public static void beforeScreen(Screen screen, Runnable action) {
		ScreenEvents.beforeExtract(screen).register((current, graphics, mouseX, mouseY, partialTick) -> action.run());
	}

	public static void afterScreenBackground(Screen screen, ScreenLayer layer) {
		ScreenEvents.afterBackground(screen).register((current, graphics, mouseX, mouseY, partialTick) -> layer.draw(Gfx.of(graphics), current));
	}

	public static void afterScreen(Screen screen, ScreenLayer layer) {
		ScreenEvents.afterExtract(screen).register((current, graphics, mouseX, mouseY, partialTick) -> layer.draw(Gfx.of(graphics), current));
	}
}

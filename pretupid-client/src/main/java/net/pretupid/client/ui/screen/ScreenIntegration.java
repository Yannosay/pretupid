package net.pretupid.client.ui.screen;

import net.pretupid.client.platform.Platform;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.pretupid.client.PretupidClient;

public final class ScreenIntegration {
	private static final int ICON_SIZE = 20;
	private static final int DEFAULT_GAP = 4;
	private static final int EDGE_MARGIN = 6;

	private ScreenIntegration() {
	}

	public static void register(PretupidClient client) {
		ScreenEvents.AFTER_INIT.register((minecraft, screen, width, height) -> {
			boolean title = screen instanceof TitleScreen && client.settings().titleScreenButton.enabled();
			boolean pause = screen instanceof PauseScreen pauseScreen && pauseScreen.showsPauseMenu() && client.settings().pauseScreenButton.enabled();
			if (title || pause) {
				attach(screen);
			}
		});
		new TitleScreenEnhancer().register();
	}

	private static void attach(Screen screen) {
		List<AbstractWidget> widgets = Screens.getWidgets(screen);
		LogoButton button = new LogoButton(ICON_SIZE, Component.translatable("pretupid.ui.open_menu"),
			pressed -> Platform.setScreen(new ClientMenuScreen(screen)));
		List<AbstractWidget> row = iconRow(widgets);
		if (row.isEmpty()) {
			button.setPosition(EDGE_MARGIN, EDGE_MARGIN);
			widgets.add(button);
			return;
		}
		AbstractWidget last = row.getLast();
		int gap = row.size() > 1 ? Math.max(2, row.get(1).getX() - row.getFirst().getX() - ICON_SIZE) : DEFAULT_GAP;
		int shift = (ICON_SIZE + gap) / 2;
		for (AbstractWidget icon : row) {
			icon.setX(icon.getX() - shift);
		}
		button.setPosition(last.getX() + ICON_SIZE + gap, last.getY());
		widgets.add(button);
	}

	private static List<AbstractWidget> iconRow(List<AbstractWidget> widgets) {
		Map<Integer, List<AbstractWidget>> rows = new TreeMap<>();
		for (AbstractWidget widget : widgets) {
			if (widget.visible && widget.getWidth() == ICON_SIZE && widget.getHeight() == ICON_SIZE) {
				rows.computeIfAbsent(widget.getY(), key -> new ArrayList<>()).add(widget);
			}
		}
		List<AbstractWidget> best = List.of();
		for (List<AbstractWidget> row : rows.values()) {
			if (row.size() > best.size()) {
				best = row;
			}
		}
		List<AbstractWidget> sorted = new ArrayList<>(best);
		sorted.sort(Comparator.comparingInt(AbstractWidget::getX));
		return sorted;
	}
}

package net.pretupid.client.ui.screen;

import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.module.utility.WaypointsModule;
import net.pretupid.client.ui.Draw;
import net.pretupid.client.ui.PretupidScreen;
import net.pretupid.client.ui.ScrollState;
import net.pretupid.client.ui.TextInput;
import net.pretupid.client.ui.Theme;
import net.pretupid.client.ui.UiContext;
import net.pretupid.client.ui.Widgets;
import net.pretupid.client.util.Colors;

public final class WaypointsScreen extends PretupidScreen {
	private static final int ROW = 26;
	private static final long CONFIRM_MS = 3000L;

	private final WaypointsModule module = PretupidClient.get().modules().get(WaypointsModule.class);
	private final ScrollState scroll = new ScrollState();
	private final TextInput nameInput = new TextInput(WaypointsModule.MAX_NAME_LENGTH);
	private WaypointsModule.Waypoint editing;
	private WaypointsModule.Waypoint pendingDelete;
	private long pendingDeleteAt;
	private boolean allDimensions;
	private Component status;
	private boolean statusError;
	private long statusAt;

	public WaypointsScreen(Screen parent) {
		super(Component.translatable("pretupid.waypoints.title"), parent);
		nameInput.onSubmit(this::submit);
	}

	private void show(Component text, boolean error) {
		status = text;
		statusError = error;
		statusAt = System.currentTimeMillis();
	}

	private void submit() {
		String name = nameInput.value().isBlank() ? module.nextName(minecraft) : nameInput.value();
		if (editing != null) {
			if (module.replace(minecraft, editing, editing.withName(name))) {
				show(Component.translatable("pretupid.waypoints.renamed", WaypointsModule.sanitize(name)), false);
				editing = null;
				nameInput.setValue("");
			} else {
				show(Component.translatable("pretupid.waypoints.name_taken"), true);
			}
			return;
		}
		Component result = module.add(minecraft, name);
		show(result, false);
		nameInput.setValue("");
		if (!module.isEnabled()) {
			module.setEnabled(true);
		}
	}

	@Override
	protected void drawContent(UiContext ui) {
		var g = ui.g();
		int w = Math.min(470, width - 12);
		int h = Math.min(300, height - 12);
		int x = (width - w) / 2;
		int y = (height - h) / 2;
		Draw.shadow(g, x, y, w, h, 6, 1f);
		Draw.glass(g, x, y, w, h, Theme.WINDOW, Theme.WINDOW_BORDER);
		Draw.textShadow(g, font, title, x + 10, y + 8, Theme.TEXT);
		Component where = Component.translatable("pretupid.waypoints.here", WaypointsModule.shortDimension(WaypointsModule.dimension(minecraft)));
		Draw.text(g, font, Draw.ellipsize(font, where, w - 120), x + 10, y + 19, Theme.TEXT_DIM);
		Widgets.button(ui, "waypoints.done", x + w - 60, y + 6, 52, 16, Component.translatable("pretupid.ui.done"), Widgets.ButtonStyle.PRIMARY, this::onClose);

		int rowY = y + 34;
		int buttonW = 92;
		nameInput.draw(ui, x + 10, rowY, w - 20 - buttonW - 4 - (editing != null ? 56 : 0), 18,
			Component.translatable(editing != null ? "pretupid.waypoints.rename_placeholder" : "pretupid.waypoints.name_placeholder"));
		Widgets.button(ui, "waypoints.submit", x + w - 10 - buttonW, rowY + 1, buttonW, 16,
			Component.translatable(editing != null ? "pretupid.waypoints.save_name" : "pretupid.waypoints.add_here"), Widgets.ButtonStyle.PRIMARY, this::submit);
		if (editing != null) {
			Widgets.button(ui, "waypoints.cancel", x + w - 10 - buttonW - 56, rowY + 1, 52, 16, Component.translatable("gui.cancel"), Widgets.ButtonStyle.GHOST, () -> {
				editing = null;
				nameInput.setValue("");
			});
		}

		int tabsY = rowY + 24;
		int tabW = 110;
		Widgets.button(ui, "waypoints.tab.here", x + 10, tabsY, tabW, 16, Component.translatable("pretupid.waypoints.this_dimension"),
			allDimensions ? Widgets.ButtonStyle.GHOST : Widgets.ButtonStyle.SECONDARY, () -> {
				allDimensions = false;
				scroll.reset();
			});
		Widgets.button(ui, "waypoints.tab.all", x + 14 + tabW, tabsY, tabW, 16, Component.translatable("pretupid.waypoints.all_dimensions"),
			allDimensions ? Widgets.ButtonStyle.SECONDARY : Widgets.ButtonStyle.GHOST, () -> {
				allDimensions = true;
				scroll.reset();
			});

		String here = WaypointsModule.dimension(minecraft);
		List<WaypointsModule.Waypoint> list = module.waypoints(minecraft).stream()
			.filter(waypoint -> allDimensions || waypoint.dimension().equals(here) || module.locate(minecraft, waypoint).isPresent())
			.toList();
		int listY = tabsY + 22;
		int listH = y + h - 18 - listY;
		scroll.begin(ui, "waypoints.list", x + 10, listY, w - 20, listH, Math.max(1, list.size()) * ROW);
		int top = listY - scroll.offset();
		if (list.isEmpty()) {
			Draw.centered(g, font, Component.translatable("pretupid.waypoints.empty_screen"), x + w / 2, listY + 20, Theme.TEXT_DIM);
		}
		for (WaypointsModule.Waypoint waypoint : list) {
			drawRow(ui, waypoint, x + 10, top, w - 26);
			top += ROW;
		}
		scroll.end(ui, x + 10, listY, w - 20, listH);

		if (status != null && System.currentTimeMillis() - statusAt < 5000L) {
			Draw.text(g, font, Draw.ellipsize(font, status, w - 20), x + 10, y + h - 13, statusError ? Theme.DANGER : Theme.TEXT_MUTED);
		}
	}

	private void drawRow(UiContext ui, WaypointsModule.Waypoint waypoint, int x, int y, int w) {
		var g = ui.g();
		boolean selected = waypoint.equals(editing);
		Draw.chamfer(g, x, y, w, ROW - 2, selected ? Colors.lerp(Theme.SURFACE, Theme.accent(), 0.25f) : Theme.SURFACE);
		boolean swatchHover = ui.interactive(x + 4, y + 5, 14, 14, UiContext.Handler.onClick(() -> cycleColor(waypoint)));
		Draw.rect(g, x + 4, y + 5, 14, 14, swatchHover ? Colors.brighten(waypoint.color(), 0.2f) : waypoint.color());
		Draw.border(g, x + 4, y + 5, 14, 14, 0x60000000);
		if (swatchHover) {
			ui.tooltip(Component.translatable("pretupid.waypoints.color_tooltip"));
		}
		int textColor = waypoint.visible() ? Theme.TEXT : Theme.TEXT_DIM;
		Draw.text(g, font, Draw.ellipsize(font, Component.literal(waypoint.name()), 120), x + 24, y + 3, textColor);
		Optional<WaypointsModule.Located> located = module.locate(minecraft, waypoint);
		String detail = waypoint.coordinates() + "  " + WaypointsModule.shortDimension(waypoint.dimension())
			+ located.map(entry -> "  " + WaypointsModule.distanceText(entry.distance()) + (entry.converted() ? " ⇄" : "")).orElse("");
		Draw.text(g, font, Draw.ellipsize(font, Component.literal(detail), w - 210), x + 24, y + 13, Theme.TEXT_DIM);
		int bx = x + w - 4;
		bx -= 48;
		boolean confirming = waypoint.equals(pendingDelete) && System.currentTimeMillis() - pendingDeleteAt < CONFIRM_MS;
		Widgets.button(ui, "waypoints.delete." + waypoint.name(), bx, y + 4, 48, 16, Component.translatable(confirming ? "pretupid.waypoints.confirm_delete" : "pretupid.waypoints.delete"), Widgets.ButtonStyle.DANGER, () -> {
			if (!confirming) {
				pendingDelete = waypoint;
				pendingDeleteAt = System.currentTimeMillis();
				return;
			}
			pendingDelete = null;
			module.delete(minecraft, waypoint);
			if (waypoint.equals(editing)) {
				editing = null;
				nameInput.setValue("");
			}
			show(Component.translatable("pretupid.waypoints.removed", waypoint.name()), false);
		});
		bx -= 44;
		Widgets.button(ui, "waypoints.copy." + waypoint.name(), bx, y + 4, 40, 16, Component.translatable("pretupid.waypoints.copy"), Widgets.ButtonStyle.GHOST, () -> {
			minecraft.keyboardHandler.setClipboard(waypoint.coordinates());
			show(Component.translatable("pretupid.waypoints.copied", waypoint.coordinates()), false);
		});
		bx -= 48;
		Widgets.button(ui, "waypoints.rename." + waypoint.name(), bx, y + 4, 44, 16, Component.translatable("pretupid.waypoints.rename"), Widgets.ButtonStyle.GHOST, () -> {
			editing = waypoint;
			nameInput.setValue(waypoint.name());
			nameInput.setFocused(true);
		});
		bx -= 26;
		Widgets.toggle(ui, "waypoints.visible." + waypoint.name(), bx, y + 7, waypoint.visible(),
			() -> module.replace(minecraft, waypoint, waypoint.withVisible(!waypoint.visible())));
	}

	private void cycleColor(WaypointsModule.Waypoint waypoint) {
		int[] palette = WaypointsModule.PALETTE;
		int next = palette[0];
		for (int i = 0; i < palette.length; i++) {
			if (palette[i] == waypoint.color()) {
				next = palette[(i + 1) % palette.length];
				break;
			}
		}
		WaypointsModule.Waypoint updated = waypoint.withColor(next);
		if (module.replace(minecraft, waypoint, updated) && waypoint.equals(editing)) {
			editing = updated;
		}
	}
}

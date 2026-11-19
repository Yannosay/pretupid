package net.pretupid.client.ui.screen.page;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.mods.ModCatalog;
import net.pretupid.client.mods.ModEntry;
import net.pretupid.client.mods.ModIcons;
import net.pretupid.client.ui.Draw;
import net.pretupid.client.ui.ScrollState;
import net.pretupid.client.ui.TextInput;
import net.pretupid.client.ui.Theme;
import net.pretupid.client.ui.UiContext;
import net.pretupid.client.ui.Widgets;
import net.pretupid.client.ui.screen.MenuPage;
import net.pretupid.client.util.Colors;
import net.pretupid.client.util.PlatformOpener;

public final class ModsPage implements MenuPage {
	private static final int ROW_HEIGHT = 32;

	private final TextInput search = new TextInput(32);
	private final ScrollState scroll = new ScrollState();

	public ModsPage() {
		search.onChange(text -> scroll.reset());
	}

	@Override
	public void onOpen() {
		PretupidClient.get().modCatalog().refresh();
	}

	@Override
	public void draw(UiContext ui, int x, int y, int w, int h) {
		var g = ui.g();
		var font = ui.font();
		ModCatalog catalog = PretupidClient.get().modCatalog();
		List<ModEntry> entries = new ArrayList<>();
		for (ModEntry entry : catalog.entries()) {
			if (entry.matches(search.value().trim())) {
				entries.add(entry);
			}
		}
		Draw.textShadow(g, font, Component.translatable("pretupid.mods.title", catalog.entries().size()), x, y + 5, Theme.TEXT);
		int buttonWidth = 86;
		Widgets.button(ui, "mods.folder", x + w - buttonWidth, y, buttonWidth, 18, Component.translatable("pretupid.mods.open_folder"), Widgets.ButtonStyle.SECONDARY,
			() -> PlatformOpener.openFolder(catalog.modsDirectory()));
		int searchWidth = Math.min(140, w - buttonWidth - 110);
		search.draw(ui, x + w - buttonWidth - searchWidth - 6, y + 1, searchWidth, 16, Component.translatable("pretupid.ui.search"));
		int listY = y + 26;
		if (catalog.restartRequired()) {
			float pulse = 0.75f + 0.25f * (float) Math.sin(System.currentTimeMillis() / 300.0);
			Draw.chamfer(g, x, listY, w, 16, Colors.withAlpha(Theme.WARNING, Math.round(40 * pulse)));
			Draw.border(g, x, listY, w, 16, Colors.withAlpha(Theme.WARNING, 120));
			Draw.text(g, font, Component.translatable("pretupid.mods.restart_required"), x + 6, listY + 4, Theme.WARNING);
			listY += 22;
		}
		int listH = y + h - listY;
		scroll.begin(ui, "mods", x, listY, w, listH, entries.size() * (ROW_HEIGHT + 4));
		int rowY = listY - scroll.offset();
		for (ModEntry entry : entries) {
			if (rowY + ROW_HEIGHT >= listY && rowY <= listY + listH) {
				drawRow(ui, catalog, entry, x, rowY, w - 6);
			}
			rowY += ROW_HEIGHT + 4;
		}
		if (entries.isEmpty()) {
			Draw.centered(g, font, Component.translatable("pretupid.ui.no_results"), x + w / 2, listY + 24, Theme.TEXT_DIM);
		}
		scroll.end(ui, x, listY, w, listH);
	}

	private void drawRow(UiContext ui, ModCatalog catalog, ModEntry entry, int x, int y, int w) {
		var g = ui.g();
		var font = ui.font();
		String id = "mod." + entry.id();
		boolean hover = ui.hovered(x, y, w, ROW_HEIGHT);
		float t = ui.animate(id + ".hover", hover ? 1f : 0f, 16f);
		Draw.chamfer(g, x, y, w, ROW_HEIGHT, Colors.lerp(Theme.SURFACE, Theme.SURFACE_HOVER, t));
		drawIcon(ui, entry, x + 7, y + 7);
		int textX = x + 32;
		int controlWidth = 80;
		int textWidth = w - 32 - controlWidth;
		int nameColor = entry.enabledOnDisk() ? Theme.TEXT : Theme.TEXT_DIM;
		Component name = Draw.ellipsize(font, Component.literal(entry.name()), textWidth - font.width(entry.version()) - 6);
		Draw.text(g, font, name, textX, y + 6, nameColor);
		Draw.text(g, font, entry.version(), textX + font.width(name) + 5, y + 6, Theme.TEXT_DIM);
		String description = entry.description().isBlank() ? entry.id() : entry.description().replace('\n', ' ');
		Draw.text(g, font, Draw.ellipsize(font, description, textWidth), textX, y + 18, Theme.TEXT_MUTED);
		if (hover && ui.mouseX() > textX && ui.mouseX() < x + w - controlWidth && !entry.authors().isBlank()) {
			ui.tooltip(Component.translatable("pretupid.mods.by", entry.authors()));
		}
		int controlX = x + w - controlWidth;
		if (entry.toggleable()) {
			Widgets.toggle(ui, id, x + w - 28, y + 11, entry.enabledOnDisk(), () -> catalog.setEnabled(entry, !entry.enabledOnDisk()));
			if (entry.restartRequired()) {
				Draw.text(g, font, Component.translatable("pretupid.mods.pending"), controlX, y + 12, Theme.WARNING);
			}
		} else {
			Component chip = switch (entry.lock()) {
				case CORE -> Component.translatable("pretupid.mods.core");
				case REQUIRED -> Component.translatable("pretupid.mods.required", entry.dependents());
				default -> Component.translatable("pretupid.mods.builtin");
			};
			int chipWidth = font.width(chip) + 8;
			Draw.chamfer(g, x + w - chipWidth - 4, y + 10, chipWidth, 12, Theme.SURFACE_ACTIVE);
			Draw.text(g, font, chip, x + w - chipWidth, y + 12, Theme.TEXT_MUTED);
		}
		entry.homepage().ifPresent(uri -> {
			if (hover) {
				boolean linkHover = ui.interactive(textX - 26, y + 6, 20, 20, UiContext.Handler.onClick(() -> PlatformOpener.openUri(uri)));
				if (linkHover) {
					ui.tooltip(Component.translatable("pretupid.mods.homepage"));
				}
			}
		});
	}

	private void drawIcon(UiContext ui, ModEntry entry, int x, int y) {
		var g = ui.g();
		Optional<ModIcons.Icon> icon = PretupidClient.get().modIcons().icon(entry);
		if (icon.isPresent()) {
			ModIcons.Icon value = icon.get();
			int tint = entry.enabledOnDisk() ? 0xFFFFFFFF : 0x80FFFFFF;
			g.blit(value.location(), x, y, 0f, 0f, 18, 18, value.width(), value.height(), value.width(), value.height(), tint);
			return;
		}
		int hue = Math.floorMod(entry.id().hashCode(), 360);
		int color = Colors.hsv(hue / 360f, 0.45f, 0.75f);
		Draw.chamfer(g, x, y, 18, 18, entry.enabledOnDisk() ? color : Colors.multiplyAlpha(color, 0.5f));
		String letter = entry.name().isEmpty() ? "?" : entry.name().substring(0, 1).toUpperCase(java.util.Locale.ROOT);
		Draw.text(g, ui.font(), letter, x + 9 - ui.font().width(letter) / 2, y + 5, 0xFFFFFFFF);
	}
}

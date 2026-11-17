package net.pretupid.client.ui.screen.page;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.config.ConfigStore;
import net.pretupid.client.hud.HudModule;
import net.pretupid.client.module.Category;
import net.pretupid.client.module.Module;
import net.pretupid.client.setting.Setting;
import net.pretupid.client.ui.Draw;
import net.pretupid.client.ui.Icon;
import net.pretupid.client.ui.ScrollState;
import net.pretupid.client.ui.TextInput;
import net.pretupid.client.ui.Theme;
import net.pretupid.client.ui.UiContext;
import net.pretupid.client.ui.UiIcon;
import net.pretupid.client.ui.Widgets;
import net.pretupid.client.ui.screen.ClientMenuScreen;
import net.pretupid.client.ui.screen.MenuPage;
import net.pretupid.client.util.Colors;

public final class ModulesPage implements MenuPage {
	private static final int CARD_HEIGHT = 40;
	private static final int GAP = 6;
	private static final Icon ALL_ICON = Icon.item("book");
	private static final Icon FAVORITE_ICON = Icon.item("nether_star");

	private enum Filter {
		ALL,
		FAVORITES,
		CATEGORY
	}

	private final ClientMenuScreen screen;
	private final TextInput search = new TextInput(32);
	private final ScrollState gridScroll = new ScrollState();
	private final ScrollState settingsScroll = new ScrollState();
	private Filter filter = Filter.ALL;
	private Category category = Category.HUD;
	private Module selected;
	private int settingsOpenedAt;

	public ModulesPage(ClientMenuScreen screen) {
		this.screen = screen;
		search.onChange(text -> gridScroll.reset());
	}

	public void openSettings(Module module) {
		selected = module;
		settingsScroll.reset();
		settingsOpenedAt++;
	}

	@Override
	public void draw(UiContext ui, int x, int y, int w, int h) {
		if (selected != null) {
			drawSettings(ui, x, y, w, h);
		} else {
			drawGrid(ui, x, y, w, h);
		}
	}

	private List<Module> visibleModules() {
		PretupidClient client = PretupidClient.get();
		ConfigStore config = client.config();
		String query = search.value().trim();
		List<Module> result = new ArrayList<>();
		for (Module module : client.modules().all()) {
			if (!module.matches(query)) {
				continue;
			}
			boolean include = switch (filter) {
				case ALL -> true;
				case FAVORITES -> config.isFavorite(module);
				case CATEGORY -> module.category() == category;
			};
			if (include) {
				result.add(module);
			}
		}
		if (filter != Filter.FAVORITES) {
			result.sort((a, b) -> Boolean.compare(config.isFavorite(b), config.isFavorite(a)));
		}
		return result;
	}

	private void drawGrid(UiContext ui, int x, int y, int w, int h) {
		var g = ui.g();
		var font = ui.font();
		int chipX = x;
		chipX += drawChip(ui, "chip.all", chipX, y, ALL_ICON, Component.translatable("pretupid.filter.all"), filter == Filter.ALL, () -> setFilter(Filter.ALL, null)) + 3;
		chipX += drawChip(ui, "chip.fav", chipX, y, FAVORITE_ICON, Component.translatable("pretupid.filter.favorites"), filter == Filter.FAVORITES, () -> setFilter(Filter.FAVORITES, null)) + 3;
		for (Category entry : Category.values()) {
			chipX += drawChip(ui, "chip." + entry.key(), chipX, y, entry.icon(), entry.label(), filter == Filter.CATEGORY && category == entry, () -> setFilter(Filter.CATEGORY, entry)) + 3;
		}
		int searchWidth = Math.max(70, Math.min(140, x + w - chipX - 4));
		search.draw(ui, x + w - searchWidth, y + 1, searchWidth, 16, Component.translatable("pretupid.ui.search"));
		int listY = y + 26;
		int listH = h - 26;
		List<Module> modules = visibleModules();
		int columns = w >= 300 ? 2 : 1;
		int cardWidth = (w - GAP * (columns - 1) - 4) / columns;
		int rows = (modules.size() + columns - 1) / columns;
		int contentHeight = Math.max(0, rows * (CARD_HEIGHT + GAP) - GAP);
		gridScroll.begin(ui, "modules", x, listY, w, listH, contentHeight);
		int offset = gridScroll.offset();
		for (int i = 0; i < modules.size(); i++) {
			int column = i % columns;
			int row = i / columns;
			int cardX = x + column * (cardWidth + GAP);
			int cardY = listY + row * (CARD_HEIGHT + GAP) - offset;
			if (cardY + CARD_HEIGHT < listY || cardY > listY + listH) {
				continue;
			}
			drawCard(ui, modules.get(i), cardX, cardY, cardWidth, i);
		}
		if (modules.isEmpty()) {
			Draw.centered(g, font, Component.translatable(filter == Filter.FAVORITES && search.value().isEmpty() ? "pretupid.ui.no_favorites" : "pretupid.ui.no_results"), x + w / 2, listY + 30, Theme.TEXT_DIM);
		}
		gridScroll.end(ui, x, listY, w, listH);
	}

	private void setFilter(Filter next, Category nextCategory) {
		filter = next;
		if (nextCategory != null) {
			category = nextCategory;
		}
		gridScroll.reset();
	}

	private int drawChip(UiContext ui, String id, int x, int y, Icon icon, Component tooltip, boolean active, Runnable action) {
		int w = 20;
		int h = 18;
		boolean hover = ui.interactive(x, y, w, h, UiContext.Handler.onClick(action));
		float t = ui.animate(id + ".hover", hover ? 1f : 0f, 16f);
		float a = ui.animate(id + ".active", active ? 1f : 0f, 14f);
		int fill = Colors.lerp(Colors.lerp(Theme.SURFACE, Theme.SURFACE_HOVER, t), Theme.accent(90), a);
		Draw.chamfer(ui.g(), x, y, w, h, fill);
		if (a > 0.01f) {
			Draw.border(ui.g(), x, y, w, h, Theme.accent(Math.round(200 * a)));
		}
		ui.g().pose().pushMatrix();
		ui.g().pose().translate(x + 2, y + 1 - t);
		icon.draw(ui.g(), 0, 0);
		ui.g().pose().popMatrix();
		if (hover) {
			ui.tooltip(tooltip);
		}
		return w;
	}

	private void drawCard(UiContext ui, Module module, int x, int y, int w, int index) {
		var g = ui.g();
		var font = ui.font();
		ConfigStore config = PretupidClient.get().config();
		String id = "card." + module.id();
		float appear = ui.animateFrom(id + ".appear." + filter + category + search.value().length(), 0f, 1f, 10f + Math.max(0, 8 - index));
		int dy = Math.round((1f - appear) * 6f);
		y += dy;
		boolean hover = ui.interactive(x, y, w, CARD_HEIGHT, new UiContext.Handler() {
			@Override
			public boolean click(double mouseX, double mouseY, int button) {
				if (button == 1) {
					openSettings(module);
				} else if (button == 0) {
					module.toggle();
				} else {
					return false;
				}
				return true;
			}
		});
		float t = ui.animate(id + ".hover", hover ? 1f : 0f, 16f);
		float on = ui.animate(id + ".on", module.isEnabled() ? 1f : 0f, 14f);
		int fill = Colors.lerp(Theme.SURFACE, Theme.SURFACE_HOVER, t);
		fill = Colors.lerp(fill, Colors.lerp(fill, Theme.accent(70), 0.6f), on);
		Draw.chamfer(g, x, y, w, CARD_HEIGHT, fill);
		Draw.border(g, x, y, w, CARD_HEIGHT, Colors.lerp(Theme.SURFACE_BORDER, Theme.accent(150), on * 0.8f));
		if (on > 0.01f) {
			int bar = Math.round((CARD_HEIGHT - 10) * on);
			Draw.rect(g, x + 1, y + CARD_HEIGHT / 2 - bar / 2, 2, bar, Theme.accent());
		}
		g.pose().pushMatrix();
		g.pose().translate(x + 7, y + 12 - t);
		module.icon().draw(g, 0, 0);
		g.pose().popMatrix();
		int textX = x + 30;
		int textWidth = w - 30 - 50;
		Draw.text(g, font, Draw.ellipsize(font, module.name(), textWidth), textX, y + 9, Theme.TEXT);
		Component description = module.isPaused() ? Component.translatable("pretupid.compat.paused_badge") : module.description();
		Draw.text(g, font, Draw.ellipsize(font, description, textWidth + 18), textX, y + 22, module.isPaused() ? Theme.WARNING : Theme.TEXT_MUTED);
		if (hover && module.isPaused() && ui.mouseX() < x + w - 50) {
			ui.tooltip(module.pauseReason());
		} else if (hover && font.width(description) > textWidth + 18 && ui.mouseX() < x + w - 50) {
			ui.tooltip(description);
		}
		Widgets.toggle(ui, id, x + w - 28, y + 7, module.enabledSetting().get(), module::toggle);
		boolean favorite = config.isFavorite(module);
		float starAlpha = favorite ? 1f : t;
		if (starAlpha > 0.02f) {
			boolean starHover = ui.interactive(x + w - 44, y + 6, 12, 12, UiContext.Handler.onClick(() -> config.toggleFavorite(module)));
			int starColor = favorite ? 0xFFFACC15 : (starHover ? 0xFFFDE68A : Theme.TEXT_DIM);
			UiIcon.STAR.draw(g, x + w - 43, y + 7, Colors.multiplyAlpha(starColor, starAlpha));
		}
		int gearX = x + w - 25;
		int gearY = y + CARD_HEIGHT - 17;
		boolean gearHover = ui.interactive(gearX, gearY, 15, 13, UiContext.Handler.onClick(() -> openSettings(module)));
		float gear = ui.animate(id + ".gear", gearHover ? 1f : 0f, 16f);
		Draw.chamfer(g, gearX, gearY, 15, 13, Colors.withAlpha(0xFFFFFF, Math.round(14 + 30 * gear)));
		UiIcon.GEAR.draw(g, gearX + 3, gearY + 2, Colors.lerp(Theme.TEXT_MUTED, Theme.accentText(), Math.max(gear, t * 0.4f)));
	}

	private void drawSettings(UiContext ui, int x, int y, int w, int h) {
		var g = ui.g();
		var font = ui.font();
		Module module = selected;
		Widgets.iconButton(ui, "settings.back", x, y, 20, 18, UiIcon.CHEVRON_LEFT, () -> selected = null);
		module.icon().draw(g, x + 26, y + 1);
		Draw.textShadow(g, font, module.name(), x + 46, y + 1, Theme.TEXT);
		Draw.text(g, font, Draw.ellipsize(font, module.description(), w - 120), x + 46, y + 11, Theme.TEXT_MUTED);
		Widgets.toggle(ui, "settings.enabled." + module.id(), x + w - 26, y + 4, module.enabledSetting().get(), module::toggle);
		Widgets.button(ui, "settings.reset", x + w - 74, y + 1, 42, 16, Component.translatable("pretupid.ui.reset"), Widgets.ButtonStyle.GHOST, module::resetSettings);
		int listY = y + 28;
		Draw.rect(g, x, listY - 4, w, 1, Theme.DIVIDER);
		List<Setting<?>> visible = new ArrayList<>();
		for (Setting<?> setting : module.settings()) {
			if (setting.isVisible()) {
				visible.add(setting);
			}
		}
		boolean hud = module instanceof HudModule;
		boolean crosshair = module instanceof net.pretupid.client.module.visual.CrosshairModule;
		int extra = Widgets.ROW_HEIGHT + 14 + (hud && screen.inWorld() ? Widgets.ROW_HEIGHT + 4 : 0) + (crosshair ? 24 : 0);
		int contentHeight = Math.max(1, visible.size()) * Widgets.ROW_HEIGHT + extra;
		int listH = h - 28;
		settingsScroll.begin(ui, "settings." + module.id(), x, listY, w, listH, contentHeight);
		int rowY = listY - settingsScroll.offset();
		int rowWidth = w - 6;
		float appear = ui.animateFrom("settings.appear." + settingsOpenedAt, 0f, 1f, 12f);
		if (crosshair) {
			Widgets.button(ui, "settings.crosshair_designer", x + 4, rowY + 2, 150, 18, Component.translatable("pretupid.crosshair.open_designer"), Widgets.ButtonStyle.PRIMARY,
				() -> net.minecraft.client.Minecraft.getInstance().gui.setScreen(new net.pretupid.client.ui.screen.CrosshairDesignerScreen(screen)));
			rowY += 24;
		}
		int index = 0;
		for (Setting<?> setting : visible) {
			int offset = Math.round((1f - appear) * (8 + index * 2));
			Widgets.settingRow(ui, screen, setting, x, rowY + offset, rowWidth);
			rowY += Widgets.ROW_HEIGHT;
			index++;
		}
		if (visible.isEmpty()) {
			Draw.text(g, font, Draw.ellipsize(font, Component.translatable("pretupid.ui.no_options"), rowWidth - 8), x + 4, rowY + 6, Theme.TEXT_DIM);
			rowY += Widgets.ROW_HEIGHT;
		}
		rowY += 8;
		Widgets.sectionHeader(ui, Component.translatable("pretupid.ui.controls"), x, rowY, rowWidth);
		rowY += 10;
		Widgets.settingRow(ui, screen, module.toggleKey(), x, rowY, rowWidth);
		rowY += Widgets.ROW_HEIGHT + 4;
		if (hud && screen.inWorld()) {
			Widgets.button(ui, "settings.edit_position", x + 4, rowY, 120, 16, Component.translatable("pretupid.ui.edit_position"), Widgets.ButtonStyle.PRIMARY, screen::openHudEditor);
		}
		settingsScroll.end(ui, x, listY, w, listH);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isEscape() && selected != null) {
			selected = null;
			return true;
		}
		if (selected == null && event.hasControlDown() && event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_F) {
			search.setFocused(true);
			return true;
		}
		return false;
	}
}

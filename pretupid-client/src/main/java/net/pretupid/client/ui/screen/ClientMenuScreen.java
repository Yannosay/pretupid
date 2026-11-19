package net.pretupid.client.ui.screen;

import net.pretupid.client.platform.Platform;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.PretupidInfo;
import net.pretupid.client.module.Module;
import net.pretupid.client.ui.Draw;
import net.pretupid.client.ui.Easing;
import net.pretupid.client.ui.Icon;
import net.pretupid.client.ui.PretupidScreen;
import net.pretupid.client.ui.Theme;
import net.pretupid.client.ui.UiContext;
import net.pretupid.client.ui.UiIcon;
import net.pretupid.client.ui.screen.page.ClientSettingsPage;
import net.pretupid.client.ui.screen.page.CosmeticsPage;
import net.pretupid.client.ui.screen.page.FriendsPage;
import net.pretupid.client.ui.screen.page.NewsPage;
import net.pretupid.client.ui.StatusText;
import net.pretupid.client.ui.screen.page.ModsPage;
import net.pretupid.client.ui.screen.page.ModulesPage;
import net.pretupid.client.ui.screen.page.ProfilesPage;
import net.pretupid.client.ui.screen.page.SkinsPage;
import net.pretupid.client.util.Colors;

public final class ClientMenuScreen extends PretupidScreen {
	public enum Tab {
		MODULES("knowledge_book"),
		COSMETICS("name_tag"),
		FRIENDS("cake"),
		SKINS("leather_chestplate"),
		MODS("bundle"),
		PROFILES("writable_book"),
		NEWS("paper"),
		SETTINGS("comparator");

		private final Icon icon;

		Tab(String icon) {
			this.icon = Icon.item(icon);
		}

		public Component label() {
			return Component.translatable("pretupid.tab." + name().toLowerCase(java.util.Locale.ROOT));
		}
	}

	private static final int SIDEBAR = 98;
	private static final int CLOSE_SIZE = 14;
	private static final Icon HUD_EDITOR_ICON = Icon.item("glow_item_frame");
	private static Tab lastTab = Tab.MODULES;

	private final Map<Tab, MenuPage> pages = new EnumMap<>(Tab.class);
	private final ModulesPage modulesPage;
	private final SkinsPage skinsPage;
	private final FriendsPage friendsPage;
	private Tab tab;
	private int switches;

	public ClientMenuScreen(Screen parent) {
		super(Component.translatable("pretupid.menu.title"), parent);
		this.modulesPage = new ModulesPage(this);
		pages.put(Tab.MODULES, modulesPage);
		pages.put(Tab.COSMETICS, new CosmeticsPage(this));
		this.friendsPage = new FriendsPage(this);
		pages.put(Tab.FRIENDS, friendsPage);
		pages.put(Tab.NEWS, new NewsPage(this));
		this.skinsPage = new SkinsPage(PretupidClient.get().skins());
		pages.put(Tab.SKINS, skinsPage);
		pages.put(Tab.MODS, new ModsPage());
		pages.put(Tab.PROFILES, new ProfilesPage(this));
		pages.put(Tab.SETTINGS, new ClientSettingsPage(this));
		this.tab = lastTab;
		pages.get(tab).onOpen();
	}

	public static ClientMenuScreen forModule(Screen parent, Module module) {
		ClientMenuScreen screen = new ClientMenuScreen(parent);
		screen.select(Tab.MODULES);
		screen.modulesPage.openSettings(module);
		return screen;
	}

	public void select(Tab next) {
		if (next == tab) {
			return;
		}
		if (tab == Tab.FRIENDS) {
			friendsPage.onClosed();
		}
		tab = next;
		lastTab = next;
		switches++;
		pages.get(next).onOpen();
	}

	public void openHudEditor() {
		Platform.setScreen(parent instanceof HudEditorScreen editor ? editor : new HudEditorScreen(this));
	}

	public boolean inWorld() {
		return minecraft.level != null;
	}

	@Override
	protected void drawContent(UiContext ui) {
		int w = Math.clamp(Math.round(width * 0.76f), 320, 500);
		int h = Math.clamp(Math.round(height * 0.8f), 200, 300);
		w = Math.min(w, width - 8);
		h = Math.min(h, height - 8);
		int x = (width - w) / 2;
		int y = (height - h) / 2;
		float open = Easing.outCubic(openProgress());
		float scale = 0.94f + 0.06f * open;
		var g = ui.g();
		g.pose().pushMatrix();
		g.pose().translate(width / 2f, height / 2f + (1f - open) * 10f);
		g.pose().scale(scale, scale);
		g.pose().translate(-width / 2f, -height / 2f);
		Draw.shadow(g, x, y, w, h, 6, 1f);
		Draw.glass(g, x, y, w, h, Theme.WINDOW, Theme.WINDOW_BORDER);
		drawSidebar(ui, x, y, h);
		int contentX = x + SIDEBAR + 10;
		int contentW = w - SIDEBAR - 20;
		float slide = ui.animateFrom("page.slide." + switches, 1f, 0f, 16f);
		g.pose().pushMatrix();
		g.pose().translate(Math.round(slide * 14f), 0f);
		pages.get(tab).draw(ui, contentX, y + 10, contentW, h - 20);
		g.pose().popMatrix();
		drawCloseButton(ui, x + w - CLOSE_SIZE - 4, y + 4);
		g.pose().popMatrix();
	}

	private void drawCloseButton(UiContext ui, int x, int y) {
		var g = ui.g();
		boolean hover = ui.interactive(x, y, CLOSE_SIZE, CLOSE_SIZE, UiContext.Handler.onClick(this::onClose));
		float t = ui.animate("menu.close", hover ? 1f : 0f, 18f);
		if (t > 0.01f) {
			Draw.chamfer(g, x, y, CLOSE_SIZE, CLOSE_SIZE, Colors.withAlpha(Theme.DANGER, Math.round(200 * t)));
		}
		UiIcon.CLOSE.draw(g, x + (CLOSE_SIZE - UiIcon.SIZE) / 2, y + (CLOSE_SIZE - UiIcon.SIZE) / 2, Colors.lerp(Theme.TEXT_MUTED, 0xFFFFFFFF, t));
	}

	private void drawSidebar(UiContext ui, int x, int y, int h) {
		var g = ui.g();
		Draw.rect(g, x + 1, y + 1, SIDEBAR, h - 2, Theme.SIDEBAR);
		Draw.rect(g, x + SIDEBAR + 1, y + 1, 1, h - 2, Theme.DIVIDER);
		UiIcon.drawLogo(g, x + 8, y + 8, 20);
		Draw.textShadow(g, font, Component.literal("Pretupid").withStyle(style -> style.withBold(true)), x + 32, y + 9, Theme.TEXT);
		Draw.text(g, font, Draw.ellipsize(font, PretupidInfo.displayVersion(), SIDEBAR - 36), x + 32, y + 19, Theme.TEXT_DIM);
		int itemY = y + 36;
		int step = 22;
		int navBottom = itemY + Tab.values().length * step;
		int editHeight = inWorld() ? 24 : 0;
		boolean showStatus = navBottom + editHeight + 28 <= y + h;
		if (navBottom + editHeight > y + h - 4) {
			step = 20;
		}
		for (Tab entry : Tab.values()) {
			drawNavItem(ui, entry, x + 5, itemY, SIDEBAR - 9);
			itemY += step;
		}
		if (inWorld()) {
			int editY = showStatus ? y + h - 52 : y + h - 24;
			drawHudEditorItem(ui, x + 5, Math.max(itemY + 2, editY), SIDEBAR - 9);
		}
		if (showStatus) {
			drawStatus(ui, x + 8, y + h - 24, SIDEBAR - 14);
		}
	}

	private void drawNavItem(UiContext ui, Tab entry, int x, int y, int w) {
		var g = ui.g();
		boolean selected = entry == tab;
		boolean hover = ui.interactive(x, y, w, 20, UiContext.Handler.onClick(() -> select(entry)));
		float h = ui.animate("nav.hover." + entry, hover ? 1f : 0f, 16f);
		float s = ui.animate("nav.selected." + entry, selected ? 1f : 0f, 14f);
		int fill = Colors.lerp(Colors.lerp(0x00FFFFFF, Theme.SURFACE, h), Theme.accent(60), s);
		Draw.chamfer(g, x, y, w, 20, fill);
		if (s > 0.01f) {
			int barHeight = Math.round(12 * s);
			Draw.rect(g, x, y + 10 - barHeight / 2, 2, barHeight, Theme.accent());
		}
		entry.icon.draw(g, x + 4, y + 2);
		Draw.text(g, font, entry.label(), x + 24 + Math.round(h * 2f), y + 6, Colors.lerp(Theme.TEXT_MUTED, Theme.TEXT, Math.max(h, s)));
		int badge = badgeCount(entry);
		if (badge > 0) {
			String count = badge > 9 ? "9+" : Integer.toString(badge);
			int badgeWidth = font.width(count) + 5;
			Draw.chamfer(g, x + w - badgeWidth - 3, y + 5, badgeWidth, 10, Theme.accent());
			Draw.text(g, font, count, x + w - badgeWidth, y + 6, 0xFFFFFFFF);
		}
	}

	private static int badgeCount(Tab entry) {
		PretupidClient client = PretupidClient.get();
		return switch (entry) {
			case FRIENDS -> client.social().pendingCount();
			case NEWS -> client.news().unreadCount();
			default -> 0;
		};
	}

	private void drawHudEditorItem(UiContext ui, int x, int y, int w) {
		var g = ui.g();
		boolean hover = ui.interactive(x, y, w, 20, UiContext.Handler.onClick(this::openHudEditor));
		float t = ui.animate("nav.hud", hover ? 1f : 0f, 16f);
		Draw.chamfer(g, x, y, w, 20, Colors.lerp(Theme.accent(170), Theme.accent(235), t));
		Draw.rect(g, x + 1, y + 19, w - 2, 1, 0x40000000);
		HUD_EDITOR_ICON.draw(g, x + 4, y + 2);
		Draw.textShadow(g, font, Component.translatable("pretupid.menu.edit_hud"), x + 24, y + 6, 0xFFFFFFFF);
	}

	private void drawStatus(UiContext ui, int x, int y, int w) {
		StatusText.drawSession(ui, x, y, w);
		Component launcher = PretupidInfo.launchedByPretupidLauncher()
			? Component.translatable("pretupid.menu.via_launcher")
			: Component.translatable("pretupid.menu.standalone");
		Draw.text(ui.g(), font, Draw.ellipsize(font, launcher, w), x, y + 11, Theme.TEXT_DIM);
	}

	@Override
	public void onFilesDrop(List<Path> paths) {
		if (pages.get(tab).onFilesDropped(paths)) {
			return;
		}
		select(Tab.SKINS);
		skinsPage.onFilesDropped(paths);
	}

	@Override
	protected boolean handleKey(KeyEvent event) {
		if (pages.get(tab).keyPressed(event)) {
			return true;
		}
		if (event.hasControlDown() && event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_TAB) {
			Tab[] tabs = Tab.values();
			select(tabs[Math.floorMod(tab.ordinal() + (event.hasShiftDown() ? -1 : 1), tabs.length)]);
			return true;
		}
		if (PretupidClient.get().keybinds().openMenu().matches(event) && ui.focusedInput() == null) {
			onClose();
			return true;
		}
		return false;
	}

	@Override
	public void removed() {
		friendsPage.onClosed();
		PretupidClient.get().modIcons().release();
		for (MenuPage page : pages.values()) {
			page.release();
		}
		super.removed();
	}
}

package net.pretupid.client.ui.screen.page;

import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.PretupidInfo;
import net.pretupid.client.config.ClientSettings;
import net.pretupid.client.module.Module;
import net.pretupid.client.setting.Setting;
import net.pretupid.client.ui.Draw;
import net.pretupid.client.ui.ScrollState;
import net.pretupid.client.ui.Theme;
import net.pretupid.client.ui.UiContext;
import net.pretupid.client.ui.Widgets;
import net.pretupid.client.ui.screen.ClientMenuScreen;
import net.pretupid.client.ui.screen.MenuPage;
import net.pretupid.client.util.PlatformOpener;

public final class ClientSettingsPage implements MenuPage {
	private final ClientMenuScreen screen;
	private final ScrollState scroll = new ScrollState();
	private long resetArmedAt;

	public ClientSettingsPage(ClientMenuScreen screen) {
		this.screen = screen;
	}

	private record Section(String key, List<Setting<?>> settings) {
	}

	private List<Section> sections(ClientSettings settings) {
		return List.of(
			new Section("interface", List.of(settings.accent, settings.moduleText, settings.animations, settings.animationSpeed, settings.uiSounds, settings.menuBlur, settings.toggleToasts)),
			new Section("hud_editor", List.of(settings.hudEditorKey, settings.hudEditorToolbar)),
			new Section("integration", List.of(settings.enhancedTitleScreen, settings.titleScreenPanels, settings.serverCompatibility, settings.newsButton, settings.titleScreenButton, settings.pauseScreenButton, settings.hideHudInDebug)),
			new Section("social", List.of(settings.sharePresence, settings.chatEmojis, settings.showOtherBadges, settings.tabListBadges, settings.showCosmetics, settings.allowFriendRequests,
				settings.shareServerWithFriends, settings.friendNotifications)),
			new Section("updates", List.of(settings.updateChecks)),
			new Section("profiles", List.of(settings.autoProfileSwitch))
		);
	}

	@Override
	public void draw(UiContext ui, int x, int y, int w, int h) {
		var g = ui.g();
		var font = ui.font();
		ClientSettings settings = PretupidClient.get().settings();
		List<Section> sections = sections(settings);
		int contentHeight = 0;
		for (Section section : sections) {
			contentHeight += 14 + section.settings().size() * Widgets.ROW_HEIGHT + 8;
		}
		contentHeight += 26 + 14 + 22 + 8 + 14 + 40;
		scroll.begin(ui, "client_settings", x, y, w, h, contentHeight);
		int rowY = y - scroll.offset();
		int rowWidth = w - 6;
		for (Section section : sections) {
			Widgets.sectionHeader(ui, Component.translatable("pretupid.settings.section." + section.key()), x, rowY + 2, rowWidth);
			rowY += 14;
			for (Setting<?> setting : section.settings()) {
				Widgets.settingRow(ui, screen, setting, x, rowY, rowWidth);
				rowY += Widgets.ROW_HEIGHT;
			}
			rowY += 8;
		}
		var updates = PretupidClient.get().updates();
		Component updateLabel = switch (updates.state()) {
			case CHECKING -> Component.translatable("pretupid.update.checking");
			case AVAILABLE, DOWNLOADING, READY, FAILED -> Component.translatable("pretupid.update.available_short", updates.release() == null ? "?" : updates.release().version());
			case UP_TO_DATE -> Component.translatable("pretupid.update.up_to_date");
			default -> Component.translatable("pretupid.update.check_now");
		};
		Widgets.button(ui, "settings.update", x + 4, rowY, rowWidth - 8, 18, updateLabel,
			updates.release() != null ? Widgets.ButtonStyle.PRIMARY : Widgets.ButtonStyle.SECONDARY, () -> {
				if (updates.release() != null) {
					Minecraft.getInstance().gui.setScreen(new net.pretupid.client.ui.screen.UpdateScreen(screen));
				} else {
					updates.check();
				}
			});
		rowY += 26;
		Widgets.sectionHeader(ui, Component.translatable("pretupid.settings.section.tools"), x, rowY + 2, rowWidth);
		rowY += 14;
		int buttonWidth = Math.min(110, (rowWidth - 12) / 3);
		Minecraft minecraft = Minecraft.getInstance();
		Widgets.button(ui, "settings.keybinds", x + 4, rowY, buttonWidth, 18, Component.translatable("pretupid.settings.keybinds"), Widgets.ButtonStyle.SECONDARY,
			() -> minecraft.gui.setScreen(new KeyBindsScreen(screen, minecraft.options)));
		Widgets.button(ui, "settings.config", x + 8 + buttonWidth, rowY, buttonWidth, 18, Component.translatable("pretupid.settings.open_config"), Widgets.ButtonStyle.SECONDARY,
			() -> PlatformOpener.openFolder(FabricLoader.getInstance().getConfigDir().resolve(PretupidClient.MOD_ID)));
		boolean armed = System.currentTimeMillis() - resetArmedAt < 3000L;
		Widgets.button(ui, "settings.reset_all", x + 12 + buttonWidth * 2, rowY, buttonWidth, 18,
			Component.translatable(armed ? "pretupid.ui.confirm" : "pretupid.settings.reset_all"), Widgets.ButtonStyle.DANGER, () -> {
				if (System.currentTimeMillis() - resetArmedAt < 3000L) {
					resetArmedAt = 0L;
					for (Module module : PretupidClient.get().modules().all()) {
						module.resetSettings();
					}
				} else {
					resetArmedAt = System.currentTimeMillis();
				}
			});
		rowY += 30;
		Widgets.sectionHeader(ui, Component.translatable("pretupid.settings.section.about"), x, rowY + 2, rowWidth);
		rowY += 14;
		Draw.text(g, font, Component.translatable("pretupid.settings.about.version", PretupidInfo.displayVersion()), x + 4, rowY, Theme.TEXT_MUTED);
		Draw.text(g, font, Component.translatable("pretupid.settings.about.minecraft", SharedConstants.getCurrentVersion().name()), x + 4, rowY + 11, Theme.TEXT_MUTED);
		Component launcher = PretupidInfo.launchedByPretupidLauncher()
			? Component.translatable("pretupid.settings.about.launcher", PretupidInfo.launcherVersion().orElse("?"))
			: Component.translatable("pretupid.settings.about.standalone");
		Draw.text(g, font, launcher, x + 4, rowY + 22, Theme.TEXT_MUTED);
		scroll.end(ui, x, y, w, h);
	}
}

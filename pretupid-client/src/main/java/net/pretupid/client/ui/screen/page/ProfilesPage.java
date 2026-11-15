package net.pretupid.client.ui.screen.page;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.config.ConfigStore;
import net.pretupid.client.ui.Draw;
import net.pretupid.client.ui.ScrollState;
import net.pretupid.client.ui.TextInput;
import net.pretupid.client.ui.Theme;
import net.pretupid.client.ui.UiContext;
import net.pretupid.client.ui.Widgets;
import net.pretupid.client.ui.screen.ClientMenuScreen;
import net.pretupid.client.ui.screen.MenuPage;
import net.pretupid.client.util.Colors;
import net.pretupid.client.util.PlatformOpener;

public final class ProfilesPage implements MenuPage {
	private static final int ROW_HEIGHT = 36;

	private final ClientMenuScreen screen;
	private final TextInput nameInput = new TextInput(32, cp -> Character.isLetterOrDigit(cp) || cp == '-' || cp == '_' || cp == ' ');
	private final ScrollState scroll = new ScrollState();
	private String pendingDelete;
	private long pendingDeleteAt;

	public ProfilesPage(ClientMenuScreen screen) {
		this.screen = screen;
		nameInput.onSubmit(this::create);
	}

	private void create() {
		String name = ConfigStore.sanitizeProfileName(nameInput.value());
		if (name.isEmpty()) {
			return;
		}
		PretupidClient.get().config().createProfile(name).ifPresent(created -> {
			nameInput.setValue("");
			PretupidClient.get().notifications().profileSwitched(created);
		});
	}

	@Override
	public void draw(UiContext ui, int x, int y, int w, int h) {
		var g = ui.g();
		var font = ui.font();
		ConfigStore config = PretupidClient.get().config();
		Draw.textShadow(g, font, Component.translatable("pretupid.profiles.title"), x, y + 1, Theme.TEXT);
		Draw.text(g, font, Draw.ellipsize(font, Component.translatable("pretupid.profiles.description"), w), x, y + 12, Theme.TEXT_MUTED);
		int formY = y + 28;
		int buttonWidth = 64;
		nameInput.draw(ui, x, formY, w - buttonWidth - 6, 18, Component.translatable("pretupid.profiles.new_placeholder"));
		Widgets.button(ui, "profiles.create", x + w - buttonWidth, formY, buttonWidth, 18, Component.translatable("pretupid.profiles.create"), Widgets.ButtonStyle.PRIMARY, this::create);
		int toggleY = formY + 24;
		Widgets.settingRow(ui, screen, PretupidClient.get().settings().autoProfileSwitch, x, toggleY, w);
		int listY = toggleY + Widgets.ROW_HEIGHT + 6;
		int listH = y + h - listY - 22;
		List<String> profiles = config.profiles();
		scroll.begin(ui, "profiles", x, listY, w, listH, profiles.size() * (ROW_HEIGHT + 4));
		int rowY = listY - scroll.offset();
		for (String profile : profiles) {
			drawRow(ui, config, profile, x, rowY, w - 6);
			rowY += ROW_HEIGHT + 4;
		}
		scroll.end(ui, x, listY, w, listH);
		Widgets.button(ui, "profiles.folder", x, y + h - 16, 110, 16, Component.translatable("pretupid.profiles.open_folder"), Widgets.ButtonStyle.GHOST,
			() -> PlatformOpener.openFolder(config.profilesDirectory()));
	}

	private void drawRow(UiContext ui, ConfigStore config, String profile, int x, int y, int w) {
		var g = ui.g();
		var font = ui.font();
		boolean active = profile.equals(config.activeProfile());
		boolean hover = ui.hovered(x, y, w, ROW_HEIGHT);
		float t = ui.animate("profile." + profile, hover ? 1f : 0f, 16f);
		int fill = Colors.lerp(Theme.SURFACE, Theme.SURFACE_HOVER, t);
		if (active) {
			fill = Colors.lerp(fill, Theme.accent(70), 0.6f);
		}
		Draw.chamfer(g, x, y, w, ROW_HEIGHT, fill);
		if (active) {
			Draw.border(g, x, y, w, ROW_HEIGHT, Theme.accent(140));
			Draw.rect(g, x + 1, y + 6, 2, ROW_HEIGHT - 12, Theme.accent());
		}
		Draw.text(g, font, Component.literal(profile), x + 10, y + 7, Theme.TEXT);
		if (active) {
			Component chip = Component.translatable("pretupid.profiles.active");
			int chipX = x + 14 + font.width(profile);
			Draw.chamfer(g, chipX, y + 5, font.width(chip) + 6, 11, Theme.accent(170));
			Draw.text(g, font, chip, chipX + 3, y + 7, 0xFFFFFFFF);
		}
		List<String> servers = config.serversFor(profile);
		int serverX = x + 10;
		int serverY = y + 21;
		if (servers.isEmpty()) {
			Draw.text(g, font, Component.translatable("pretupid.profiles.no_servers"), serverX, serverY, Theme.TEXT_DIM);
		}
		for (String server : servers) {
			int chipWidth = font.width(server) + 14;
			if (serverX + chipWidth > x + w - 150) {
				Draw.text(g, font, "…", serverX, serverY, Theme.TEXT_DIM);
				break;
			}
			boolean chipHover = ui.interactive(serverX, serverY - 2, chipWidth, 11, UiContext.Handler.onClick(() -> config.unbindServer(profile, server)));
			Draw.chamfer(g, serverX, serverY - 2, chipWidth, 11, chipHover ? Colors.withAlpha(Theme.DANGER, 80) : Theme.SURFACE_ACTIVE);
			Draw.text(g, font, server, serverX + 3, serverY, Theme.TEXT_MUTED);
			Draw.text(g, font, "×", serverX + chipWidth - 8, serverY, chipHover ? Theme.DANGER : Theme.TEXT_DIM);
			if (chipHover) {
				ui.tooltip(Component.translatable("pretupid.profiles.unbind"));
			}
			serverX += chipWidth + 4;
		}
		int buttonY = y + (ROW_HEIGHT - 16) / 2;
		int buttonX = x + w - 6;
		if (!ConfigStore.DEFAULT_PROFILE.equals(profile)) {
			boolean confirming = profile.equals(pendingDelete) && System.currentTimeMillis() - pendingDeleteAt < 3000L;
			buttonX -= 46;
			Widgets.button(ui, "profile.delete." + profile, buttonX, buttonY, 46, 16,
				Component.translatable(confirming ? "pretupid.ui.confirm" : "pretupid.ui.delete"), Widgets.ButtonStyle.DANGER, () -> {
					if (profile.equals(pendingDelete) && System.currentTimeMillis() - pendingDeleteAt < 3000L) {
						config.deleteProfile(profile);
						pendingDelete = null;
					} else {
						pendingDelete = profile;
						pendingDeleteAt = System.currentTimeMillis();
					}
				});
		}
		ServerData server = Minecraft.getInstance().getCurrentServer();
		if (server != null && !Minecraft.getInstance().hasSingleplayerServer()) {
			buttonX -= 52;
			Widgets.button(ui, "profile.bind." + profile, buttonX, buttonY, 48, 16, Component.translatable("pretupid.profiles.bind"), Widgets.ButtonStyle.SECONDARY,
				() -> config.bindServer(profile, server.ip));
			if (ui.hovered(buttonX, buttonY, 48, 16)) {
				ui.tooltip(Component.translatable("pretupid.profiles.bind_tooltip"));
			}
		}
		if (!active) {
			buttonX -= 44;
			Widgets.button(ui, "profile.use." + profile, buttonX, buttonY, 40, 16, Component.translatable("pretupid.profiles.use"), Widgets.ButtonStyle.PRIMARY,
				() -> {
					if (config.switchProfile(profile)) {
						PretupidClient.get().notifications().profileSwitched(profile);
					}
				});
		}
	}
}

package net.pretupid.client.ui.screen.page;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.config.ClientSettings;
import net.pretupid.client.cosmetic.Catalog;
import net.pretupid.client.cosmetic.EmoteWheel;
import net.pretupid.client.cosmetic.CosmeticsService;
import net.pretupid.client.nametag.NametagFormatter;
import net.pretupid.client.nametag.NametagPresets;
import net.pretupid.client.nametag.NametagStyle;
import net.pretupid.client.network.ApiClient;
import net.pretupid.client.network.RemoteProfile;
import net.pretupid.client.setting.Setting;
import net.pretupid.client.skins.MojangCapes;
import net.pretupid.client.ui.Draw;
import net.pretupid.client.ui.ScrollState;
import net.pretupid.client.ui.StatusText;
import net.pretupid.client.ui.Theme;
import net.pretupid.client.ui.UiContext;
import net.pretupid.client.ui.Widgets;
import net.pretupid.client.ui.screen.ClientMenuScreen;
import net.pretupid.client.ui.screen.MenuPage;
import net.pretupid.client.util.Colors;
import net.pretupid.client.util.PlatformOpener;

public final class CosmeticsPage implements MenuPage {
	private enum Section {
		NAMETAG,
		CAPE,
		WEARABLES,
		TRAIL,
		EMOTES
	}

	private static final int PREVIEW_HEIGHT = 58;
	private static final int CARD_WIDTH = 62;
	private static final int CARD_HEIGHT = 84;

	private final ClientMenuScreen screen;
	private final ScrollState scroll = new ScrollState();
	private static final int WHEEL_SLOT_HEIGHT = 26;
	private static final int EMOTE_WHEEL_WIDTH = 28;
	private static final int EMOTE_KEY_WIDTH = 46;
	private static final int EMOTE_PLAY_WIDTH = 34;
	private int selectedWheelSlot = -1;
	private Section section = Section.NAMETAG;
	private Component message;
	private boolean messageError;
	private long messageAt;
	private List<Path> localCapes = List.of();

	public CosmeticsPage(ClientMenuScreen screen) {
		this.screen = screen;
	}

	private static Path capesFolder() {
		return FabricLoader.getInstance().getConfigDir().resolve(PretupidClient.MOD_ID).resolve("capes");
	}

	@Override
	public void onOpen() {
		scanLocalCapes();
		PretupidClient.get().cosmetics().refreshCatalog();
		PretupidClient.get().mojangCapes().refresh();
	}

	private void scanLocalCapes() {
		List<Path> found = new ArrayList<>();
		Path folder = capesFolder();
		if (Files.isDirectory(folder)) {
			try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder, "*.png")) {
				for (Path path : stream) {
					if (found.size() < 12) {
						found.add(path);
					}
				}
			} catch (IOException exception) {
				PretupidClient.LOGGER.debug("Could not list capes folder: {}", exception.getMessage());
			}
		}
		localCapes = found;
	}

	@Override
	public boolean onFilesDropped(List<Path> paths) {
		for (Path path : paths) {
			if (path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png")) {
				section = Section.CAPE;
				upload(path);
				return true;
			}
		}
		return false;
	}

	private void show(Component text, boolean error) {
		message = text;
		messageError = error;
		messageAt = System.currentTimeMillis();
	}

	private void upload(Path path) {
		if (!PretupidClient.get().session().online()) {
			show(Component.translatable("pretupid.cosmetics.cape.offline"), true);
			return;
		}
		show(Component.translatable("pretupid.cosmetics.cape.uploading"), false);
		PretupidClient.get().cosmetics().uploadCape(path).whenComplete((ignored, error) -> Minecraft.getInstance().execute(() -> {
			if (error != null) {
				show(Component.literal(ApiClient.describe(error)), true);
			} else {
				show(Component.translatable("pretupid.cosmetics.cape.uploaded"), false);
			}
		}));
	}

	@Override
	public void draw(UiContext ui, int x, int y, int w, int h) {
		Section[] values = Section.values();
		int tabWidth = (w - 6 - (values.length - 1) * 4) / values.length;
		for (int i = 0; i < values.length; i++) {
			Section entry = values[i];
			Widgets.button(ui, "cosmetics.section." + entry, x + i * (tabWidth + 4), y, tabWidth, 16,
				Component.translatable("pretupid.cosmetics.section." + entry.name().toLowerCase(Locale.ROOT)),
				entry == section ? Widgets.ButtonStyle.PRIMARY : Widgets.ButtonStyle.GHOST, () -> {
					section = entry;
					scroll.reset();
				});
		}
		int bodyY = y + 22;
		int bodyH = h - 22 - 12;
		switch (section) {
			case NAMETAG -> drawNametag(ui, x, bodyY, w, bodyH);
			case CAPE -> drawCapes(ui, x, bodyY, w, bodyH);
			case WEARABLES -> drawWearables(ui, x, bodyY, w, bodyH);
			case TRAIL -> drawTrails(ui, x, bodyY, w, bodyH);
			case EMOTES -> drawEmotes(ui, x, bodyY, w, bodyH);
		}
		drawFooter(ui, x, y + h - 9, w);
	}

	private void drawFooter(UiContext ui, int x, int y, int w) {
		if (message != null && System.currentTimeMillis() - messageAt < 8000L) {
			Draw.text(ui.g(), ui.font(), Draw.ellipsize(ui.font(), message, w), x, y, messageError ? Theme.DANGER : Theme.SUCCESS);
			return;
		}
		StatusText.drawSession(ui, x, y, w);
	}

	private static final int PRESET_HEIGHT = 20;
	private static final int PRESET_MIN_WIDTH = 74;

	private void drawNametag(UiContext ui, int x, int y, int w, int h) {
		PretupidClient client = PretupidClient.get();
		ClientSettings settings = client.settings();
		drawNametagPreview(ui, x, y, w - 6);
		int listY = y + PREVIEW_HEIGHT + 6;
		int listH = h - PREVIEW_HEIGHT - 6;
		List<NametagPresets.Preset> presets = NametagPresets.all();
		int areaW = w - 6;
		int columns = Math.max(1, (areaW + 4) / (PRESET_MIN_WIDTH + 4));
		int presetW = (areaW - (columns - 1) * 4) / columns;
		int presetRows = (presets.size() + 1 + columns - 1) / columns;
		List<Setting<?>> custom = visible(settings.nametagAnimation, settings.nametagPrimary, settings.nametagSecondary, settings.nametagEffect, settings.nametagSpeed,
			settings.nametagBold, settings.nametagBadge, settings.nametagMovingBackground);
		List<Setting<?>> display = visible(settings.showOtherBadges, settings.tabListBadges);
		List<FormattedCharSequence> hint = ui.font().split(Component.translatable("pretupid.cosmetics.nametag.hint"), areaW - 4);
		int content = 16 + hint.size() * 10 + 4 + presetRows * (PRESET_HEIGHT + 4) + 8 + 16 + custom.size() * Widgets.ROW_HEIGHT + 8 + 16 + display.size() * Widgets.ROW_HEIGHT;
		scroll.begin(ui, "cosmetics.nametag", x, listY, w, listH, content);
		int rowY = listY - scroll.offset();
		Widgets.sectionHeader(ui, Component.translatable("pretupid.cosmetics.nametag.presets"), x, rowY + 2, areaW);
		rowY += 16;
		for (FormattedCharSequence line : hint) {
			Draw.text(ui.g(), ui.font(), line, x, rowY, Theme.TEXT_DIM, false);
			rowY += 10;
		}
		rowY += 4;
		NametagStyle current = settings.nametagStyle();
		for (int i = 0; i <= presets.size(); i++) {
			int px = x + (i % columns) * (presetW + 4);
			int py = rowY + (i / columns) * (PRESET_HEIGHT + 4);
			if (i == 0) {
				drawPreset(ui, "original", px, py, presetW, Component.translatable("pretupid.cosmetics.nametag.original"), NametagStyle.BADGE_ONLY,
					!current.decoratesName(), Component.translatable("pretupid.cosmetics.nametag.original_hint"));
			} else {
				NametagPresets.Preset preset = presets.get(i - 1);
				drawPreset(ui, preset.id(), px, py, presetW, Component.literal(preset.name()), preset.style(), preset.matches(current), Component.literal(preset.description()));
			}
		}
		rowY += presetRows * (PRESET_HEIGHT + 4) + 8;
		Widgets.sectionHeader(ui, Component.translatable("pretupid.cosmetics.nametag.customize"), x, rowY + 2, areaW);
		rowY += 16;
		for (Setting<?> setting : custom) {
			Widgets.settingRow(ui, screen, setting, x, rowY, areaW);
			rowY += Widgets.ROW_HEIGHT;
		}
		rowY += 8;
		Widgets.sectionHeader(ui, Component.translatable("pretupid.cosmetics.nametag.visibility"), x, rowY + 2, areaW);
		rowY += 16;
		for (Setting<?> setting : display) {
			Widgets.settingRow(ui, screen, setting, x, rowY, areaW);
			rowY += Widgets.ROW_HEIGHT;
		}
		scroll.end(ui, x, listY, w, listH);
	}

	private static List<Setting<?>> visible(Setting<?>... candidates) {
		List<Setting<?>> rows = new ArrayList<>();
		for (Setting<?> setting : candidates) {
			if (setting.isVisible()) {
				rows.add(setting);
			}
		}
		return rows;
	}

	private void drawPreset(UiContext ui, String id, int x, int y, int w, Component label, NametagStyle style, boolean selected, Component tooltip) {
		var g = ui.g();
		boolean hover = ui.interactive(x, y, w, PRESET_HEIGHT, UiContext.Handler.onClick(() -> {
			PretupidClient client = PretupidClient.get();
			client.settings().applyNametagStyle(style);
			client.presence().requestHeartbeat();
		}));
		float t = ui.animate("cosmetics.preset." + id, hover ? 1f : 0f, 16f);
		Draw.chamfer(g, x, y, w, PRESET_HEIGHT, Colors.lerp(Theme.SURFACE, Theme.SURFACE_HOVER, t));
		Draw.border(g, x, y, w, PRESET_HEIGHT, selected ? Theme.accent() : Colors.lerp(Theme.SURFACE_BORDER, 0x60FFFFFF, t));
		String text = Draw.ellipsize(ui.font(), label, w - 10).getString();
		Component sample = NametagFormatter.format(Component.literal(text), text, style.withBadge(false), false, true);
		Draw.centered(g, ui.font(), sample, x + w / 2, y + 6, selected || !style.decoratesName() ? (selected ? Theme.accentText() : Theme.TEXT) : 0xFFFFFFFF);
		if (hover && !tooltip.getString().isEmpty()) {
			ui.tooltip(tooltip);
		}
	}

	private void drawNametagPreview(UiContext ui, int x, int y, int w) {
		var g = ui.g();
		var font = ui.font();
		Draw.chamfer(g, x, y, w, PREVIEW_HEIGHT, 0x30000000);
		Draw.verticalGradient(g, x + 1, y + 1, w - 2, PREVIEW_HEIGHT - 2, Colors.withAlpha(Theme.accent(), 46), 0x10000000);
		Draw.border(g, x, y, w, PREVIEW_HEIGHT, Theme.SURFACE_BORDER);
		String name = Minecraft.getInstance().getUser().getName();
		Component nametag = NametagFormatter.format(Component.literal(name), name, PretupidClient.get().settings().nametagStyle(), true, true);
		float scale = 2f;
		int tagWidth = Math.round(font.width(nametag) * scale);
		int tagX = x + (w - tagWidth) / 2;
		int tagY = y + 12;
		Draw.rect(g, tagX - 4, tagY - 4, tagWidth + 8, Math.round(9 * scale) + 6, 0x40000000);
		Draw.scaled(g, font, nametag, tagX, tagY, scale, 0xFFFFFFFF, false);
		Component tab = NametagFormatter.format(Component.literal(name), name, PretupidClient.get().settings().nametagStyle(), true, false);
		int tabWidth = font.width(tab) + 8;
		int tabX = x + (w - tabWidth) / 2;
		Draw.rect(g, tabX, tagY + 28, tabWidth, 10, 0x50000000);
		Draw.text(g, font, tab, tabX + 4, tagY + 29, 0xFFFFFFFF);
		Draw.text(g, font, Component.translatable("pretupid.cosmetics.tab_preview"), tabX + tabWidth + 6, tagY + 29, Theme.TEXT_DIM);
	}

	private void drawCapes(UiContext ui, int x, int y, int w, int h) {
		PretupidClient client = PretupidClient.get();
		CosmeticsService cosmetics = client.cosmetics();
		ClientSettings settings = client.settings();
		int previewWidth = drawPlayerPreview(ui, x + w - 86, y, 80, h);
		int areaW = w - previewWidth - 8;
		List<CapeOption> options = new ArrayList<>();
		options.add(new CapeOption("", Component.translatable("pretupid.cosmetics.none"), null));
		CosmeticsService.OwnCape own = cosmetics.ownCape();
		if (own != null && own.texture() != null) {
			options.add(new CapeOption("custom", Component.translatable("pretupid.cosmetics.cape.mine"), own.texture()));
		}
		for (Catalog.CapeEntry entry : cosmetics.catalog().capes()) {
			options.add(new CapeOption("catalog:" + entry.id(), Component.literal(entry.name()), entry.texture()));
		}
		int columns = Math.max(1, (areaW - 6) / (CARD_WIDTH + 4));
		int gridRows = (options.size() + columns - 1) / columns;
		MojangCapes mojang = client.mojangCapes();
		int mojangRows = mojang.state() == MojangCapes.State.READY ? (mojang.capes().size() + 1 + columns - 1) / columns : 0;
		int mojangHeight = 14 + (mojangRows > 0 ? mojangRows * (CARD_HEIGHT + 4) : 14);
		int toolsHeight = Widgets.ROW_HEIGHT + 4 + 14 + 22 + (localCapes.isEmpty() ? 0 : 12 + localCapes.size() * 20) + 26;
		scroll.begin(ui, "cosmetics.capes", x, y, areaW, h, 14 + gridRows * (CARD_HEIGHT + 4) + mojangHeight + toolsHeight);
		int top = y - scroll.offset();
		Widgets.sectionHeader(ui, Component.translatable("pretupid.cosmetics.cape.pretupid"), x, top + 2, areaW - 6);
		top += 14;
		for (int i = 0; i < options.size(); i++) {
			CapeOption option = options.get(i);
			int cx = x + (i % columns) * (CARD_WIDTH + 4);
			int cy = top + (i / columns) * (CARD_HEIGHT + 4);
			boolean selected = settings.cape.get().equals(option.value());
			Identifier texture = option.texture() == null ? null : cosmetics.capes().get(option.texture());
			drawCapeCard(ui, "cape." + i, cx, cy, option.label(), texture, option.texture() != null, selected, () -> {
				settings.cape.set(option.value());
				client.presence().requestHeartbeat();
			});
		}
		int rowY = top + gridRows * (CARD_HEIGHT + 4) + 4;
		rowY = drawMojangCapes(ui, mojang, x, rowY, areaW, columns);
		Widgets.settingRow(ui, screen, settings.capePhysics, x, rowY, areaW - 6);
		rowY += Widgets.ROW_HEIGHT + 4;
		Widgets.sectionHeader(ui, Component.translatable("pretupid.cosmetics.cape.custom"), x, rowY, areaW - 6);
		rowY += 14;
		int buttonWidth = (areaW - 14) / 2;
		Widgets.button(ui, "cape.folder", x, rowY, buttonWidth, 18, Component.translatable("pretupid.cosmetics.cape.open_folder"), Widgets.ButtonStyle.SECONDARY, () -> {
			try {
				Files.createDirectories(capesFolder());
			} catch (IOException exception) {
				show(Component.literal(exception.getMessage()), true);
				return;
			}
			PlatformOpener.openFolder(capesFolder());
		});
		boolean canDelete = own != null && own.texture() != null && !cosmetics.busy();
		Widgets.button(ui, "cape.delete", x + buttonWidth + 4, rowY, buttonWidth, 18, Component.translatable("pretupid.cosmetics.cape.delete"),
			canDelete ? Widgets.ButtonStyle.DANGER : Widgets.ButtonStyle.GHOST, () -> {
				if (!canDelete) {
					return;
				}
				cosmetics.deleteCape().whenComplete((ignored, error) -> Minecraft.getInstance().execute(() ->
					show(error == null ? Component.translatable("pretupid.cosmetics.cape.deleted") : Component.literal(ApiClient.describe(error)), error != null)));
			});
		rowY += 22;
		if (!localCapes.isEmpty()) {
			Draw.text(ui.g(), ui.font(), Component.translatable("pretupid.cosmetics.cape.local"), x, rowY, Theme.TEXT_MUTED);
			rowY += 12;
			for (Path path : localCapes) {
				Draw.chamfer(ui.g(), x, rowY, areaW - 6, 18, Theme.SURFACE);
				Draw.text(ui.g(), ui.font(), Draw.ellipsize(ui.font(), path.getFileName().toString(), areaW - 80), x + 5, rowY + 5, Theme.TEXT);
				Widgets.button(ui, "cape.upload." + path.getFileName(), x + areaW - 66, rowY + 1, 58, 16, Component.translatable("pretupid.cosmetics.cape.upload"),
					Widgets.ButtonStyle.PRIMARY, () -> upload(path));
				rowY += 20;
			}
		}
		Component hint = own != null && own.removalReason() != null && (own.texture() == null)
			? Component.translatable("pretupid.cosmetics.cape.removed", own.removalReason())
			: own != null && !own.uploadsAllowed() ? Component.translatable("pretupid.cosmetics.cape.uploads_disabled") : Component.translatable("pretupid.cosmetics.cape.hint");
		int hintColor = own != null && (!own.uploadsAllowed() || own.removalReason() != null && own.texture() == null) ? Theme.WARNING : Theme.TEXT_DIM;
		int lineY = rowY + 2;
		for (var line : ui.font().split(hint, areaW - 8)) {
			Draw.text(ui.g(), ui.font(), line, x, lineY, hintColor, false);
			lineY += 10;
		}
		scroll.end(ui, x, y, areaW, h);
	}

	private int drawMojangCapes(UiContext ui, MojangCapes mojang, int x, int y, int areaW, int columns) {
		Widgets.sectionHeader(ui, Component.translatable("pretupid.cosmetics.cape.minecraft"), x, y + 2, areaW - 6);
		int top = y + 14;
		if (mojang.state() != MojangCapes.State.READY) {
			String key = switch (mojang.state()) {
				case NOT_SIGNED_IN -> "pretupid.cosmetics.cape.minecraft.signed_out";
				case FAILED -> "pretupid.cosmetics.cape.minecraft.failed";
				default -> "pretupid.cosmetics.cape.minecraft.loading";
			};
			Draw.text(ui.g(), ui.font(), Draw.ellipsize(ui.font(), Component.translatable(key), areaW - 8), x, top + 2, Theme.TEXT_DIM);
			return top + 18;
		}
		List<MojangCapes.Cape> capes = mojang.capes();
		MojangCapes.Cape active = mojang.active();
		for (int i = 0; i <= capes.size(); i++) {
			MojangCapes.Cape cape = i == 0 ? null : capes.get(i - 1);
			int cx = x + (i % columns) * (CARD_WIDTH + 4);
			int cy = top + (i / columns) * (CARD_HEIGHT + 4);
			boolean selected = cape == null ? active == null : cape.active();
			Component label = cape == null ? Component.translatable("pretupid.cosmetics.cape.minecraft.hidden") : Component.literal(cape.alias());
			drawCapeCard(ui, "mojang." + i, cx, cy, label, mojang.texture(cape), cape != null, selected, () -> equipMojang(mojang, cape));
		}
		int rows = (capes.size() + 1 + columns - 1) / columns;
		return top + rows * (CARD_HEIGHT + 4) + 4;
	}

	private void equipMojang(MojangCapes mojang, MojangCapes.Cape cape) {
		if (mojang.busy()) {
			return;
		}
		PretupidClient client = PretupidClient.get();
		if (!client.settings().cape.get().isEmpty()) {
			client.settings().cape.set("");
			client.presence().requestHeartbeat();
		}
		boolean alreadyActive = cape == null ? mojang.active() == null : cape.active();
		if (alreadyActive) {
			return;
		}
		show(Component.translatable("pretupid.cosmetics.cape.minecraft.saving"), false);
		(cape == null ? mojang.hide() : mojang.equip(cape)).whenComplete((ignored, error) -> Minecraft.getInstance().execute(() ->
			show(error == null ? Component.translatable("pretupid.cosmetics.cape.minecraft.saved") : Component.literal(ApiClient.describe(error)), error != null)));
	}

	private void drawCapeCard(UiContext ui, String id, int x, int y, Component optionLabel, Identifier texture, boolean hasTexture, boolean selected, Runnable action) {
		var g = ui.g();
		boolean hover = ui.interactive(x, y, CARD_WIDTH, CARD_HEIGHT, UiContext.Handler.onClick(action));
		float t = ui.animate("cosmetics.card." + id, hover ? 1f : 0f, 16f);
		Draw.chamfer(g, x, y, CARD_WIDTH, CARD_HEIGHT, Colors.lerp(Theme.SURFACE, Theme.SURFACE_HOVER, t));
		Draw.border(g, x, y, CARD_WIDTH, CARD_HEIGHT, selected ? Theme.accent() : Theme.SURFACE_BORDER);
		int artW = 30;
		int artH = 48;
		int artX = x + (CARD_WIDTH - artW) / 2;
		int artY = y + 6;
		Draw.rect(g, artX - 1, artY - 1, artW + 2, artH + 2, 0x40000000);
		if (hasTexture) {
			if (texture != null) {
				g.blit(texture, artX, artY, artX + artW, artY + artH, 1f / 64f, 11f / 64f, 1f / 32f, 17f / 32f);
			} else {
				Draw.centered(g, ui.font(), Component.literal("…"), artX + artW / 2, artY + artH / 2 - 4, Theme.TEXT_DIM);
			}
		} else {
			Draw.centered(g, ui.font(), Component.literal("—"), artX + artW / 2, artY + artH / 2 - 4, Theme.TEXT_DIM);
		}
		Component label = Draw.ellipsize(ui.font(), optionLabel, CARD_WIDTH - 6);
		Draw.centered(g, ui.font(), label, x + CARD_WIDTH / 2, y + CARD_HEIGHT - 22, selected ? Theme.accentText() : Theme.TEXT);
		if (selected) {
			Draw.centered(g, ui.font(), Component.translatable("pretupid.cosmetics.equipped"), x + CARD_WIDTH / 2, y + CARD_HEIGHT - 11, Theme.TEXT_DIM);
		}
	}

	private int drawPlayerPreview(UiContext ui, int x, int y, int w, int h) {
		var g = ui.g();
		Minecraft minecraft = Minecraft.getInstance();
		Draw.chamfer(g, x, y, w, h, 0x30000000);
		Draw.border(g, x, y, w, h, Theme.SURFACE_BORDER);
		if (minecraft.player == null) {
			int lineY = y + h / 2 - 10;
			for (var line : ui.font().split(Component.translatable("pretupid.cosmetics.preview_in_world"), w - 8)) {
				g.text(ui.font(), line, x + 4, lineY, Theme.TEXT_DIM, false);
				lineY += 10;
			}
			return w;
		}
		int size = Math.min(w / 2, (h - 12) / 3);
		g.entity(minecraft.player, x + 2, y + 2, x + w - 2, y + h - 2, size, 0.0625f, ui.mouseX(), ui.mouseY());
		return w;
	}

	private void drawTrails(UiContext ui, int x, int y, int w, int h) {
		PretupidClient client = PretupidClient.get();
		ClientSettings settings = client.settings();
		List<Catalog.TrailEntry> trails = client.cosmetics().catalog().trails();
		int rows = trails.size() + 1;
		scroll.begin(ui, "cosmetics.trails", x, y, w, h, rows * 24 + 2 * Widgets.ROW_HEIGHT + 8);
		int rowY = y - scroll.offset();
		rowY = drawChoice(ui, "trail.none", x, rowY, w - 6, Component.translatable("pretupid.cosmetics.none"), Component.empty(), settings.trail.get().isEmpty(), () -> {
			settings.trail.set("");
			client.presence().requestHeartbeat();
		});
		for (Catalog.TrailEntry trail : trails) {
			rowY = drawChoice(ui, "trail." + trail.id(), x, rowY, w - 6, Component.literal(trail.name()), Component.literal(trail.description()),
				settings.trail.get().equals(trail.id()), () -> {
					settings.trail.set(trail.id());
					client.presence().requestHeartbeat();
				});
		}
		rowY += 8;
		Widgets.settingRow(ui, screen, settings.showTrails, x, rowY, w - 6);
		rowY += Widgets.ROW_HEIGHT;
		Widgets.settingRow(ui, screen, settings.showCosmetics, x, rowY, w - 6);
		scroll.end(ui, x, y, w, h);
	}

	private void drawWearables(UiContext ui, int x, int y, int w, int h) {
		ClientSettings settings = PretupidClient.get().settings();
		Catalog catalog = PretupidClient.get().cosmetics().catalog();
		List<Catalog.WearableEntry> hats = catalog.wearables(Catalog.WearableKind.HAT);
		List<Catalog.WearableEntry> backs = catalog.wearables(Catalog.WearableKind.BACK);
		List<Catalog.WearableEntry> pets = catalog.wearables(Catalog.WearableKind.PET);
		int rows = 3 + hats.size() + backs.size() + pets.size();
		scroll.begin(ui, "cosmetics.wearables", x, y, w, h, 3 * 16 + rows * 24 + Widgets.ROW_HEIGHT + 12);
		int rowY = y - scroll.offset();
		rowY = drawWearableGroup(ui, "hat", hats, settings.hat, x, rowY, w - 6);
		rowY = drawWearableGroup(ui, "back", backs, settings.back, x, rowY, w - 6);
		rowY = drawWearableGroup(ui, "pet", pets, settings.pet, x, rowY, w - 6);
		Widgets.settingRow(ui, screen, settings.showCosmetics, x, rowY + 4, w - 6);
		scroll.end(ui, x, y, w, h);
	}

	private int drawWearableGroup(UiContext ui, String kind, List<Catalog.WearableEntry> entries, net.pretupid.client.setting.TextSetting setting, int x, int y, int w) {
		Widgets.sectionHeader(ui, Component.translatable("pretupid.wearable." + kind), x, y + 2, w);
		int rowY = y + 16;
		String current = setting.get();
		rowY = drawChoice(ui, "wearable." + kind + ".none", x, rowY, w, Component.translatable("pretupid.cosmetics.none"), Component.empty(), current.isEmpty(), () -> {
			setting.set("");
			PretupidClient.get().presence().requestHeartbeat();
		});
		for (Catalog.WearableEntry entry : entries) {
			rowY = drawChoice(ui, "wearable." + kind + "." + entry.id(), x, rowY, w, Component.literal(entry.name()), Component.literal(entry.description()),
				current.equals(entry.id()), () -> {
					setting.set(entry.id());
					PretupidClient.get().presence().requestHeartbeat();
				});
		}
		return rowY;
	}

	private void drawWheelEditor(UiContext ui, int x, int y, int w) {
		PretupidClient client = PretupidClient.get();
		EmoteWheel wheel = client.emoteWheel();
		List<java.util.Optional<Catalog.EmoteEntry>> slots = wheel.slots(client.cosmetics().catalog());
		var g = ui.g();
		Widgets.sectionHeader(ui, Component.translatable("pretupid.cosmetics.emotes.wheel"), x, y, w);
		Component hint = Component.translatable(selectedWheelSlot >= 0 ? "pretupid.cosmetics.emotes.wheel_pick" : "pretupid.cosmetics.emotes.wheel_hint");
		Draw.text(g, ui.font(), Draw.ellipsize(ui.font(), hint, w - 4), x, y + 13, selectedWheelSlot >= 0 ? Theme.accentText() : Theme.TEXT_DIM);
		int gap = 4;
		int slotWidth = (w - gap * (EmoteWheel.SIZE - 1)) / EmoteWheel.SIZE;
		int slotY = y + 26;
		for (int i = 0; i < EmoteWheel.SIZE; i++) {
			int index = i;
			int slotX = x + i * (slotWidth + gap);
			java.util.Optional<Catalog.EmoteEntry> entry = slots.get(i);
			boolean picked = selectedWheelSlot == i;
			boolean hover = ui.interactive(slotX, slotY, slotWidth, WHEEL_SLOT_HEIGHT, new UiContext.Handler() {
				@Override
				public boolean click(double mouseX, double mouseY, int button) {
					if (button == 1) {
						wheel.clear(index);
						if (selectedWheelSlot == index) {
							selectedWheelSlot = -1;
						}
						return true;
					}
					if (button != 0) {
						return false;
					}
					if (selectedWheelSlot == index) {
						selectedWheelSlot = -1;
					} else if (selectedWheelSlot >= 0) {
						wheel.swap(selectedWheelSlot, index);
						selectedWheelSlot = -1;
					} else {
						selectedWheelSlot = index;
					}
					return true;
				}
			});
			float t = ui.animate("emote.wheel.slot." + i, hover ? 1f : 0f, 16f);
			float p = ui.animate("emote.wheel.pick." + i, picked ? 1f : 0f, 16f);
			int lift = Math.round(p * 2f);
			int fill = Colors.lerp(Colors.lerp(Theme.SURFACE, Theme.SURFACE_HOVER, t), Theme.accent(80), p);
			Draw.chamfer(g, slotX, slotY - lift, slotWidth, WHEEL_SLOT_HEIGHT, fill);
			Draw.border(g, slotX, slotY - lift, slotWidth, WHEEL_SLOT_HEIGHT, picked ? Theme.accent() : Colors.lerp(Theme.SURFACE_BORDER, 0x60FFFFFF, t));
			Draw.text(g, ui.font(), Component.literal(String.valueOf(i + 1)), slotX + 4, slotY - lift + 3, picked ? Theme.accentText() : Theme.TEXT_DIM);
			Component label = entry.<Component>map(e -> Component.literal(e.name())).orElse(Component.translatable("pretupid.emotes.empty_slot"));
			Draw.centered(g, ui.font(), Draw.ellipsize(ui.font(), label, slotWidth - 6), slotX + slotWidth / 2, slotY - lift + 14, entry.isPresent() ? Theme.TEXT : Theme.TEXT_DIM);
			if (hover && entry.isPresent()) {
				ui.tooltip(Component.translatable("pretupid.cosmetics.emotes.slot_tooltip"));
			}
		}
	}

	private void drawEmotes(UiContext ui, int x, int y, int w, int h) {
		PretupidClient client = PretupidClient.get();
		ClientSettings settings = client.settings();
		EmoteWheel wheel = client.emoteWheel();
		List<Catalog.EmoteEntry> emotes = client.cosmetics().catalog().emotes();
		boolean inWorld = Minecraft.getInstance().player != null;
		int wheelHeight = 26 + WHEEL_SLOT_HEIGHT + 10;
		scroll.begin(ui, "cosmetics.emotes", x, y, w, h, 24 + wheelHeight + emotes.size() * 24 + Widgets.ROW_HEIGHT * 5 + 8);
		int rowY = y - scroll.offset();
		Component key = client.keybinds().emoteWheel().getTranslatedKeyMessage();
		Component hint = inWorld
			? Component.translatable("pretupid.cosmetics.emotes.hint", key)
			: Component.translatable("pretupid.cosmetics.emotes.join_world");
		Draw.text(ui.g(), ui.font(), Draw.ellipsize(ui.font(), hint, w - 8), x, rowY + 4, Theme.TEXT_MUTED);
		rowY += 20;
		drawWheelEditor(ui, x, rowY, w - 6);
		rowY += wheelHeight;
		for (Catalog.EmoteEntry emote : emotes) {
			int slot = wheel.slotOf(emote.id());
			Draw.chamfer(ui.g(), x, rowY, w - 6, 22, Theme.SURFACE);
			if (slot >= 0) {
				Draw.rect(ui.g(), x + 1, rowY + 3, 2, 16, Theme.accent());
			}
			Draw.text(ui.g(), ui.font(), Component.literal(emote.name()), x + 6, rowY + 3, Theme.TEXT);
			int playX = x + w - 6 - 4 - EMOTE_PLAY_WIDTH;
			int keyX = (inWorld ? playX - 4 : x + w - 10) - EMOTE_KEY_WIDTH;
			int wheelX = keyX - 4 - EMOTE_WHEEL_WIDTH;
			Draw.text(ui.g(), ui.font(), Draw.ellipsize(ui.font(), Component.literal(emote.description()), wheelX - x - 12), x + 6, rowY + 12, Theme.TEXT_DIM);
			drawWheelButton(ui, wheel, emote, slot, wheelX, rowY + 3);
			Widgets.keyButton(ui, screen, "emote.key." + emote.id(), keyX, rowY + 4, EMOTE_KEY_WIDTH, client.emoteKeys().binding(emote.id()));
			if (inWorld) {
				Widgets.button(ui, "emote.play." + emote.id(), playX, rowY + 3, EMOTE_PLAY_WIDTH, 16, Component.translatable("pretupid.cosmetics.emotes.play"),
					Widgets.ButtonStyle.PRIMARY, () -> {
						if (Minecraft.getInstance().player != null) {
							screen.onClose();
							client.emotes().playLocal(emote);
						}
					});
			}
			rowY += 24;
		}
		rowY += 8;
		Widgets.settingRow(ui, screen, settings.emoteThirdPerson, x, rowY, w - 6);
		rowY += Widgets.ROW_HEIGHT;
		Widgets.settingRow(ui, screen, settings.emoteWheelHold, x, rowY, w - 6);
		for (Setting<?> setting : List.of(settings.emoteStopOnSneak, settings.emoteStopOnMove, settings.emoteStopOnAttack)) {
			rowY += Widgets.ROW_HEIGHT;
			Widgets.settingRow(ui, screen, setting, x, rowY, w - 6);
		}
		scroll.end(ui, x, y, w, h);
	}

	private void drawWheelButton(UiContext ui, EmoteWheel wheel, Catalog.EmoteEntry emote, int slot, int x, int y) {
		String id = "emote.wheel." + emote.id();
		boolean hover;
		Component tooltip;
		if (selectedWheelSlot >= 0) {
			int target = selectedWheelSlot;
			hover = Widgets.button(ui, id, x, y, EMOTE_WHEEL_WIDTH, 16, Component.translatable("pretupid.cosmetics.emotes.put", target + 1), Widgets.ButtonStyle.PRIMARY, () -> {
				wheel.assign(target, emote.id());
				selectedWheelSlot = -1;
			});
			tooltip = Component.translatable("pretupid.cosmetics.emotes.put_tooltip", target + 1);
		} else if (slot >= 0) {
			hover = Widgets.button(ui, id, x, y, EMOTE_WHEEL_WIDTH, 16, Component.translatable("pretupid.cosmetics.emotes.in_slot", slot + 1), Widgets.ButtonStyle.SECONDARY, () -> wheel.clear(slot));
			tooltip = Component.translatable("pretupid.cosmetics.emotes.in_slot_tooltip", slot + 1);
		} else {
			int free = wheel.firstFreeSlot();
			if (free >= 0) {
				hover = Widgets.button(ui, id, x, y, EMOTE_WHEEL_WIDTH, 16, Component.translatable("pretupid.cosmetics.emotes.add"), Widgets.ButtonStyle.GHOST, () -> wheel.assign(free, emote.id()));
				tooltip = Component.translatable("pretupid.cosmetics.emotes.add_tooltip");
			} else {
				hover = ui.interactive(x, y, EMOTE_WHEEL_WIDTH, 16, UiContext.Handler.onClick(() -> { }));
				Draw.centered(ui.g(), ui.font(), Component.translatable("pretupid.cosmetics.emotes.full"), x + EMOTE_WHEEL_WIDTH / 2, y + 4, Theme.TEXT_DIM);
				tooltip = Component.translatable("pretupid.cosmetics.emotes.full_tooltip");
			}
		}
		if (hover) {
			ui.tooltip(tooltip);
		}
	}

	private int drawChoice(UiContext ui, String id, int x, int y, int w, Component title, Component subtitle, boolean selected, Runnable action) {
		var g = ui.g();
		boolean hover = ui.interactive(x, y, w, 22, UiContext.Handler.onClick(action));
		float t = ui.animate("cosmetics.choice." + id, hover ? 1f : 0f, 16f);
		Draw.chamfer(g, x, y, w, 22, Colors.lerp(Theme.SURFACE, Theme.SURFACE_HOVER, t));
		if (selected) {
			Draw.border(g, x, y, w, 22, Theme.accent());
		}
		Draw.text(g, ui.font(), title, x + 6, y + (subtitle.getString().isEmpty() ? 7 : 3), selected ? Theme.accentText() : Theme.TEXT);
		if (!subtitle.getString().isEmpty()) {
			Draw.text(g, ui.font(), Draw.ellipsize(ui.font(), subtitle, w - 12), x + 6, y + 12, Theme.TEXT_DIM);
		}
		return y + 24;
	}

	private record CapeOption(String value, Component label, RemoteProfile.CapeRef texture) {
	}
}

package net.pretupid.client.ui.screen.page;

import net.pretupid.client.platform.Gfx;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.skins.AccountSkin;
import net.pretupid.client.skins.SkinLibrary;
import net.pretupid.client.skins.SkinUploader;
import net.pretupid.client.ui.Draw;
import net.pretupid.client.ui.Icon;
import net.pretupid.client.ui.ScrollState;
import net.pretupid.client.ui.Theme;
import net.pretupid.client.ui.UiContext;
import net.pretupid.client.ui.UiIcon;
import net.pretupid.client.ui.Widgets;
import net.pretupid.client.ui.screen.MenuPage;
import net.pretupid.client.util.Colors;
import net.pretupid.client.util.PlatformOpener;

public final class SkinsPage implements MenuPage {
	private static final int CARD_WIDTH = 90;
	private static final int CARD_HEIGHT = 124;
	private static final int GAP = 6;
	private static final int PREVIEW_SCALE = 2;
	private static final int BUTTON_HEIGHT = 15;
	private static final long POLL_INTERVAL_MS = 1000L;
	private static final Icon NOTIFICATION_ICON = Icon.item("leather_chestplate");

	private final SkinLibrary library;
	private final AccountSkin account;
	private final SkinUploader uploader = new SkinUploader();
	private final ScrollState scroll = new ScrollState();
	private String uploading;
	private long lastPoll;

	public SkinsPage(SkinLibrary library) {
		this.library = library;
		this.account = PretupidClient.get().accountSkin();
	}

	@Override
	public void onOpen() {
		library.refresh();
		account.refresh();
		lastPoll = System.currentTimeMillis();
	}

	@Override
	public boolean onFilesDropped(List<Path> paths) {
		int imported = 0;
		int rejected = 0;
		for (Path path : paths) {
			switch (library.importFile(path)) {
				case IMPORTED -> imported++;
				default -> rejected++;
			}
		}
		library.refresh();
		var notifications = PretupidClient.get().notifications();
		if (imported > 0) {
			notifications.info(Component.translatable("pretupid.skins.title"), Component.translatable("pretupid.skins.imported", imported), NOTIFICATION_ICON);
		}
		if (rejected > 0) {
			notifications.push(Component.translatable("pretupid.skins.title"), Component.translatable("pretupid.skins.rejected", rejected), NOTIFICATION_ICON, null, Theme.WARNING, 4200L);
		}
		return true;
	}

	private void poll() {
		long now = System.currentTimeMillis();
		if (now - lastPoll >= POLL_INTERVAL_MS) {
			lastPoll = now;
			library.refreshIfChanged();
		}
	}

	private Optional<SkinLibrary.Skin> wornSkin() {
		AccountSkin.Current current = account.current();
		if (current != null) {
			return library.findByHash(current.contentHash());
		}
		String active = library.activeSkin();
		return library.skins().stream().filter(skin -> skin.name().equals(active)).findFirst();
	}

	@Override
	public void draw(UiContext ui, int x, int y, int w, int h) {
		poll();
		var g = ui.g();
		var font = ui.font();
		int buttonsWidth = 86 + 4 + 62;
		Draw.textShadow(g, font, Component.translatable("pretupid.skins.title"), x, y + 1, Theme.TEXT);
		Draw.text(g, font, Draw.ellipsize(font, Component.translatable("pretupid.skins.hint"), w - buttonsWidth - 8), x, y + 12, Theme.TEXT_MUTED);
		Widgets.button(ui, "skins.refresh", x + w - buttonsWidth, y + 2, 62, 18, Component.translatable("pretupid.skins.refresh"), Widgets.ButtonStyle.SECONDARY, () -> {
			library.refresh();
			account.refresh();
		});
		Widgets.button(ui, "skins.folder", x + w - 86, y + 2, 86, 18, Component.translatable("pretupid.mods.open_folder"), Widgets.ButtonStyle.SECONDARY,
			() -> PlatformOpener.openFolder(library.directory()));

		int listY = y + 28;
		int listH = h - 28;
		List<SkinLibrary.Skin> skins = library.skins();
		Optional<SkinLibrary.Skin> worn = wornSkin();
		AccountSkin.Current current = account.current();
		boolean showCurrent = current != null && worn.isEmpty();
		int count = skins.size() + (showCurrent ? 1 : 0);
		int columns = Math.max(1, (w + GAP) / (CARD_WIDTH + GAP));
		int rows = (count + columns - 1) / columns;
		scroll.begin(ui, "skins", x, listY, w, listH, rows * (CARD_HEIGHT + GAP));
		int index = 0;
		if (showCurrent) {
			int cardY = listY - scroll.offset();
			if (cardY + CARD_HEIGHT >= listY && cardY <= listY + listH) {
				drawCurrentCard(ui, current, x, cardY);
			}
			index++;
		}
		for (SkinLibrary.Skin skin : skins) {
			int cardX = x + (index % columns) * (CARD_WIDTH + GAP);
			int cardY = listY + (index / columns) * (CARD_HEIGHT + GAP) - scroll.offset();
			if (cardY + CARD_HEIGHT >= listY && cardY <= listY + listH) {
				drawCard(ui, skin, worn.map(w2 -> w2.name().equals(skin.name())).orElse(false), cardX, cardY);
			}
			index++;
		}
		if (count == 0) {
			Draw.centered(g, font, Component.translatable("pretupid.skins.empty"), x + w / 2, listY + 36, Theme.TEXT_DIM);
		}
		scroll.end(ui, x, listY, w, listH);
	}

	private void drawFrame(UiContext ui, String id, int x, int y, boolean highlighted) {
		var g = ui.g();
		boolean hover = ui.hovered(x, y, CARD_WIDTH, CARD_HEIGHT);
		float t = ui.animate(id + ".hover", hover ? 1f : 0f, 16f);
		int fill = Colors.lerp(Theme.SURFACE, Theme.SURFACE_HOVER, t);
		Draw.chamfer(g, x, y, CARD_WIDTH, CARD_HEIGHT, highlighted ? Colors.lerp(fill, Theme.accent(60), 0.6f) : fill);
		Draw.border(g, x, y, CARD_WIDTH, CARD_HEIGHT, highlighted ? Theme.accent(170) : Theme.SURFACE_BORDER);
	}

	private void drawCurrentCard(UiContext ui, AccountSkin.Current current, int x, int y) {
		var g = ui.g();
		var font = ui.font();
		drawFrame(ui, "skin.current", x, y, true);
		drawFigure(g, current.texture(), 64, current.variant(), x + (CARD_WIDTH - 16 * PREVIEW_SCALE) / 2, y + 16);
		drawVariantTag(ui, current.variant(), x + 4, y + 4, null);
		UiIcon.CHECK.draw(g, x + CARD_WIDTH - UiIcon.SIZE - 4, y + 4, Theme.accentText());
		Component name = Draw.ellipsize(font, Component.translatable("pretupid.skins.current"), CARD_WIDTH - 8);
		Draw.centered(g, font, name, x + CARD_WIDTH / 2, y + CARD_HEIGHT - BUTTON_HEIGHT - 16, Theme.TEXT);
		Widgets.button(ui, "skin.current.save", x + 4, y + CARD_HEIGHT - BUTTON_HEIGHT - 4, CARD_WIDTH - 8, BUTTON_HEIGHT,
			Component.translatable("pretupid.skins.save_current"), Widgets.ButtonStyle.SECONDARY, () -> saveCurrent(current));
	}

	private void drawCard(UiContext ui, SkinLibrary.Skin skin, boolean worn, int x, int y) {
		var g = ui.g();
		var font = ui.font();
		String id = "skin." + skin.name();
		drawFrame(ui, id, x, y, worn);
		boolean hover = ui.hovered(x, y, CARD_WIDTH, CARD_HEIGHT);
		float t = ui.animate(id + ".lift", hover ? 1f : 0f, 16f);
		drawFigure(g, skin.texture(), skin.textureHeight(), skin.variant(), x + (CARD_WIDTH - 16 * PREVIEW_SCALE) / 2, y + 16 - Math.round(t * 2f));
		drawVariantTag(ui, skin.variant(), x + 4, y + 4, () ->
			library.setVariant(skin, skin.variant() == SkinLibrary.Variant.SLIM ? SkinLibrary.Variant.CLASSIC : SkinLibrary.Variant.SLIM));
		if (hover && !worn) {
			int deleteX = x + CARD_WIDTH - UiIcon.SIZE - 4;
			boolean deleteHover = ui.interactive(deleteX - 1, y + 3, UiIcon.SIZE + 2, UiIcon.SIZE + 2, UiContext.Handler.onClick(() -> library.delete(skin)));
			UiIcon.CLOSE.draw(g, deleteX, y + 4, deleteHover ? Theme.DANGER : Theme.TEXT_DIM);
		} else if (worn) {
			UiIcon.CHECK.draw(g, x + CARD_WIDTH - UiIcon.SIZE - 4, y + 4, Theme.accentText());
		}
		Component name = Draw.ellipsize(font, Component.literal(skin.name()), CARD_WIDTH - 8);
		Draw.centered(g, font, name, x + CARD_WIDTH / 2, y + CARD_HEIGHT - BUTTON_HEIGHT - 16, Theme.TEXT);
		boolean busy = skin.name().equals(uploading);
		Component label = Component.translatable(worn ? "pretupid.skins.active" : busy ? "pretupid.skins.applying" : "pretupid.skins.apply");
		Widgets.button(ui, id + ".apply", x + 4, y + CARD_HEIGHT - BUTTON_HEIGHT - 4, CARD_WIDTH - 8, BUTTON_HEIGHT, label,
			worn ? Widgets.ButtonStyle.GHOST : Widgets.ButtonStyle.PRIMARY, () -> {
				if (!worn) {
					apply(skin);
				}
			});
	}

	private void drawVariantTag(UiContext ui, SkinLibrary.Variant variant, int x, int y, Runnable toggle) {
		var g = ui.g();
		var font = ui.font();
		Component label = Component.translatable(variant == SkinLibrary.Variant.SLIM ? "pretupid.skins.slim" : "pretupid.skins.classic");
		int width = font.width(label) + 8;
		boolean hover = false;
		if (toggle != null) {
			hover = ui.interactive(x, y, width, 12, UiContext.Handler.onClick(toggle));
			if (hover) {
				ui.tooltip(Component.translatable("pretupid.skins.toggle_variant"));
			}
		}
		Draw.chamfer(g, x, y, width, 12, hover ? Theme.SURFACE_ACTIVE : Colors.withAlpha(0, 110));
		Draw.text(g, font, label, x + 4, y + 2, hover ? Theme.TEXT : Theme.TEXT_MUTED);
	}

	private static void drawFigure(Gfx g, Identifier texture, int textureHeight, SkinLibrary.Variant variant, int x, int y) {
		int s = PREVIEW_SCALE;
		boolean modern = textureHeight == 64;
		int arm = variant == SkinLibrary.Variant.SLIM ? 3 : 4;
		Draw.rect(g, x - 2, y + 32 * s - 1, 16 * s + 4, 2, 0x30000000);
		part(g, texture, textureHeight, x + 4 * s, y, 8, 8, 8, 8, s);
		part(g, texture, textureHeight, x + 4 * s, y + 8 * s, 20, 20, 8, 12, s);
		part(g, texture, textureHeight, x + (4 - arm) * s, y + 8 * s, 44, 20, arm, 12, s);
		part(g, texture, textureHeight, x + 12 * s, y + 8 * s, modern ? 36 : 44, modern ? 52 : 20, arm, 12, s);
		part(g, texture, textureHeight, x + 4 * s, y + 20 * s, 4, 20, 4, 12, s);
		part(g, texture, textureHeight, x + 8 * s, y + 20 * s, modern ? 20 : 4, modern ? 52 : 20, 4, 12, s);
		part(g, texture, textureHeight, x + 4 * s, y, 40, 8, 8, 8, s);
		if (modern) {
			part(g, texture, textureHeight, x + 4 * s, y + 8 * s, 20, 36, 8, 12, s);
			part(g, texture, textureHeight, x + (4 - arm) * s, y + 8 * s, 44, 36, arm, 12, s);
			part(g, texture, textureHeight, x + 12 * s, y + 8 * s, 52, 52, arm, 12, s);
			part(g, texture, textureHeight, x + 4 * s, y + 20 * s, 4, 36, 4, 12, s);
			part(g, texture, textureHeight, x + 8 * s, y + 20 * s, 4, 52, 4, 12, s);
		}
	}

	private static void part(Gfx g, Identifier texture, int textureHeight, int x, int y, int u, int v, int w, int h, int scale) {
		g.blit(texture, x, y, u, v, w * scale, h * scale, w, h, 64, textureHeight);
	}

	private void saveCurrent(AccountSkin.Current current) {
		String base = Minecraft.getInstance().getUser().getName();
		if (library.importBytes(base == null || base.isBlank() ? "current" : base, current.png(), current.variant()) == SkinLibrary.ImportResult.IMPORTED) {
			library.refresh();
			PretupidClient.get().notifications().info(Component.translatable("pretupid.skins.title"), Component.translatable("pretupid.skins.saved_current"), NOTIFICATION_ICON);
		}
	}

	private void apply(SkinLibrary.Skin skin) {
		if (uploading != null) {
			return;
		}
		uploading = skin.name();
		uploader.upload(skin).thenAccept(outcome -> Minecraft.getInstance().execute(() -> {
			uploading = null;
			PretupidClient client = PretupidClient.get();
			var notifications = client.notifications();
			Component title = Component.translatable("pretupid.skins.title");
			switch (outcome) {
				case APPLIED -> {
					library.markActive(skin);
					client.localSkin().set(skin.file(), skin.variant());
					account.refresh();
					notifications.push(title, Component.translatable("pretupid.skins.applied"), NOTIFICATION_ICON, null, Theme.SUCCESS, 4200L);
				}
				case NOT_SIGNED_IN -> notifications.push(title, Component.translatable("pretupid.skins.not_signed_in"), NOTIFICATION_ICON, null, Theme.DANGER, 4200L);
				case RATE_LIMITED -> notifications.push(title, Component.translatable("pretupid.skins.rate_limited"), NOTIFICATION_ICON, null, Theme.WARNING, 4200L);
				case REJECTED -> notifications.push(title, Component.translatable("pretupid.skins.rejected_upload"), NOTIFICATION_ICON, null, Theme.DANGER, 4200L);
				case NETWORK -> notifications.push(title, Component.translatable("pretupid.skins.network"), NOTIFICATION_ICON, null, Theme.DANGER, 4200L);
			}
		}));
	}

	@Override
	public void release() {
		library.release();
	}
}

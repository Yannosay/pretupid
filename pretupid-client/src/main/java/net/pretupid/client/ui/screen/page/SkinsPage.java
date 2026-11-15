package net.pretupid.client.ui.screen.page;

import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.pretupid.client.PretupidClient;
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
	private static final int CARD_WIDTH = 78;
	private static final int CARD_HEIGHT = 106;
	private static final int GAP = 6;
	private static final int PREVIEW_SCALE = 2;
	private static final Icon NOTIFICATION_ICON = Icon.item("leather_chestplate");

	private final SkinLibrary library;
	private final SkinUploader uploader = new SkinUploader();
	private final ScrollState scroll = new ScrollState();
	private String uploading;

	public SkinsPage(SkinLibrary library) {
		this.library = library;
	}

	@Override
	public void onOpen() {
		library.refresh();
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

	@Override
	public void draw(UiContext ui, int x, int y, int w, int h) {
		var g = ui.g();
		var font = ui.font();
		Draw.textShadow(g, font, Component.translatable("pretupid.skins.title"), x, y + 1, Theme.TEXT);
		Draw.text(g, font, Draw.ellipsize(font, Component.translatable("pretupid.skins.hint"), w - 120), x, y + 12, Theme.TEXT_MUTED);
		Widgets.button(ui, "skins.folder", x + w - 86, y + 2, 86, 18, Component.translatable("pretupid.mods.open_folder"), Widgets.ButtonStyle.SECONDARY,
			() -> PlatformOpener.openFolder(library.directory()));
		int listY = y + 28;
		int listH = h - 28;
		List<SkinLibrary.Skin> skins = library.skins();
		int columns = Math.max(1, (w + GAP) / (CARD_WIDTH + GAP));
		int rows = (skins.size() + columns - 1) / columns;
		scroll.begin(ui, "skins", x, listY, w, listH, rows * (CARD_HEIGHT + GAP));
		for (int i = 0; i < skins.size(); i++) {
			int cardX = x + (i % columns) * (CARD_WIDTH + GAP);
			int cardY = listY + (i / columns) * (CARD_HEIGHT + GAP) - scroll.offset();
			if (cardY + CARD_HEIGHT >= listY && cardY <= listY + listH) {
				drawCard(ui, skins.get(i), cardX, cardY);
			}
		}
		if (skins.isEmpty()) {
			Draw.centered(g, font, Component.translatable("pretupid.skins.empty"), x + w / 2, listY + 36, Theme.TEXT_DIM);
		}
		scroll.end(ui, x, listY, w, listH);
	}

	private void drawCard(UiContext ui, SkinLibrary.Skin skin, int x, int y) {
		var g = ui.g();
		var font = ui.font();
		String id = "skin." + skin.name();
		boolean active = skin.name().equals(library.activeSkin());
		boolean hover = ui.hovered(x, y, CARD_WIDTH, CARD_HEIGHT);
		float t = ui.animate(id + ".hover", hover ? 1f : 0f, 16f);
		int fill = Colors.lerp(Theme.SURFACE, Theme.SURFACE_HOVER, t);
		Draw.chamfer(g, x, y, CARD_WIDTH, CARD_HEIGHT, active ? Colors.lerp(fill, Theme.accent(60), 0.6f) : fill);
		Draw.border(g, x, y, CARD_WIDTH, CARD_HEIGHT, active ? Theme.accent(170) : Theme.SURFACE_BORDER);
		int previewX = x + (CARD_WIDTH - 16 * PREVIEW_SCALE) / 2;
		int previewY = y + 6 - Math.round(t * 2f);
		drawFigure(g, skin, previewX, previewY);
		if (active) {
			UiIcon.CHECK.draw(g, x + 4, y + 4, Theme.accentText());
		}
		if (hover) {
			boolean deleteHover = ui.interactive(x + CARD_WIDTH - 13, y + 3, 10, 10, UiContext.Handler.onClick(() -> library.delete(skin)));
			UiIcon.CLOSE.draw(g, x + CARD_WIDTH - 13, y + 3, deleteHover ? Theme.DANGER : Theme.TEXT_DIM);
		}
		Component name = Draw.ellipsize(font, Component.literal(skin.name()), CARD_WIDTH - 8);
		Draw.centered(g, font, name, x + CARD_WIDTH / 2, y + 74, Theme.TEXT);
		Component variant = Component.translatable(skin.variant() == SkinLibrary.Variant.SLIM ? "pretupid.skins.slim" : "pretupid.skins.classic");
		int chipWidth = font.width(variant) + 8;
		int chipX = x + 4;
		int chipY = y + CARD_HEIGHT - 16;
		boolean chipHover = ui.interactive(chipX, chipY, chipWidth, 12, UiContext.Handler.onClick(() ->
			library.setVariant(skin, skin.variant() == SkinLibrary.Variant.SLIM ? SkinLibrary.Variant.CLASSIC : SkinLibrary.Variant.SLIM)));
		Draw.chamfer(g, chipX, chipY, chipWidth, 12, chipHover ? Theme.SURFACE_ACTIVE : Theme.SURFACE);
		Draw.text(g, font, variant, chipX + 4, chipY + 2, Theme.TEXT_MUTED);
		int applyWidth = CARD_WIDTH - chipWidth - 12;
		boolean busy = skin.name().equals(uploading);
		Component label = Component.translatable(busy ? "pretupid.skins.applying" : "pretupid.skins.apply");
		Widgets.button(ui, id + ".apply", chipX + chipWidth + 4, chipY, applyWidth, 12, label, Widgets.ButtonStyle.PRIMARY, () -> apply(skin));
	}

	private void drawFigure(GuiGraphicsExtractor g, SkinLibrary.Skin skin, int x, int y) {
		int s = PREVIEW_SCALE;
		boolean modern = skin.textureHeight() == 64;
		int arm = skin.variant() == SkinLibrary.Variant.SLIM ? 3 : 4;
		Draw.rect(g, x - 2, y + 32 * s - 1, 16 * s + 4, 2, 0x30000000);
		part(g, skin, x + 4 * s, y, 8, 8, 8, 8, s);
		part(g, skin, x + 4 * s, y + 8 * s, 20, 20, 8, 12, s);
		part(g, skin, x + (4 - arm) * s, y + 8 * s, 44, 20, arm, 12, s);
		part(g, skin, x + 12 * s, y + 8 * s, modern ? 36 : 44, modern ? 52 : 20, arm, 12, s);
		part(g, skin, x + 4 * s, y + 20 * s, 4, 20, 4, 12, s);
		part(g, skin, x + 8 * s, y + 20 * s, modern ? 20 : 4, modern ? 52 : 20, 4, 12, s);
		part(g, skin, x + 4 * s, y, 40, 8, 8, 8, s);
		if (modern) {
			part(g, skin, x + 4 * s, y + 8 * s, 20, 36, 8, 12, s);
			part(g, skin, x + (4 - arm) * s, y + 8 * s, 44, 36, arm, 12, s);
			part(g, skin, x + 12 * s, y + 8 * s, 52, 52, arm, 12, s);
			part(g, skin, x + 4 * s, y + 20 * s, 4, 36, 4, 12, s);
			part(g, skin, x + 8 * s, y + 20 * s, 4, 52, 4, 12, s);
		}
	}

	private static void part(GuiGraphicsExtractor g, SkinLibrary.Skin skin, int x, int y, int u, int v, int w, int h, int scale) {
		g.blit(RenderPipelines.GUI_TEXTURED, skin.texture(), x, y, u, v, w * scale, h * scale, w, h, 64, skin.textureHeight());
	}

	private void apply(SkinLibrary.Skin skin) {
		if (uploading != null) {
			return;
		}
		uploading = skin.name();
		uploader.upload(skin).thenAccept(outcome -> Minecraft.getInstance().execute(() -> {
			uploading = null;
			var notifications = PretupidClient.get().notifications();
			Component title = Component.translatable("pretupid.skins.title");
			switch (outcome) {
				case APPLIED -> {
					library.markActive(skin);
					notifications.push(title, Component.translatable("pretupid.skins.applied"), NOTIFICATION_ICON, null, Theme.SUCCESS, 4200L);
				}
				case NOT_SIGNED_IN -> notifications.push(title, Component.translatable("pretupid.skins.not_signed_in"), NOTIFICATION_ICON, null, Theme.DANGER, 4200L);
				case RATE_LIMITED -> notifications.push(title, Component.translatable("pretupid.skins.rate_limited"), NOTIFICATION_ICON, null, Theme.WARNING, 4200L);
				case REJECTED -> notifications.push(title, Component.translatable("pretupid.skins.rejected_upload"), NOTIFICATION_ICON, null, Theme.DANGER, 4200L);
				case NETWORK -> notifications.push(title, Component.translatable("pretupid.skins.network"), NOTIFICATION_ICON, null, Theme.DANGER, 4200L);
			}
		}));
	}

	public void release() {
		library.release();
	}
}

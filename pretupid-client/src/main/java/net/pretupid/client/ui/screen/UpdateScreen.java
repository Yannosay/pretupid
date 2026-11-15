package net.pretupid.client.ui.screen;

import java.net.URI;
import java.util.List;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.PretupidInfo;
import net.pretupid.client.news.MarkdownText;
import net.pretupid.client.ui.Draw;
import net.pretupid.client.ui.PretupidScreen;
import net.pretupid.client.ui.ScrollState;
import net.pretupid.client.ui.Theme;
import net.pretupid.client.ui.UiContext;
import net.pretupid.client.ui.UiIcon;
import net.pretupid.client.ui.Widgets;
import net.pretupid.client.update.UpdateService;

public final class UpdateScreen extends PretupidScreen {
	private final ScrollState scroll = new ScrollState();
	private List<MarkdownText.Line> notes;
	private int notesWidth;

	public UpdateScreen(Screen parent) {
		super(Component.translatable("pretupid.update.title"), parent);
	}

	@Override
	protected void drawContent(UiContext ui) {
		var g = ui.g();
		UpdateService updates = PretupidClient.get().updates();
		UpdateService.Release release = updates.release();
		int w = Math.min(320, width - 16);
		int h = Math.min(220, height - 16);
		int x = (width - w) / 2;
		int y = (height - h) / 2;
		Draw.shadow(g, x, y, w, h, 6, 1f);
		Draw.glass(g, x, y, w, h, Theme.WINDOW, Theme.WINDOW_BORDER);
		UiIcon.drawLogo(g, x + 10, y + 10, 20);
		Draw.textShadow(g, font, Component.translatable("pretupid.update.title"), x + 36, y + 11, Theme.TEXT);
		Component versions = release == null
			? Component.translatable("pretupid.update.none")
			: Component.translatable("pretupid.update.versions", PretupidInfo.shortVersion(), release.version());
		Draw.text(g, font, Draw.ellipsize(font, versions, w - 46), x + 36, y + 22, Theme.TEXT_MUTED);
		int bodyY = y + 40;
		int bodyH = h - 40 - 34;
		Draw.chamfer(g, x + 8, bodyY, w - 16, bodyH, 0x24000000);
		if (release != null) {
			if (notes == null || notesWidth != w - 30) {
				notesWidth = w - 30;
				String text = release.notes().isBlank() ? Component.translatable("pretupid.update.no_notes").getString() : release.notes();
				notes = MarkdownText.layout(text, font, notesWidth, Theme.TEXT, Theme.accentText(), Theme.TEXT_MUTED);
			}
			int contentHeight = 8;
			for (MarkdownText.Line line : notes) {
				contentHeight += line.spacingBefore() + 10;
			}
			scroll.begin(ui, "update.notes", x + 8, bodyY + 2, w - 16, bodyH - 4, contentHeight);
			int lineY = bodyY + 6 - scroll.offset();
			for (MarkdownText.Line line : notes) {
				lineY += line.spacingBefore();
				if (!line.rule()) {
					g.text(font, line.text(), x + 14 + line.indent(), lineY, line.color(), false);
				}
				lineY += 10;
			}
			scroll.end(ui, x + 8, bodyY + 2, w - 16, bodyH - 4);
		}
		int buttonY = y + h - 26;
		int buttonW = (w - 16 - 8) / 3;
		UpdateService.State state = updates.state();
		Component status = switch (state) {
			case DOWNLOADING -> Component.translatable("pretupid.update.downloading");
			case READY -> Component.translatable("pretupid.update.ready");
			case FAILED -> Component.translatable("pretupid.update.failed", updates.error() == null ? "?" : updates.error());
			default -> null;
		};
		if (state == UpdateService.State.READY) {
			Draw.text(g, font, Draw.ellipsize(font, status, w - 16 - buttonW - 8), x + 8, buttonY + 5, Theme.SUCCESS);
			Widgets.button(ui, "update.quit", x + w - 8 - buttonW, buttonY, buttonW, 18, Component.translatable("pretupid.update.quit"), Widgets.ButtonStyle.PRIMARY, () -> minecraft.stop());
			return;
		}
		if (status != null) {
			Draw.text(g, font, Draw.ellipsize(font, status, w - 16), x + 8, buttonY - 11, state == UpdateService.State.FAILED ? Theme.DANGER : Theme.TEXT_MUTED);
		}
		boolean canInstall = release != null && state != UpdateService.State.DOWNLOADING && updates.unsupportedReason().isEmpty();
		Widgets.button(ui, "update.install", x + 8, buttonY, buttonW, 18, Component.translatable("pretupid.update.install"),
			canInstall ? Widgets.ButtonStyle.PRIMARY : Widgets.ButtonStyle.GHOST, () -> {
				if (canInstall) {
					updates.install();
				}
			});
		Widgets.button(ui, "update.page", x + 12 + buttonW, buttonY, buttonW, 18, Component.translatable("pretupid.update.details"), Widgets.ButtonStyle.SECONDARY, () -> {
			if (release != null && release.page() != null && MarkdownText.safeUrl(release.page()) != null) {
				ConfirmLinkScreen.confirmLinkNow(this, URI.create(release.page()), false);
			}
		});
		Widgets.button(ui, "update.skip", x + 16 + buttonW * 2, buttonY, buttonW, 18, Component.translatable("pretupid.update.skip"), Widgets.ButtonStyle.GHOST, () -> {
			updates.skip();
			onClose();
		});
		if (updates.unsupportedReason().isPresent()) {
			Draw.text(g, font, Draw.ellipsize(font, Component.translatable(updates.unsupportedReason().get()), w - 16), x + 8, buttonY - 11, Theme.WARNING);
		}
	}
}

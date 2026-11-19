package net.pretupid.client.ui.screen.page;

import net.pretupid.client.platform.Platform;
import java.net.URI;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.news.MarkdownText;
import net.pretupid.client.news.NewsImages;
import net.minecraft.client.renderer.RenderPipelines;
import net.pretupid.client.news.NewsService;
import net.pretupid.client.ui.Draw;
import net.pretupid.client.ui.ScrollState;
import net.pretupid.client.ui.Theme;
import net.pretupid.client.ui.UiContext;
import net.pretupid.client.ui.Widgets;
import net.pretupid.client.ui.screen.ClientMenuScreen;
import net.pretupid.client.ui.screen.MenuPage;
import net.pretupid.client.util.Colors;

public final class NewsPage implements MenuPage {
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH).withZone(ZoneId.systemDefault());
	private static final int LIST_WIDTH = 118;

	private final ClientMenuScreen screen;
	private final ScrollState listScroll = new ScrollState();
	private final ScrollState bodyScroll = new ScrollState();
	private String selectedId;
	private String layoutKey;
	private List<MarkdownText.Line> layout = List.of();
	private final NewsImages images = new NewsImages();
	private static final int BUTTON_HEIGHT = 18;
	private static final int MAX_IMAGE_HEIGHT = 140;
	private static final int IMAGE_PLACEHOLDER_HEIGHT = 40;

	public NewsPage(ClientMenuScreen screen) {
		this.screen = screen;
	}

	@Override
	public void onOpen() {
		NewsService news = PretupidClient.get().news();
		if (!news.loaded() || news.posts().isEmpty()) {
			news.refresh();
		}
	}

	@Override
	public void draw(UiContext ui, int x, int y, int w, int h) {
		NewsService news = PretupidClient.get().news();
		var g = ui.g();
		var font = ui.font();
		List<NewsService.Post> posts = news.posts();
		if (posts.isEmpty()) {
			Component text = news.loading() || !news.loaded() && news.error() == null
				? Component.translatable("pretupid.news.loading")
				: news.error() != null ? Component.translatable("pretupid.news.error") : Component.translatable("pretupid.news.empty");
			Draw.centered(g, font, text, x + w / 2, y + h / 2 - 10, Theme.TEXT_MUTED);
			if (news.error() != null && !news.loading()) {
				Widgets.button(ui, "news.retry", x + w / 2 - 40, y + h / 2 + 4, 80, 18, Component.translatable("pretupid.ui.retry"), Widgets.ButtonStyle.SECONDARY, news::refresh);
			}
			return;
		}
		NewsService.Post selected = posts.stream().filter(post -> post.id().equals(selectedId)).findFirst().orElse(posts.getFirst());
		if (!selected.id().equals(selectedId)) {
			selectedId = selected.id();
			bodyScroll.reset();
		}
		drawList(ui, news, posts, selected, x, y, LIST_WIDTH, h);
		drawBody(ui, selected, x + LIST_WIDTH + 8, y, w - LIST_WIDTH - 8, h);
	}

	private void drawList(UiContext ui, NewsService news, List<NewsService.Post> posts, NewsService.Post selected, int x, int y, int w, int h) {
		var g = ui.g();
		var font = ui.font();
		int rowHeight = 30;
		listScroll.begin(ui, "news.list", x, y, w, h, posts.size() * (rowHeight + 2));
		int rowY = y - listScroll.offset();
		for (NewsService.Post post : posts) {
			boolean active = post == selected;
			boolean hover = ui.interactive(x, rowY, w - 6, rowHeight, UiContext.Handler.onClick(() -> {
				selectedId = post.id();
				bodyScroll.reset();
			}));
			float t = ui.animate("news.row." + post.id(), hover ? 1f : 0f, 16f);
			Draw.chamfer(g, x, rowY, w - 6, rowHeight, active ? Theme.accent(60) : Colors.lerp(Theme.SURFACE, Theme.SURFACE_HOVER, t));
			if (news.isUnread(post)) {
				Draw.chamfer(g, x + w - 14, rowY + 4, 4, 4, Theme.accent());
			}
			Draw.text(g, font, Draw.ellipsize(font, Component.literal(post.title()), w - 20), x + 5, rowY + 5, Theme.TEXT);
			String meta = (post.pinned() ? "★ " : "") + post.category().toUpperCase(Locale.ROOT) + " · " + date(post.publishedAt());
			Draw.text(g, font, Draw.ellipsize(font, Component.literal(meta), w - 14), x + 5, rowY + 17, Theme.TEXT_DIM);
			rowY += rowHeight + 2;
		}
		listScroll.end(ui, x, y, w, h);
	}

	private void drawBody(UiContext ui, NewsService.Post post, int x, int y, int w, int h) {
		var g = ui.g();
		var font = ui.font();
		String key = post.id() + ":" + w + ":" + post.body().hashCode();
		if (!key.equals(layoutKey)) {
			layoutKey = key;
			layout = MarkdownText.layout(post.body(), font, w - 14, Theme.TEXT, Theme.accentText(), Theme.TEXT_MUTED);
		}
		Draw.chamfer(g, x, y, w, h, 0x24000000);
		int headerHeight = 0;
		List<net.minecraft.util.FormattedCharSequence> title = font.split(Component.literal(post.title()).withStyle(style -> style.withBold(true)), w - 14);
		headerHeight += title.size() * 11 + 14;
		int contentWidth = w - 18;
		int contentHeight = headerHeight + 6;
		for (MarkdownText.Line line : layout) {
			contentHeight += line.spacingBefore() + lineHeight(line, contentWidth);
		}
		bodyScroll.begin(ui, "news.body", x, y + 2, w, h - 4, contentHeight + 4);
		int lineY = y + 6 - bodyScroll.offset();
		for (var part : title) {
			g.text(font, part, x + 6, lineY, Theme.TEXT, false);
			lineY += 11;
		}
		Draw.text(g, font, Component.literal(post.category().toUpperCase(Locale.ROOT) + " · " + date(post.publishedAt())), x + 6, lineY + 1, Theme.TEXT_DIM);
		lineY += 14;
		Draw.rect(g, x + 6, lineY - 3, w - 18, 1, Theme.DIVIDER);
		lineY += 4;
		int index = 0;
		for (MarkdownText.Line line : layout) {
			lineY += line.spacingBefore();
			int height = lineHeight(line, contentWidth);
			switch (line.kind()) {
				case RULE -> Draw.rect(g, x + 6, lineY + 4, contentWidth, 1, Theme.DIVIDER);
				case BUTTON -> {
					int buttonWidth = Math.min(contentWidth, Math.max(80, font.width(line.label()) + 20));
					String url = line.url();
					Widgets.button(ui, "news.button." + post.id() + "." + index, x + 6, lineY, buttonWidth, BUTTON_HEIGHT, Component.literal(line.label()),
						Widgets.ButtonStyle.PRIMARY, () -> openLink(url));
					if (ui.hovered(x + 6, lineY, buttonWidth, BUTTON_HEIGHT)) {
						ui.tooltip(Component.literal(url));
					}
				}
				case IMAGE -> drawImage(ui, line, x + 6, lineY, contentWidth, height);
				case TEXT -> drawTextLine(ui, line, x + 6, lineY, w);
			}
			lineY += height;
			index++;
		}
		bodyScroll.end(ui, x, y + 2, w, h - 4);
		PretupidClient.get().news().markAllSeen();
	}

	private int lineHeight(MarkdownText.Line line, int width) {
		return switch (line.kind()) {
			case TEXT -> Math.round(10 * line.scale());
			case RULE -> 10;
			case BUTTON -> BUTTON_HEIGHT + 2;
			case IMAGE -> {
				NewsImages.Image image = images.get(line.url());
				if (image.state() == NewsImages.State.READY && image.width() > 0) {
					float scale = Math.min(1f, Math.min(width / (float) image.width(), MAX_IMAGE_HEIGHT / (float) image.height()));
					yield Math.max(8, Math.round(image.height() * scale)) + 4;
				}
				yield image.state() == NewsImages.State.FAILED ? 12 : IMAGE_PLACEHOLDER_HEIGHT;
			}
		};
	}

	private void drawImage(UiContext ui, MarkdownText.Line line, int x, int y, int width, int height) {
		var g = ui.g();
		NewsImages.Image image = images.get(line.url());
		if (image.state() == NewsImages.State.READY) {
			float scale = Math.min(1f, Math.min(width / (float) image.width(), MAX_IMAGE_HEIGHT / (float) image.height()));
			int drawWidth = Math.max(1, Math.round(image.width() * scale));
			int drawHeight = Math.max(1, Math.round(image.height() * scale));
			g.blit(RenderPipelines.GUI_TEXTURED, image.texture(), x, y, 0f, 0f, drawWidth, drawHeight, image.width(), image.height(), image.width(), image.height());
			if (!line.label().isEmpty() && ui.hovered(x, y, drawWidth, drawHeight)) {
				ui.tooltip(Component.literal(line.label()));
			}
			return;
		}
		if (image.state() == NewsImages.State.FAILED) {
			String alt = line.label().isEmpty() ? line.url() : line.label();
			Draw.text(g, ui.font(), Draw.ellipsize(ui.font(), Component.literal("[" + alt + "]"), width), x, y + 1, Theme.TEXT_DIM);
			return;
		}
		Draw.chamfer(g, x, y, width, height - 4, 0x30000000);
		Draw.centered(g, ui.font(), Component.translatable("pretupid.news.image_loading"), x + width / 2, y + (height - 4) / 2 - 4, Theme.TEXT_DIM);
	}

	private void drawTextLine(UiContext ui, MarkdownText.Line line, int x, int y, int w) {
		var g = ui.g();
		var font = ui.font();
		int lineX = x + line.indent();
		if (line.scale() != 1f) {
			g.pose().pushMatrix();
			g.pose().translate(lineX, y);
			g.pose().scale(line.scale(), line.scale());
			g.text(font, line.text(), 0, 0, line.color(), false);
			g.pose().popMatrix();
			return;
		}
		g.text(font, line.text(), lineX, y, line.color(), false);
		if (ui.hovered(lineX, y, w - 12 - line.indent(), 10)) {
			Style style = styleAt(font, line.text(), ui.mouseX() - lineX);
			if (style != null && style.getInsertion() != null) {
				String url = style.getInsertion();
				ui.interactive(lineX, y, w - 12 - line.indent(), 10, UiContext.Handler.onClick(() -> openLink(url)));
				ui.tooltip(Component.literal(url));
			}
		}
	}

	@Override
	public void release() {
		images.release();
	}

	private static Style styleAt(net.minecraft.client.gui.Font font, net.minecraft.util.FormattedCharSequence text, int target) {
		Style[] found = {null};
		float[] advance = {0f};
		text.accept((index, style, codePoint) -> {
			float width = font.getSplitter().stringWidth(net.minecraft.util.FormattedCharSequence.codepoint(codePoint, style));
			if (target < advance[0] + width) {
				found[0] = style;
				return false;
			}
			advance[0] += width;
			return true;
		});
		return found[0];
	}

	private void openLink(String url) {
		String safe = MarkdownText.safeUrl(url);
		if (safe != null) {
			ConfirmLinkScreen.confirmLinkNow(screen, URI.create(safe), false);
		}
	}

	private static String date(long seconds) {
		return seconds <= 0 ? "" : DATE.format(Instant.ofEpochSecond(seconds));
	}

	public static void openMenu() {
		Minecraft minecraft = Minecraft.getInstance();
		ClientMenuScreen menu = new ClientMenuScreen(Platform.screen());
		menu.select(ClientMenuScreen.Tab.NEWS);
		Platform.setScreen(menu);
	}
}

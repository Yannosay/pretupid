package net.pretupid.client.ui.screen.page;

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
		int contentHeight = headerHeight + 6;
		for (MarkdownText.Line line : layout) {
			contentHeight += line.spacingBefore() + 10;
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
		for (MarkdownText.Line line : layout) {
			lineY += line.spacingBefore();
			if (line.rule()) {
				Draw.rect(g, x + 6, lineY + 4, w - 18, 1, Theme.DIVIDER);
			} else {
				int lineX = x + 6 + line.indent();
				g.text(font, line.text(), lineX, lineY, line.color(), false);
				if (ui.hovered(lineX, lineY, w - 12 - line.indent(), 10)) {
					Style style = styleAt(font, line.text(), ui.mouseX() - lineX);
					if (style != null && style.getInsertion() != null) {
						String url = style.getInsertion();
						ui.interactive(lineX, lineY, w - 12 - line.indent(), 10, UiContext.Handler.onClick(() -> openLink(url)));
						ui.tooltip(Component.literal(url));
					}
				}
			}
			lineY += 10;
		}
		bodyScroll.end(ui, x, y + 2, w, h - 4);
		PretupidClient.get().news().markAllSeen();
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
		ClientMenuScreen menu = new ClientMenuScreen(minecraft.gui.screen());
		menu.select(ClientMenuScreen.Tab.NEWS);
		minecraft.gui.setScreen(menu);
	}
}

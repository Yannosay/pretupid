package net.pretupid.client.ui.screen;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.network.AuthSession;
import net.pretupid.client.news.NewsService;
import net.pretupid.client.social.SocialService;
import net.pretupid.client.ui.Draw;
import net.pretupid.client.ui.Theme;
import net.pretupid.client.ui.screen.page.NewsPage;
import net.pretupid.client.util.Colors;

final class TitleScreenPanels {
	private static final int MIN_SCREEN_WIDTH = 420;
	private static final int MIN_PANEL_WIDTH = 90;
	private static final int LOGO_HALF_WIDTH = 140;
	private static final int LOGO_BOTTOM = 92;
	private static final int MAX_PANEL_WIDTH = 150;
	private static final int PADDING = 6;
	private static final int BUTTON_HEIGHT = 20;
	private static final int MAX_FRIENDS = 3;
	private static final int TITLE_LINES = 2;
	private static final int SUMMARY_LINES = 2;
	private static final int PANEL_FILL = 0x90000000;
	private static final int PANEL_BORDER = 0x30FFFFFF;

	private boolean visible;
	private int panelWidth;
	private int top;
	private int leftX;
	private int rightX;
	private int leftHeight;
	private int rightHeight;
	private Button rejoinButton;
	private Button newsButton;

	boolean visible() {
		return visible;
	}

	void init(Screen screen, List<AbstractWidget> widgets) {
		rejoinButton = null;
		newsButton = null;
		PretupidClient client = PretupidClient.get();
		visible = client.settings().titleScreenPanels.enabled() && screen.width >= MIN_SCREEN_WIDTH;
		if (!visible) {
			return;
		}
		panelWidth = Math.min(MAX_PANEL_WIDTH, screen.width / 2 - 100 - 2 * PADDING - 8);
		if (panelWidth < MIN_PANEL_WIDTH) {
			visible = false;
			return;
		}
		boolean overlapsLogo = PADDING + 4 + panelWidth > screen.width / 2 - LOGO_HALF_WIDTH;
		top = overlapsLogo ? LOGO_BOTTOM : 32;
		leftX = PADDING + 4;
		rightX = screen.width - PADDING - 4 - panelWidth;
		leftHeight = 26 + 12 + MAX_FRIENDS * 10 + 6;
		rightHeight = 12 + TITLE_LINES * 10 + 4 + SUMMARY_LINES * 10 + 6;
		String address = client.settings().lastServerAddress.get();
		boolean canRejoin = !address.isBlank() && ServerAddress.isValidAddress(address);
		int tallest = Math.max(leftHeight + (canRejoin ? BUTTON_HEIGHT + PADDING : 0), rightHeight + BUTTON_HEIGHT + PADDING);
		if (top + tallest > screen.height - 34) {
			visible = false;
			return;
		}
		if (canRejoin) {
			String name = client.settings().lastServerName.get();
			Component label = Component.translatable("pretupid.title.rejoin", Draw.ellipsize(Minecraft.getInstance().font, name.isBlank() ? address : name, panelWidth - 50));
			rejoinButton = Button.builder(label, button -> rejoin(screen, address, name))
				.bounds(leftX + PADDING, top + leftHeight, panelWidth - 2 * PADDING, BUTTON_HEIGHT)
				.tooltip(Tooltip.create(Component.literal(address)))
				.build();
			widgets.add(rejoinButton);
			leftHeight += BUTTON_HEIGHT + PADDING;
		}
		newsButton = Button.builder(Component.translatable("pretupid.title.read_news"), button -> NewsPage.openMenu())
			.bounds(rightX + PADDING, top + rightHeight, panelWidth - 2 * PADDING, BUTTON_HEIGHT)
			.build();
		widgets.add(newsButton);
		rightHeight += BUTTON_HEIGHT + PADDING;
	}

	private static void rejoin(Screen parent, String address, String name) {
		Minecraft minecraft = Minecraft.getInstance();
		ServerData data = new ServerData(name.isBlank() ? address : name, address, ServerData.Type.OTHER);
		ConnectScreen.startConnecting(parent, minecraft, ServerAddress.parseString(address), data, false, null);
	}

	void render(GuiGraphicsExtractor graphics, int alpha) {
		if (!visible || alpha < 8) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		renderAccount(graphics, font, alpha);
		renderNews(graphics, font, alpha);
	}

	private static void panel(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int alpha) {
		Draw.rect(graphics, x, y, w, h, Colors.multiplyAlpha(PANEL_FILL, alpha / 255f));
		Draw.border(graphics, x, y, w, h, Colors.multiplyAlpha(PANEL_BORDER, alpha / 255f));
	}

	private void renderAccount(GuiGraphicsExtractor graphics, Font font, int alpha) {
		PretupidClient client = PretupidClient.get();
		panel(graphics, leftX, top, panelWidth, leftHeight, alpha);
		int x = leftX + PADDING;
		int y = top + PADDING;
		String name = Minecraft.getInstance().getUser().getName();
		Draw.text(graphics, font, Draw.ellipsize(font, Component.literal(name), panelWidth - 2 * PADDING), x, y, Colors.withAlpha(0xFFFFFF, alpha));
		boolean online = client.session().status() == AuthSession.Status.ONLINE;
		int dot = online ? Theme.SUCCESS : Theme.TEXT_DIM;
		Draw.rect(graphics, x, y + 13, 4, 4, Colors.withAlpha(dot, alpha));
		Component status = Component.translatable(online ? "pretupid.title.connected" : "pretupid.title.offline");
		Draw.text(graphics, font, Draw.ellipsize(font, status, panelWidth - 2 * PADDING - 8), x + 8, y + 11, Colors.withAlpha(0xA3ABB9, alpha));
		y += 26;
		List<SocialService.Friend> friends = client.social().friends().stream().filter(SocialService.Friend::online).toList();
		Component heading = Component.translatable("pretupid.title.friends_heading", friends.size());
		Draw.text(graphics, font, heading, x, y, Colors.withAlpha(0xFFFFFF, alpha));
		y += 12;
		if (friends.isEmpty()) {
			Draw.text(graphics, font, Draw.ellipsize(font, Component.translatable("pretupid.title.no_friends_online"), panelWidth - 2 * PADDING), x, y, Colors.withAlpha(0x7B8494, alpha));
			return;
		}
		for (int i = 0; i < Math.min(MAX_FRIENDS, friends.size()); i++) {
			SocialService.Friend friend = friends.get(i);
			String line = friend.server() == null || friend.server().isBlank() ? friend.name() : friend.name() + " · " + friend.server();
			Draw.rect(graphics, x, y + 3, 3, 3, Colors.withAlpha(Theme.SUCCESS, alpha));
			Draw.text(graphics, font, Draw.ellipsize(font, Component.literal(line), panelWidth - 2 * PADDING - 6), x + 6, y, Colors.withAlpha(0xD0D4DC, alpha));
			y += 10;
		}
	}

	private void renderNews(GuiGraphicsExtractor graphics, Font font, int alpha) {
		NewsService news = PretupidClient.get().news();
		panel(graphics, rightX, top, panelWidth, rightHeight, alpha);
		int x = rightX + PADDING;
		int y = top + PADDING;
		int width = panelWidth - 2 * PADDING;
		List<NewsService.Post> posts = news.posts();
		if (posts.isEmpty()) {
			Component empty = Component.translatable(news.loading() ? "pretupid.title.news_loading" : "pretupid.title.news_empty");
			Draw.text(graphics, font, Draw.ellipsize(font, empty, width), x, y, Colors.withAlpha(0xA3ABB9, alpha));
			return;
		}
		NewsService.Post post = posts.getFirst();
		int unread = news.unreadCount();
		Component label = unread > 0
			? Component.translatable("pretupid.title.news_new", unread)
			: Component.translatable("pretupid.title.news_latest");
		Draw.text(graphics, font, label, x, y, Colors.withAlpha(unread > 0 ? Theme.accent() : 0xA3ABB9, alpha));
		y += 12;
		List<FormattedCharSequence> title = font.split(Component.literal(post.title()), width);
		for (int i = 0; i < Math.min(TITLE_LINES, title.size()); i++) {
			Draw.text(graphics, font, title.get(i), x, y, Colors.withAlpha(0xFFFFFF, alpha), true);
			y += 10;
		}
		y = top + PADDING + 12 + TITLE_LINES * 10 + 4;
		List<FormattedCharSequence> summary = font.split(Component.literal(post.summary()), width);
		for (int i = 0; i < Math.min(SUMMARY_LINES, summary.size()); i++) {
			Draw.text(graphics, font, summary.get(i), x, y, Colors.withAlpha(0xA3ABB9, alpha), false);
			y += 10;
		}
	}
}

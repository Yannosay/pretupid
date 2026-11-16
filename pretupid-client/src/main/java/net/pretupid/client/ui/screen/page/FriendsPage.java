package net.pretupid.client.ui.screen.page;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.network.ApiClient;
import net.pretupid.client.social.LocalHosting;
import net.pretupid.client.social.SocialService;
import net.pretupid.client.ui.Draw;
import net.pretupid.client.ui.ScrollState;
import net.pretupid.client.ui.StatusText;
import net.pretupid.client.ui.TextInput;
import net.pretupid.client.ui.Theme;
import net.pretupid.client.ui.UiContext;
import net.pretupid.client.ui.Widgets;
import net.pretupid.client.ui.screen.ClientMenuScreen;
import net.pretupid.client.ui.screen.MenuPage;

public final class FriendsPage implements MenuPage {
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH).withZone(ZoneId.systemDefault());
	private static final int LIST_WIDTH = 120;

	private final ClientMenuScreen screen;
	private final ScrollState listScroll = new ScrollState();
	private final ScrollState chatScroll = new ScrollState();
	private final TextInput addInput = new TextInput(16, cp -> Character.isLetterOrDigit(cp) && cp < 0x80 || cp == '_');
	private final TextInput chatInput = new TextInput(256);
	private UUID selected;
	private Component message;
	private boolean messageError;
	private long messageAt;
	private int lastMessageCount = -1;
	private long confirmRemoveAt;

	public FriendsPage(ClientMenuScreen screen) {
		this.screen = screen;
		addInput.onSubmit(this::sendRequest);
		chatInput.onSubmit(this::sendChat);
	}

	private SocialService social() {
		return PretupidClient.get().social();
	}

	@Override
	public void onOpen() {
		social().setActive(true);
		if (selected != null) {
			social().openConversation(selected);
		}
	}

	public void onClosed() {
		social().setActive(false);
		social().openConversation(null);
	}

	private void show(Component text, boolean error) {
		message = text;
		messageError = error;
		messageAt = System.currentTimeMillis();
	}

	private <T> void track(CompletableFuture<T> future, Component success) {
		future.whenComplete((result, error) -> Minecraft.getInstance().execute(() -> {
			if (error != null) {
				show(Component.literal(ApiClient.describe(error)), true);
			} else if (success != null) {
				show(success, false);
			}
		}));
	}

	private void sendRequest() {
		String name = addInput.value().trim();
		if (name.length() < 3) {
			return;
		}
		social().sendRequest(name).whenComplete((status, error) -> Minecraft.getInstance().execute(() -> {
			if (error != null) {
				show(Component.literal(ApiClient.describe(error)), true);
				return;
			}
			addInput.setValue("");
			show(Component.translatable("friends".equals(status) ? "pretupid.friends.now_friends" : "pretupid.friends.request_sent", name), false);
		}));
	}

	private void sendChat() {
		String text = chatInput.value().strip();
		if (text.isEmpty() || selected == null) {
			return;
		}
		chatInput.setValue("");
		track(social().sendMessage(selected, text), null);
	}

	@Override
	public void draw(UiContext ui, int x, int y, int w, int h) {
		var g = ui.g();
		SocialService social = social();
		social.setActive(true);
		if (!social.available()) {
			int lineY = y + h / 2 - 16;
			for (FormattedCharSequence line : ui.font().split(Component.translatable("pretupid.friends.offline"), w - 40)) {
				g.text(ui.font(), line, x + 20, lineY, Theme.TEXT_MUTED, false);
				lineY += 11;
			}
			StatusText.drawSession(ui, x + 20, lineY + 6, w - 40);
			return;
		}
		drawList(ui, social, x, y, LIST_WIDTH, h - 12);
		int panelX = x + LIST_WIDTH + 8;
		int panelW = w - LIST_WIDTH - 8;
		Optional<SocialService.Friend> friend = selected == null ? Optional.empty() : social.friend(selected);
		if (friend.isPresent()) {
			drawChat(ui, social, friend.get(), panelX, y, panelW, h - 12);
		} else {
			selected = null;
			drawOverview(ui, social, panelX, y, panelW, h - 12);
		}
		if (message != null && System.currentTimeMillis() - messageAt < 7000L) {
			Draw.text(g, ui.font(), Draw.ellipsize(ui.font(), message, w), x, y + h - 9, messageError ? Theme.DANGER : Theme.SUCCESS);
		} else {
			StatusText.drawSession(ui, x, y + h - 9, w);
		}
	}

	private void drawList(UiContext ui, SocialService social, int x, int y, int w, int h) {
		var g = ui.g();
		var font = ui.font();
		List<SocialService.Friend> friends = social.friends();
		int addHeight = 22;
		boolean overviewActive = selected == null;
		boolean hoverOverview = ui.interactive(x, y, w - 6, 18, UiContext.Handler.onClick(() -> {
			selected = null;
			social.openConversation(null);
		}));
		Draw.chamfer(g, x, y, w - 6, 18, overviewActive ? Theme.accent(60) : hoverOverview ? Theme.SURFACE_HOVER : Theme.SURFACE);
		Component overviewLabel = social.incoming().isEmpty()
			? Component.translatable("pretupid.friends.add_title")
			: Component.translatable("pretupid.friends.requests_count", social.incoming().size());
		Draw.text(g, font, Draw.ellipsize(font, overviewLabel, w - 14), x + 5, y + 5, social.incoming().isEmpty() ? Theme.TEXT : Theme.WARNING);
		int listY = y + addHeight;
		int listH = h - addHeight;
		if (friends.isEmpty()) {
			int lineY = listY + 6;
			for (FormattedCharSequence line : font.split(Component.translatable(social.loaded() ? "pretupid.friends.none" : "pretupid.friends.loading"), w - 10)) {
				g.text(font, line, x + 4, lineY, Theme.TEXT_DIM, false);
				lineY += 10;
			}
			return;
		}
		listScroll.begin(ui, "friends.list", x, listY, w, listH, friends.size() * 22);
		int rowY = listY - listScroll.offset();
		for (SocialService.Friend friend : friends) {
			boolean active = friend.uuid().equals(selected);
			boolean hover = ui.interactive(x, rowY, w - 6, 20, UiContext.Handler.onClick(() -> select(friend.uuid())));
			Draw.chamfer(g, x, rowY, w - 6, 20, active ? Theme.accent(60) : hover ? Theme.SURFACE_HOVER : Theme.SURFACE);
			Draw.chamfer(g, x + 5, rowY + 8, 4, 4, friend.online() ? Theme.SUCCESS : Theme.TEXT_DIM);
			int nameWidth = w - 30 - (friend.unread() > 0 ? 16 : 0);
			Draw.text(g, font, Draw.ellipsize(font, Component.literal(friend.name()), nameWidth), x + 13, rowY + 6, friend.online() ? Theme.TEXT : Theme.TEXT_MUTED);
			if (friend.unread() > 0) {
				String count = friend.unread() > 9 ? "9+" : Integer.toString(friend.unread());
				int badgeWidth = font.width(count) + 6;
				Draw.chamfer(g, x + w - 10 - badgeWidth, rowY + 5, badgeWidth, 10, Theme.accent());
				Draw.text(g, font, count, x + w - 7 - badgeWidth, rowY + 6, 0xFFFFFFFF);
			}
			rowY += 22;
		}
		listScroll.end(ui, x, listY, w, listH);
	}

	private void select(UUID uuid) {
		selected = uuid;
		lastMessageCount = -1;
		chatScroll.reset();
		social().openConversation(uuid);
	}

	private void drawOverview(UiContext ui, SocialService social, int x, int y, int w, int h) {
		var g = ui.g();
		var font = ui.font();
		Widgets.sectionHeader(ui, Component.translatable("pretupid.friends.add_title"), x, y, w - 6);
		addInput.draw(ui, x, y + 14, w - 76, 18, Component.translatable("pretupid.friends.add_placeholder"));
		Widgets.button(ui, "friends.add", x + w - 72, y + 14, 66, 18, Component.translatable("pretupid.friends.add"), Widgets.ButtonStyle.PRIMARY, this::sendRequest);
		int rowY = y + 42;
		if (!social.incoming().isEmpty()) {
			Widgets.sectionHeader(ui, Component.translatable("pretupid.friends.incoming"), x, rowY, w - 6);
			rowY += 14;
			for (SocialService.Person person : social.incoming()) {
				if (rowY > y + h - 24) {
					break;
				}
				Draw.chamfer(g, x, rowY, w - 6, 20, Theme.SURFACE);
				Draw.text(g, font, Draw.ellipsize(font, Component.literal(person.name()), w - 150), x + 6, rowY + 6, Theme.TEXT);
				Widgets.button(ui, "friends.accept." + person.uuid(), x + w - 142, rowY + 2, 62, 16, Component.translatable("pretupid.friends.accept"), Widgets.ButtonStyle.PRIMARY,
					() -> track(social.respond(person.uuid(), true), Component.translatable("pretupid.friends.now_friends", person.name())));
				Widgets.button(ui, "friends.decline." + person.uuid(), x + w - 76, rowY + 2, 66, 16, Component.translatable("pretupid.friends.decline"), Widgets.ButtonStyle.GHOST,
					() -> track(social.respond(person.uuid(), false), null));
				rowY += 22;
			}
			rowY += 6;
		}
		if (!social.outgoing().isEmpty() && rowY < y + h - 30) {
			Widgets.sectionHeader(ui, Component.translatable("pretupid.friends.outgoing"), x, rowY, w - 6);
			rowY += 14;
			for (SocialService.Person person : social.outgoing()) {
				if (rowY > y + h - 22) {
					break;
				}
				Draw.text(g, font, Draw.ellipsize(font, Component.literal(person.name()), w - 90), x + 4, rowY + 4, Theme.TEXT_MUTED);
				Widgets.button(ui, "friends.cancel." + person.uuid(), x + w - 76, rowY, 66, 16, Component.translatable("pretupid.ui.cancel"), Widgets.ButtonStyle.GHOST,
					() -> track(social.remove(person.uuid()), null));
				rowY += 18;
			}
			rowY += 6;
		}
		if (rowY < y + h - 50) {
			Widgets.sectionHeader(ui, Component.translatable("pretupid.friends.privacy"), x, rowY, w - 6);
			rowY += 14;
			var settings = PretupidClient.get().settings();
			Widgets.settingRow(ui, screen, settings.allowFriendRequests, x, rowY, w - 6);
			rowY += Widgets.ROW_HEIGHT;
			if (rowY < y + h - Widgets.ROW_HEIGHT) {
				Widgets.settingRow(ui, screen, settings.shareServerWithFriends, x, rowY, w - 6);
				rowY += Widgets.ROW_HEIGHT;
			}
			if (rowY < y + h - Widgets.ROW_HEIGHT) {
				Widgets.settingRow(ui, screen, settings.friendNotifications, x, rowY, w - 6);
			}
		}
	}

	private void drawChat(UiContext ui, SocialService social, SocialService.Friend friend, int x, int y, int w, int h) {
		var g = ui.g();
		var font = ui.font();
		Draw.chamfer(g, x, y, w, 22, Theme.SURFACE);
		Draw.chamfer(g, x + 6, y + 9, 4, 4, friend.online() ? Theme.SUCCESS : Theme.TEXT_DIM);
		Draw.text(g, font, Component.literal(friend.name()).withStyle(style -> style.withBold(true)), x + 14, y + 3, Theme.TEXT);
		Component status = friend.online()
			? friend.server() != null ? Component.translatable("pretupid.friends.playing_on", friend.server()) : Component.translatable("pretupid.friends.online")
			: Component.translatable("pretupid.friends.offline_since", relative(friend.lastSeen()));
		Draw.text(g, font, Draw.ellipsize(font, status, w - 140), x + 14, y + 12, Theme.TEXT_DIM);
		int buttonX = x + w - 6;
		boolean armed = System.currentTimeMillis() - confirmRemoveAt < 3000L;
		buttonX -= 46;
		Widgets.button(ui, "friends.remove", buttonX, y + 3, 44, 16, Component.translatable(armed ? "pretupid.ui.confirm" : "pretupid.friends.remove"), Widgets.ButtonStyle.DANGER, () -> {
			if (System.currentTimeMillis() - confirmRemoveAt < 3000L) {
				confirmRemoveAt = 0L;
				track(social.remove(friend.uuid()), Component.translatable("pretupid.friends.removed", friend.name()));
				selected = null;
			} else {
				confirmRemoveAt = System.currentTimeMillis();
			}
		});
		buttonX -= 48;
		Widgets.button(ui, "friends.invite", buttonX, y + 3, 46, 16, Component.translatable("pretupid.friends.invite"), Widgets.ButtonStyle.SECONDARY, () -> invite(friend));
		if (friend.server() != null) {
			buttonX -= 40;
			Widgets.button(ui, "friends.join", buttonX, y + 3, 38, 16, Component.translatable("pretupid.friends.join"), Widgets.ButtonStyle.PRIMARY,
				() -> confirmJoin(friend.server(), friend.name(), "", false));
		}
		int inputHeight = 18;
		int chatY = y + 26;
		int chatH = h - 26 - inputHeight - 4;
		List<SocialService.Message> messages = social.conversation(friend.uuid());
		UUID self = Minecraft.getInstance().getUser().getProfileId();
		int bubbleWidth = Math.max(60, w - 40);
		int contentHeight = 0;
		for (SocialService.Message entry : messages) {
			contentHeight += messageHeight(font, entry, bubbleWidth) + 4;
		}
		if (messages.size() != lastMessageCount) {
			lastMessageCount = messages.size();
			chatScroll.scrollTo(Math.max(0, contentHeight - chatH));
		}
		chatScroll.begin(ui, "friends.chat." + friend.uuid(), x, chatY, w, chatH, contentHeight);
		int rowY = chatY - chatScroll.offset();
		if (messages.isEmpty()) {
			Draw.centered(g, font, Component.translatable("pretupid.friends.say_hi", friend.name()), x + w / 2, chatY + chatH / 2 - 4, Theme.TEXT_DIM);
		}
		for (SocialService.Message entry : messages) {
			int height = messageHeight(font, entry, bubbleWidth);
			boolean mine = entry.mine(self);
			if (entry.invite() != null) {
				drawInvite(ui, entry, mine, friend, x, rowY, w - 6, height);
			} else {
				List<FormattedCharSequence> lines = font.split(Component.literal(entry.text()), bubbleWidth - 10);
				int textWidth = 0;
				for (FormattedCharSequence line : lines) {
					textWidth = Math.max(textWidth, font.width(line));
				}
				int bw = Math.max(36, textWidth + 10);
				int bx = mine ? x + w - 6 - bw : x;
				Draw.chamfer(g, bx, rowY, bw, height, mine ? Theme.accent(110) : Theme.SURFACE_ACTIVE);
				int lineY = rowY + 4;
				for (FormattedCharSequence line : lines) {
					g.text(font, line, bx + 5, lineY, Theme.TEXT, false);
					lineY += 10;
				}
				Draw.text(g, font, Component.literal(TIME.format(Instant.ofEpochSecond(entry.at()))), mine ? bx - 30 : bx + bw + 4, rowY + height - 10, Theme.TEXT_DIM);
			}
			rowY += height + 4;
		}
		chatScroll.end(ui, x, chatY, w, chatH);
		chatInput.draw(ui, x, y + h - inputHeight, w - 56, inputHeight, Component.translatable("pretupid.friends.message_placeholder", friend.name()));
		Widgets.button(ui, "friends.send", x + w - 52, y + h - inputHeight, 46, inputHeight, Component.translatable("pretupid.friends.send"), Widgets.ButtonStyle.PRIMARY, this::sendChat);
	}

	private void drawInvite(UiContext ui, SocialService.Message entry, boolean mine, SocialService.Friend friend, int x, int y, int w, int height) {
		var g = ui.g();
		var font = ui.font();
		SocialService.Invite invite = entry.invite();
		Draw.chamfer(g, x, y, w, height, 0x3060A5FA);
		Draw.border(g, x, y, w, height, 0x8060A5FA);
		Component title = mine ? Component.translatable("pretupid.friends.invite_sent") : Component.translatable("pretupid.friends.invite_received", friend.name());
		Draw.text(g, font, Draw.ellipsize(font, title, w - 70), x + 6, y + 4, Theme.TEXT);
		boolean relayed = LocalHosting.tunnelHost(invite.address()).isPresent();
		String target = invite.label().isEmpty() ? (relayed ? friend.name() : invite.address())
			: relayed ? invite.label() : invite.label() + " (" + invite.address() + ")";
		Draw.text(g, font, Draw.ellipsize(font, Component.literal(target), w - 70), x + 6, y + 15, Theme.TEXT_MUTED);
		if (!mine) {
			Widgets.button(ui, "friends.invite.join." + entry.id(), x + w - 58, y + 5, 52, 16, Component.translatable("pretupid.friends.join"), Widgets.ButtonStyle.PRIMARY,
				() -> confirmJoin(invite.address(), friend.name(), invite.label(), invite.lan()));
		}
	}

	private static int messageHeight(net.minecraft.client.gui.Font font, SocialService.Message message, int bubbleWidth) {
		if (message.invite() != null) {
			return 26;
		}
		return font.split(Component.literal(message.text()), bubbleWidth - 10).size() * 10 + 6;
	}

	private void invite(SocialService.Friend friend) {
		Minecraft minecraft = Minecraft.getInstance();
		SocialService social = social();
		if (minecraft.getSingleplayerServer() != null) {
			LocalHosting hosting = PretupidClient.get().hosting();
			hosting.invite(friend.uuid());
			String label = minecraft.getSingleplayerServer().getWorldData().getLevelName();
			show(Component.translatable("pretupid.friends.hosting"), false);
			hosting.openToFriends().whenComplete((share, error) -> Minecraft.getInstance().execute(() -> {
				if (error != null) {
					show(Component.translatable("pretupid.friends.host_failed"), true);
					return;
				}
				Component sent = share.lan()
					? Component.translatable("pretupid.friends.invite_sent_lan", friend.name())
					: Component.translatable("pretupid.friends.invite_sent_world", friend.name());
				track(social.sendInvite(friend.uuid(), share.address(), label, share.lan()), sent);
			}));
			return;
		}
		ServerData server = minecraft.getCurrentServer();
		if (server == null || server.isLan()) {
			show(Component.translatable("pretupid.friends.invite_nowhere"), true);
			return;
		}
		track(social.sendInvite(friend.uuid(), server.ip, server.name, false), Component.translatable("pretupid.friends.invite_sent_to", friend.name()));
	}

	private void confirmJoin(String address, String friendName, String label, boolean lan) {
		Minecraft minecraft = Minecraft.getInstance();
		Optional<UUID> tunnelHost = LocalHosting.tunnelHost(address);
		if (tunnelHost.isEmpty() && !ServerAddress.isValidAddress(address)) {
			show(Component.translatable("pretupid.friends.invalid_address"), true);
			return;
		}
		Component body = tunnelHost.isPresent()
			? Component.translatable("pretupid.friends.join_confirm_world", friendName, label.isEmpty() ? friendName : label)
			: Component.translatable(lan ? "pretupid.friends.join_confirm_lan" : "pretupid.friends.join_confirm", friendName, address);
		minecraft.gui.setScreen(new ConfirmScreen(accepted -> {
			if (!accepted) {
				minecraft.gui.setScreen(screen);
				return;
			}
			if (minecraft.level != null) {
				minecraft.level.disconnect(Component.translatable("pretupid.friends.joining"));
				minecraft.disconnectWithProgressScreen();
			}
			String target = address;
			if (tunnelHost.isPresent()) {
				try {
					target = PretupidClient.get().hosting().joinThroughTunnel(tunnelHost.get());
				} catch (java.io.IOException exception) {
					minecraft.gui.setScreen(screen);
					show(Component.translatable("pretupid.friends.join_failed"), true);
					return;
				}
			}
			ServerData data = new ServerData(friendName, target, lan ? ServerData.Type.LAN : ServerData.Type.OTHER);
			ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(target), data, false, null);
		}, Component.translatable("pretupid.friends.join_title"), body));
	}

	private static Component relative(long seconds) {
		if (seconds <= 0) {
			return Component.translatable("pretupid.friends.long_ago");
		}
		long minutes = Math.max(0, (System.currentTimeMillis() / 1000L - seconds) / 60L);
		if (minutes < 1) {
			return Component.translatable("pretupid.friends.just_now");
		}
		if (minutes < 60) {
			return Component.translatable("pretupid.friends.minutes_ago", minutes);
		}
		if (minutes < 60 * 24) {
			return Component.translatable("pretupid.friends.hours_ago", minutes / 60);
		}
		return Component.translatable("pretupid.friends.days_ago", minutes / (60 * 24));
	}
}

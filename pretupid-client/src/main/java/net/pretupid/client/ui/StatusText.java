package net.pretupid.client.ui;

import net.minecraft.network.chat.Component;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.network.AuthSession;

public final class StatusText {
	private StatusText() {
	}

	public static Component session() {
		PretupidClient client = PretupidClient.get();
		return switch (client.session().status()) {
			case ONLINE -> client.presence().onlineUsers() > 0
				? Component.translatable("pretupid.presence.online_count", client.presence().onlineUsers())
				: Component.translatable("pretupid.presence.online");
			case CONNECTING -> Component.translatable("pretupid.presence.connecting");
			case AUTH_FAILED -> Component.translatable("pretupid.presence.offline_account");
			case UNREACHABLE -> Component.translatable("pretupid.presence.unreachable");
			case SUSPENDED -> Component.translatable("pretupid.presence.suspended");
			case DISABLED -> Component.translatable("pretupid.presence.disabled");
		};
	}

	public static int color(AuthSession.Status status) {
		return switch (status) {
			case ONLINE -> Theme.SUCCESS;
			case CONNECTING -> Theme.WARNING;
			case UNREACHABLE, SUSPENDED -> Theme.DANGER;
			default -> Theme.TEXT_DIM;
		};
	}

	public static void drawSession(UiContext ui, int x, int y, int w) {
		AuthSession.Status status = PretupidClient.get().session().status();
		Draw.chamfer(ui.g(), x, y + 2, 4, 4, color(status));
		Draw.text(ui.g(), ui.font(), Draw.ellipsize(ui.font(), session(), w - 8), x + 8, y, Theme.TEXT_MUTED);
	}
}

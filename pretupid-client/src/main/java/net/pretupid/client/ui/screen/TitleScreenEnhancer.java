package net.pretupid.client.ui.screen;

import net.pretupid.client.platform.Gfx;
import net.pretupid.client.platform.PlatformHud;
import net.pretupid.client.platform.Platform;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.PretupidInfo;
import net.pretupid.client.network.AuthSession;
import net.pretupid.client.ui.Draw;
import net.pretupid.client.ui.Easing;
import net.pretupid.client.ui.Theme;
import net.pretupid.client.ui.screen.page.NewsPage;
import net.pretupid.client.util.Colors;

public final class TitleScreenEnhancer {
	private static final long STAGGER_MS = 45L;
	private static final long ENTER_MS = 420L;
	private static final int SLIDE = 14;

	private final List<Entry> entries = new ArrayList<>();
	private final TitleScreenPanels panels = new TitleScreenPanels();
	private long animationStart;
	private long lastInit;
	private Button newsButton;
	private Button updateButton;

	private record Entry(AbstractWidget widget, int baseY, int order) {
	}

	public void register() {
		ScreenEvents.AFTER_INIT.register((minecraft, screen, width, height) -> {
			if (!(screen instanceof TitleScreen)) {
				return;
			}
			PretupidClient client = PretupidClient.get();
			panels.init(screen, Screens.getWidgets(screen));
			addTopRightButtons(screen, width);
			entries.clear();
			if (client.settings().enhancedTitleScreen.enabled() && client.settings().animations.enabled()) {
				List<AbstractWidget> widgets = new ArrayList<>(Screens.getWidgets(screen));
				widgets.sort(Comparator.comparingInt(AbstractWidget::getY).thenComparingInt(AbstractWidget::getX));
				for (int i = 0; i < widgets.size(); i++) {
					AbstractWidget widget = widgets.get(i);
					if (widget != newsButton && widget != updateButton) {
						entries.add(new Entry(widget, widget.getY(), i));
					}
				}
				long now = System.currentTimeMillis();
				if (now - lastInit > 1500L) {
					animationStart = now;
				}
				lastInit = now;
			}
			PlatformHud.beforeScreen(screen, this::animate);
			PlatformHud.afterScreenBackground(screen, this::drawBackdrop);
			PlatformHud.afterScreen(screen, this::drawOverlay);
			ScreenEvents.remove(screen).register(current -> {
				for (Entry entry : entries) {
					entry.widget().setY(entry.baseY());
					entry.widget().setAlpha(1f);
				}
				lastInit = System.currentTimeMillis();
			});
		});
	}

	private void addTopRightButtons(Screen screen, int width) {
		PretupidClient client = PretupidClient.get();
		List<AbstractWidget> widgets = Screens.getWidgets(screen);
		int y = 6;
		newsButton = null;
		updateButton = null;
		if (client.settings().newsButton.enabled() && !panels.visible()) {
			newsButton = Button.builder(newsLabel(), button -> NewsPage.openMenu()).bounds(width - 76, y, 70, 20).build();
			newsButton.setTooltip(Tooltip.create(Component.translatable("pretupid.news.tooltip")));
			widgets.add(newsButton);
			y += 24;
		}
		if (client.updates().updateVisible()) {
			updateButton = Button.builder(Component.translatable("pretupid.update.button"), button -> Platform.setScreen(new UpdateScreen(screen)))
				.bounds(width - 106, y, 100, 20)
				.build();
			updateButton.setTooltip(Tooltip.create(Component.translatable("pretupid.update.tooltip", client.updates().release().version())));
			widgets.add(updateButton);
		}
	}

	private static Component newsLabel() {
		int unread = PretupidClient.get().news().unreadCount();
		return unread > 0
			? Component.translatable("pretupid.news.button_unread", unread > 9 ? "9+" : Integer.toString(unread))
			: Component.translatable("pretupid.news.button");
	}

	private void animate() {
		if (newsButton != null) {
			newsButton.setMessage(newsLabel());
		}
		if (entries.isEmpty()) {
			return;
		}
		long elapsed = System.currentTimeMillis() - animationStart;
		boolean done = true;
		for (Entry entry : entries) {
			float t = Math.clamp((elapsed - entry.order() * STAGGER_MS) / (float) ENTER_MS, 0f, 1f);
			float eased = Easing.outCubic(t);
			entry.widget().setY(entry.baseY() + Math.round((1f - eased) * SLIDE));
			entry.widget().setAlpha(eased);
			done &= t >= 1f;
		}
		if (done) {
			for (Entry entry : entries) {
				entry.widget().setY(entry.baseY());
				entry.widget().setAlpha(1f);
			}
			entries.clear();
		}
	}

	private void drawBackdrop(Gfx graphics, Screen screen) {
		if (panels.visible()) {
			boolean animated = PretupidClient.get().settings().animations.enabled();
			float fade = animated ? Easing.outCubic(Math.clamp((System.currentTimeMillis() - animationStart) / 600f, 0f, 1f)) : 1f;
			panels.render(graphics, Math.round(fade * 255));
		}
		if (!PretupidClient.get().settings().enhancedTitleScreen.enabled()) {
			return;
		}
		int width = screen.width;
		int height = screen.height;
		float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 2400.0);
		Draw.verticalGradient(graphics, 0, 0, width, height / 3, 0x50000000, 0x00000000);
		Draw.verticalGradient(graphics, 0, height * 2 / 3, width, height - height * 2 / 3, 0x00000000, 0x70000000);
		int glow = Colors.withAlpha(Theme.accent(), Math.round(18 + 14 * pulse));
		Draw.verticalGradient(graphics, 0, height - 60, width, 60, 0x00000000, glow);
	}

	private void drawOverlay(Gfx graphics, Screen screen) {
		PretupidClient client = PretupidClient.get();
		if (!client.settings().enhancedTitleScreen.enabled()) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		long elapsed = System.currentTimeMillis() - animationStart;
		float fade = client.settings().animations.enabled() ? Easing.outCubic(Math.clamp(elapsed / 700f, 0f, 1f)) : 1f;
		int alpha = Math.round(fade * 255);
		if (alpha < 8) {
			return;
		}
		Component brand = Component.literal("Pretupid Client " + PretupidInfo.displayVersion());
		int y = screen.height - 20;
		Draw.text(graphics, font, brand, 2, y, Colors.withAlpha(0xFFFFFF, Math.round(alpha * 0.85f)));
		if (client.session().status() == AuthSession.Status.ONLINE && !panels.visible()) {
			int friendsOnline = (int) client.social().friends().stream().filter(friend -> friend.online()).count();
			Component status = friendsOnline > 0
				? Component.translatable("pretupid.title.friends_online", friendsOnline)
				: Component.translatable("pretupid.title.connected");
			Draw.chamfer(graphics, 2, y - 10, 4, 4, Colors.withAlpha(Theme.SUCCESS, alpha));
			Draw.text(graphics, font, status, 9, y - 12, Colors.withAlpha(0xA3ABB9, alpha));
		}
	}
}

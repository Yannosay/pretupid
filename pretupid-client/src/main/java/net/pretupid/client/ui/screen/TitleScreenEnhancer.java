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
import net.pretupid.client.mixin.TitleScreenAccess;
import net.pretupid.client.network.AuthSession;
import net.pretupid.client.ui.Draw;
import net.pretupid.client.ui.Easing;
import net.pretupid.client.ui.Theme;
import net.pretupid.client.ui.screen.page.NewsPage;
import net.pretupid.client.util.Colors;

public final class TitleScreenEnhancer {
	private static final long STAGGER_MS = 45L;
	private static final long ENTER_MS = 420L;
	private static final int SLIDE = 24;

	private final List<Entry> entries = new ArrayList<>();
	private final TitleScreenPanels panels = new TitleScreenPanels();
	private long animationStart;
	private long lastInit;
	private Button newsButton;
	private Button updateButton;
	private float parallaxX;
	private float parallaxY;
	private float markerX;
	private float markerY;
	private float markerWidth;
	private float markerOpacity;
	private long markerAt = System.nanoTime();

	private record Entry(AbstractWidget widget, int baseY, int order) {
	}

	public void register() {
		ScreenEvents.AFTER_INIT.register((minecraft, screen, width, height) -> {
			if (!(screen instanceof TitleScreen)) {
				return;
			}
			PretupidClient client = PretupidClient.get();
			panels.init(screen, Platform.widgets(screen));
			addTopRightButtons(screen, width);
			entries.clear();
			if (client.settings().enhancedTitleScreen.enabled() && client.settings().animations.enabled()) {
				((TitleScreenAccess) screen).pretupid$setFading(false);
				List<AbstractWidget> widgets = new ArrayList<>(Platform.widgets(screen));
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
		List<AbstractWidget> widgets = Platform.widgets(screen);
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
		boolean animated = PretupidClient.get().settings().animations.enabled();
		if (PretupidClient.get().settings().enhancedTitleScreen.enabled()) {
			drawCinematic(graphics, screen.width, screen.height, animated);
		}
		if (panels.visible()) {
			float fade = animated ? Easing.outCubic(Math.clamp((System.currentTimeMillis() - animationStart) / 600f, 0f, 1f)) : 1f;
			panels.render(graphics, Math.round(fade * 255));
		}
	}

	private void drawCinematic(Gfx graphics, int width, int height, boolean animated) {
		long now = System.currentTimeMillis();
		float fade = animated ? Easing.outCubic(Math.clamp((now - animationStart) / 1200f, 0f, 1f)) : 1f;
		float pulse = 0.5f + 0.5f * (float) Math.sin(now / 2400.0);
		Draw.verticalGradient(graphics, 0, 0, width, height / 3, Colors.withAlpha(0x000000, Math.round(0xA0 * fade)), 0x00000000);
		Draw.verticalGradient(graphics, 0, height / 2, width, height - height / 2, 0x00000000, Colors.withAlpha(0x000000, Math.round(0x58 * fade)));
		int side = Math.max(24, width / 6);
		for (int i = 0; i < side; i += 2) {
			int alpha = Math.round(0x70 * fade * (1f - i / (float) side) * (1f - i / (float) side));
			Draw.rect(graphics, i, 0, 2, height, Colors.withAlpha(0x000000, alpha));
			Draw.rect(graphics, width - i - 2, 0, 2, height, Colors.withAlpha(0x000000, alpha));
		}
		int halo = Math.round((26 + 18 * pulse) * fade);
		int haloX = width / 2;
		int haloY = Math.round(height * 0.17f);
		int radiusX = Math.round(width * 0.36f);
		int radiusY = Math.round(height * 0.2f);
		for (int dy = -radiusY; dy <= radiusY; dy += 2) {
			float ny = dy / (float) radiusY;
			float span = (float) Math.sqrt(Math.max(0f, 1f - ny * ny));
			int half = Math.round(radiusX * span);
			for (int ring = 0; ring < 3; ring++) {
				int inner = Math.round(half * (1f - ring * 0.3f));
				int alpha = Math.round(halo * (1f - Math.abs(ny)) * 0.34f);
				Draw.rect(graphics, haloX - inner, haloY + dy, inner * 2, 2, Colors.withAlpha(Theme.accent(), alpha));
			}
		}
		if (animated) {
			Minecraft minecraft = Minecraft.getInstance();
			float targetX = (float) (minecraft.mouseHandler.xpos() / Math.max(1, minecraft.getWindow().getScreenWidth())) - 0.5f;
			float targetY = (float) (minecraft.mouseHandler.ypos() / Math.max(1, minecraft.getWindow().getScreenHeight())) - 0.5f;
			parallaxX += (targetX - parallaxX) * 0.06f;
			parallaxY += (targetY - parallaxY) * 0.06f;
			for (int i = 0; i < 36; i++) {
				long seed = i * 2654435761L;
				float depth = (i % 3 + 1) / 3f;
				float speed = (4f + (seed >>> 8 & 7)) * (0.6f + depth);
				double travel = (now % 3_600_000L) / 1000.0 * speed / height + ((seed >>> 16) & 1023) / 1023.0;
				float life = (float) (travel - Math.floor(travel));
				float px = ((seed >>> 4) & 4095) / 4095f * width + (float) Math.sin(now / 2100.0 + i) * 6f - parallaxX * (4f + 16f * depth);
				float py = height * (1.05f - life * 1.1f) - parallaxY * (2f + 8f * depth);
				float twinkle = 0.55f + 0.45f * (float) Math.sin(now / 520.0 + i * 1.7);
				int alpha = Math.round(fade * twinkle * (50 + 80 * depth) * Math.min(1f, Math.min(life * 5f, (1f - life) * 3f)));
				int size = depth > 0.9f ? 2 : 1;
				int color = (seed >>> 28 & 1) == 0 ? Theme.accent() : 0xFFFFF2D6;
				Draw.rect(graphics, Math.round(px), Math.round(py), size, size, Colors.withAlpha(color, alpha));
			}
		}
	}

	private void drawHoverMarker(Gfx graphics, Screen screen, int alpha, boolean animated) {
		AbstractWidget target = null;
		for (AbstractWidget widget : Platform.widgets(screen)) {
			if (widget.visible && widget.active && widget.isHovered() && widget.getWidth() >= 60 && widget.getHeight() >= 18) {
				target = widget;
				break;
			}
		}
		long now = System.nanoTime();
		float delta = Math.clamp((now - markerAt) / 1_000_000_000f, 0f, 0.1f);
		markerAt = now;
		float rate = animated ? 1f - (float) Math.exp(-delta * 18f) : 1f;
		if (target != null) {
			float x = target.getX() + 2;
			float y = target.getY() + target.getHeight() - 3;
			float width = target.getWidth() - 4;
			if (markerOpacity < 0.02f) {
				markerX = x;
				markerY = y;
				markerWidth = width;
			} else {
				markerX += (x - markerX) * rate;
				markerY += (y - markerY) * rate;
				markerWidth += (width - markerWidth) * rate;
			}
			markerOpacity += (1f - markerOpacity) * rate;
		} else {
			markerOpacity -= markerOpacity * rate;
		}
		if (markerOpacity >= 0.02f) {
			Draw.rect(graphics, Math.round(markerX), Math.round(markerY), Math.round(markerWidth), 1, Colors.withAlpha(Theme.accent(), Math.round(alpha * markerOpacity)));
		}
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
		drawHoverMarker(graphics, screen, alpha, client.settings().animations.enabled());
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

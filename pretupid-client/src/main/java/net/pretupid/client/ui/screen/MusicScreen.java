package net.pretupid.client.ui.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.module.hud.NowPlayingModule;
import net.pretupid.client.music.MusicController;
import net.pretupid.client.music.MusicTrack;
import net.pretupid.client.ui.Draw;
import net.pretupid.client.ui.PretupidScreen;
import net.pretupid.client.ui.ScrollState;
import net.pretupid.client.ui.TextInput;
import net.pretupid.client.ui.Theme;
import net.pretupid.client.ui.UiContext;
import net.pretupid.client.ui.UiIcon;
import net.pretupid.client.ui.Widgets;
import net.pretupid.client.util.Colors;

public final class MusicScreen extends PretupidScreen {
	private static final int ROW = 24;
	private static final int QUEUE_ROW = 16;
	private static final int QUEUE_WIDTH = 132;

	private final MusicController music = PretupidClient.get().music();
	private final NowPlayingModule module = PretupidClient.get().modules().get(NowPlayingModule.class);
	private final ScrollState listScroll = new ScrollState();
	private final ScrollState queueScroll = new ScrollState();
	private final TextInput search = new TextInput(32);

	public MusicScreen(Screen parent) {
		super(Component.translatable("pretupid.music.title"), parent);
		music.catalog().invalidate();
		search.onChange(text -> listScroll.reset());
	}

	private void activate() {
		if (!module.enabledSetting().get()) {
			module.setEnabled(true);
		}
	}

	private List<MusicTrack> filtered() {
		String query = search.value().trim().toLowerCase(Locale.ROOT);
		List<MusicTrack> all = music.catalog().tracks(minecraft);
		if (query.isEmpty()) {
			return all;
		}
		List<MusicTrack> result = new ArrayList<>();
		for (MusicTrack track : all) {
			if (track.title().toLowerCase(Locale.ROOT).contains(query) || track.artist().toLowerCase(Locale.ROOT).contains(query)) {
				result.add(track);
			}
		}
		return result;
	}

	@Override
	protected void drawContent(UiContext ui) {
		var g = ui.g();
		int w = Math.min(520, width - 12);
		int h = Math.min(320, height - 12);
		int x = (width - w) / 2;
		int y = (height - h) / 2;
		Draw.shadow(g, x, y, w, h, 6, 1f);
		Draw.glass(g, x, y, w, h, Theme.WINDOW, Theme.WINDOW_BORDER);
		Draw.textShadow(g, font, title, x + 10, y + 9, Theme.TEXT);
		Widgets.button(ui, "music.done", x + w - 60, y + 6, 52, 16, Component.translatable("pretupid.ui.done"), Widgets.ButtonStyle.PRIMARY, this::onClose);
		MusicTrack current = music.current(minecraft);
		drawNowPlaying(ui, current, x + 10, y + 27, w - 20);
		int contentY = y + 63;
		int queueWidth = w >= 400 ? QUEUE_WIDTH : 0;
		int listWidth = w - 20 - (queueWidth > 0 ? queueWidth + 6 : 0);
		search.draw(ui, x + 10, contentY, listWidth, 16, Component.translatable("pretupid.ui.search"));
		int listY = contentY + 21;
		int listH = y + h - 8 - listY;
		drawList(ui, current, x + 10, listY, listWidth, listH);
		if (queueWidth > 0) {
			drawQueue(ui, x + w - 10 - queueWidth, contentY, queueWidth, y + h - 8 - contentY);
		}
	}

	private void drawNowPlaying(UiContext ui, MusicTrack current, int x, int y, int w) {
		var g = ui.g();
		Draw.chamfer(g, x, y, w, 30, Theme.SURFACE);
		int buttons = 132;
		int textWidth = w - buttons - 14;
		Component name = current == null ? Component.translatable("pretupid.music.nothing") : Component.literal(current.title());
		Draw.text(g, font, Draw.ellipsize(font, name, textWidth), x + 7, y + 5, current == null ? Theme.TEXT_MUTED : Theme.TEXT);
		String mode = music.looping() != null ? "pretupid.music.mode.loop" : music.overridesVanilla() ? "pretupid.music.mode.manual" : "pretupid.music.mode.dynamic";
		Component detail = current == null || current.artist().isEmpty()
			? Component.translatable(mode)
			: Component.literal(current.artist() + "  ").append(Component.translatable(mode));
		Draw.text(g, font, Draw.ellipsize(font, detail, textWidth), x + 7, y + 17, Theme.TEXT_DIM);
		Widgets.button(ui, "music.skip", x + w - buttons - 4, y + 7, 54, 16, Component.translatable("pretupid.music.skip"), Widgets.ButtonStyle.SECONDARY, () -> music.skip(minecraft));
		Widgets.button(ui, "music.dynamic", x + w - 74, y + 7, 70, 16, Component.translatable("pretupid.music.dynamic"),
			music.overridesVanilla() ? Widgets.ButtonStyle.SECONDARY : Widgets.ButtonStyle.GHOST, () -> music.useDynamic(minecraft));
	}

	private void drawList(UiContext ui, MusicTrack current, int x, int y, int w, int h) {
		var g = ui.g();
		List<MusicTrack> tracks = filtered();
		listScroll.begin(ui, "music.list", x, y, w, h, Math.max(1, tracks.size()) * ROW);
		int offset = listScroll.offset();
		if (tracks.isEmpty()) {
			Draw.centered(g, font, Component.translatable("pretupid.ui.no_results"), x + w / 2, y + 16, Theme.TEXT_DIM);
		}
		int first = Math.max(0, offset / ROW);
		int last = Math.min(tracks.size(), (offset + h) / ROW + 1);
		for (int index = first; index < last; index++) {
			drawTrack(ui, tracks.get(index), current, x, y + index * ROW - offset, w - 6);
		}
		listScroll.end(ui, x, y, w, h);
	}

	private void drawTrack(UiContext ui, MusicTrack track, MusicTrack current, int x, int y, int w) {
		var g = ui.g();
		boolean active = track.equals(current);
		Draw.chamfer(g, x, y, w, ROW - 2, active ? Colors.lerp(Theme.SURFACE, Theme.accent(), 0.25f) : Theme.SURFACE);
		int buttons = 40 + 2 + 40 + 2 + 44;
		int textWidth = w - buttons - 12;
		Draw.text(g, font, Draw.ellipsize(font, track.title(), textWidth), x + 5, y + 3, Theme.TEXT);
		Draw.text(g, font, Draw.ellipsize(font, track.artist(), textWidth), x + 5, y + 12, Theme.TEXT_DIM);
		String id = track.file().toString();
		int bx = x + w - 3 - 44;
		Widgets.button(ui, "music.queue." + id, bx, y + 3, 44, 16, Component.translatable("pretupid.music.queue"), Widgets.ButtonStyle.GHOST, () -> {
			activate();
			music.enqueue(minecraft, track);
		});
		bx -= 42;
		Widgets.button(ui, "music.loop." + id, bx, y + 3, 40, 16, Component.translatable("pretupid.music.loop"),
			track.equals(music.looping()) ? Widgets.ButtonStyle.PRIMARY : Widgets.ButtonStyle.GHOST, () -> {
				activate();
				music.loop(minecraft, track);
			});
		bx -= 42;
		Widgets.button(ui, "music.play." + id, bx, y + 3, 40, 16, Component.translatable("pretupid.music.play"), Widgets.ButtonStyle.SECONDARY, () -> {
			activate();
			music.play(minecraft, track);
		});
	}

	private void drawQueue(UiContext ui, int x, int y, int w, int h) {
		var g = ui.g();
		List<MusicTrack> queue = music.queue();
		Draw.chamfer(g, x, y, w, h, Theme.SURFACE);
		Component header = Component.translatable("pretupid.music.up_next", queue.size());
		Draw.text(g, font, Draw.ellipsize(font, header, w - 50), x + 5, y + 5, Theme.TEXT_MUTED);
		if (!queue.isEmpty()) {
			Widgets.button(ui, "music.clear", x + w - 42, y + 2, 39, 14, Component.translatable("pretupid.music.clear"), Widgets.ButtonStyle.GHOST, music::clearQueue);
		}
		int listY = y + 19;
		int listH = h - 22;
		queueScroll.begin(ui, "music.queue_list", x + 2, listY, w - 4, listH, Math.max(1, queue.size()) * QUEUE_ROW);
		int top = listY - queueScroll.offset();
		for (int index = 0; index < queue.size(); index++) {
			int position = index;
			Draw.text(g, font, Draw.ellipsize(font, queue.get(index).title(), w - 32), x + 5, top + 4, Theme.TEXT);
			Widgets.iconButton(ui, "music.remove." + index, x + w - 23, top + 1, 14, 13, UiIcon.CLOSE, () -> music.removeFromQueue(position));
			top += QUEUE_ROW;
		}
		queueScroll.end(ui, x + 2, listY, w - 4, listH);
	}
}

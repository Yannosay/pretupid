package net.pretupid.client.ui.screen;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.cinematic.CameraKeyframe;
import net.pretupid.client.cinematic.CinematicDirector;
import net.pretupid.client.cinematic.CinematicOverlay;
import net.pretupid.client.cinematic.Shot;
import net.pretupid.client.cinematic.ShotTemplate;
import net.pretupid.client.module.visual.CinematicModule;
import net.pretupid.client.module.visual.FreecamModule;
import net.pretupid.client.platform.Gfx;
import net.pretupid.client.platform.Platform;
import net.pretupid.client.setting.NumberSetting;
import net.pretupid.client.ui.Draw;
import net.pretupid.client.ui.Icon;
import net.pretupid.client.ui.Popup;
import net.pretupid.client.ui.PretupidScreen;
import net.pretupid.client.ui.Theme;
import net.pretupid.client.ui.UiContext;
import net.pretupid.client.ui.UiIcon;
import net.pretupid.client.ui.Widgets;
import net.pretupid.client.util.Colors;
import net.pretupid.client.util.PlatformOpener;

public final class CinematicEditorScreen extends PretupidScreen {
	private static final int PANEL_HEIGHT = 174;
	private static final int TIMELINE_HEIGHT = 40;
	private static final double SNAP = 0.05;
	private static final double[] SPEEDS = {0.25, 0.5, 1.0, 1.5, 2.0, 4.0};

	private final CinematicDirector director = PretupidClient.get().cinematic();
	private final CinematicModule module = PretupidClient.get().modules().get(CinematicModule.class);
	private final NumberSetting fov = new NumberSetting("fov", 70, CameraKeyframe.MIN_FOV, CameraKeyframe.MAX_FOV, 1, "°");
	private final NumberSetting roll = new NumberSetting("roll", 0, -180, 180, 1, "°");
	private final NumberSetting time = new NumberSetting("time", 0, 0, 300, 0.05, "s");
	private int selected = -1;
	private boolean syncing;
	private double timelineSeconds = 10.0;

	public CinematicEditorScreen() {
		super(Component.translatable("pretupid.cinematic.title"), null);
		fov.onChange(value -> editSelected(keyframe -> keyframe.withFov(value.floatValue())));
		roll.onChange(value -> editSelected(keyframe -> keyframe.withRoll(value.floatValue())));
		time.onChange(value -> editSelected(keyframe -> keyframe.withTime(value)));
		Shot shot = director.current();
		if (shot != null && !shot.keyframes().isEmpty()) {
			select(0);
		}
	}

	private interface Edit {
		CameraKeyframe apply(CameraKeyframe keyframe);
	}

	private void editSelected(Edit edit) {
		Shot shot = director.current();
		if (syncing || shot == null || selected < 0 || selected >= shot.keyframes().size()) {
			return;
		}
		selected = director.updateKeyframe(selected, edit.apply(shot.keyframes().get(selected)));
	}

	private void select(int index) {
		Shot shot = director.current();
		if (shot == null || index < 0 || index >= shot.keyframes().size()) {
			selected = -1;
			return;
		}
		selected = index;
		CameraKeyframe keyframe = shot.keyframes().get(index);
		syncing = true;
		try {
			fov.set((double) Math.round(keyframe.fov()));
			roll.set((double) Math.round(keyframe.roll()));
			time.set(Math.round(keyframe.time() / SNAP) * SNAP);
		} finally {
			syncing = false;
		}
	}

	private void jumpTo(int index) {
		Shot shot = director.current();
		if (shot == null || shot.keyframes().isEmpty()) {
			return;
		}
		int target = Math.clamp(index, 0, shot.keyframes().size() - 1);
		select(target);
		director.preview(shot.keyframes().get(target).time());
	}

	@Override
	protected void renderScreenBackground(Gfx graphics, int mouseX, int mouseY, float partialTick) {
	}

	@Override
	public void removed() {
		if (!director.rendering()) {
			director.stop();
		}
		super.removed();
	}

	@Override
	protected boolean handleKey(KeyEvent event) {
		if (director.rendering()) {
			if (event.isEscape()) {
				director.cancelRender();
				return true;
			}
			return true;
		}
		int key = event.key();
		if (key == InputConstants.KEY_SPACE) {
			togglePlay();
			return true;
		}
		if (director.playing()) {
			director.pause();
			return true;
		}
		Shot shot = director.current();
		switch (key) {
			case InputConstants.KEY_DELETE, InputConstants.KEY_BACKSPACE -> {
				if (selected >= 0) {
					deleteSelected();
				}
				return true;
			}
			case InputConstants.KEY_LEFT -> {
				jumpTo(selected <= 0 ? 0 : selected - 1);
				return true;
			}
			case InputConstants.KEY_RIGHT -> {
				jumpTo(selected + 1);
				return true;
			}
			case InputConstants.KEY_HOME -> {
				director.preview(0);
				return true;
			}
			case InputConstants.KEY_END -> {
				if (shot != null) {
					director.preview(shot.duration());
				}
				return true;
			}
			default -> {
				return false;
			}
		}
	}

	@Override
	protected boolean onBackgroundClick(double mouseX, double mouseY, int button) {
		if (director.playing()) {
			director.pause();
			return true;
		}
		return false;
	}

	private void togglePlay() {
		if (director.playing()) {
			director.pause();
			return;
		}
		Shot shot = director.current();
		if (shot == null || shot.keyframes().size() < 2) {
			return;
		}
		director.play();
	}

	private void deleteSelected() {
		director.removeKeyframe(selected);
		Shot shot = director.current();
		select(shot == null || shot.keyframes().isEmpty() ? -1 : Math.min(selected, shot.keyframes().size() - 1));
	}

	private void addKeyframeHere() {
		int index = director.addKeyframeAtCamera(minecraft, module.spacing());
		select(index);
	}

	private void render() {
		boolean started = director.startRender(module.renderFps(), module.renderScale(), result -> minecraft.execute(() -> {
			Component body = result.failed()
				? Component.translatable("pretupid.cinematic.render_failed")
				: Component.translatable("pretupid.cinematic.render_done", result.frames(), result.folder().getFileName().toString());
			PretupidClient.get().notifications().push(Component.translatable("pretupid.cinematic.title"), body, Icon.item("spyglass"), null,
				result.failed() ? Theme.WARNING : Theme.SUCCESS, 5000L);
			if (!result.failed() && result.frames() > 0) {
				PlatformOpener.openFolder(result.folder());
			}
		}));
		if (!started) {
			PretupidClient.get().notifications().push(Component.translatable("pretupid.cinematic.title"), Component.translatable("pretupid.cinematic.render_failed"),
				Icon.item("spyglass"), null, Theme.WARNING, 4000L);
		}
	}

	@Override
	protected void drawContent(UiContext ui) {
		var g = ui.g();
		Shot shot = director.current();
		if (director.rendering()) {
			drawRenderProgress(ui);
			return;
		}
		boolean playing = director.playing();
		if (module.letterbox() && director.controlsCamera() && playing) {
			CinematicOverlay.drawLetterbox(g, width, height);
		}
		if (playing) {
			drawPlayingBar(ui, shot);
			return;
		}
		if (module.showsPath()) {
			CinematicOverlay.drawPath(g, font, shot, selected, director.playhead(), width, height);
		}
		int panelX = 6;
		int panelW = width - 12;
		int panelY = height - PANEL_HEIGHT - 6;
		Draw.shadow(g, panelX, panelY, panelW, PANEL_HEIGHT, 4, 0.8f);
		Draw.glass(g, panelX, panelY, panelW, PANEL_HEIGHT, Theme.POPUP, Theme.WINDOW_BORDER);
		ui.hit(panelX, panelY, panelW, PANEL_HEIGHT, new UiContext.Handler() {
			@Override
			public boolean click(double mouseX, double mouseY, int button) {
				return true;
			}

			@Override
			public boolean pointer() {
				return false;
			}

			@Override
			public boolean clickSound() {
				return false;
			}
		});
		int x = panelX + 8;
		int w = panelW - 16;
		drawShotRow(ui, shot, x, panelY + 7, w);
		drawTimeline(ui, shot, x, panelY + 28, w);
		int transportY = panelY + 28 + TIMELINE_HEIGHT + 6;
		drawTransport(ui, shot, x, transportY, w);
		drawShotOptions(ui, shot, x, transportY + 22, w);
		Draw.rect(g, x, transportY + 46, w, 1, Theme.DIVIDER);
		drawInspector(ui, shot, x, transportY + 52, w);
	}

	private void drawPlayingBar(UiContext ui, Shot shot) {
		if (shot == null || shot.duration() <= 0) {
			return;
		}
		var g = ui.g();
		int barW = Math.min(260, width - 40);
		int bx = (width - barW) / 2;
		int by = height - 14;
		Draw.rect(g, bx, by, barW, 3, 0x60000000);
		Draw.rect(g, bx, by, Math.round(barW * (float) Math.min(1.0, director.playhead() / shot.duration())), 3, Theme.accent());
	}

	private void drawRenderProgress(UiContext ui) {
		var g = ui.g();
		int boxW = Math.min(240, width - 24);
		int bx = (width - boxW) / 2;
		int by = 10;
		Draw.glass(g, bx, by, boxW, 40, Theme.POPUP, Theme.WINDOW_BORDER);
		Component label = Component.translatable("pretupid.cinematic.rendering", Math.round(director.renderProgress() * 100));
		Draw.text(g, font, label, bx + 8, by + 8, Theme.TEXT);
		Draw.rect(g, bx + 8, by + 24, boxW - 80, 6, 0x60000000);
		Draw.rect(g, bx + 8, by + 24, Math.round((boxW - 80) * director.renderProgress()), 6, Theme.accent());
		Widgets.button(ui, "cinematic.render.cancel", bx + boxW - 66, by + 18, 58, 16, Component.translatable("pretupid.ui.cancel"), Widgets.ButtonStyle.DANGER,
			director::cancelRender);
	}

	private void drawShotRow(UiContext ui, Shot shot, int x, int y, int w) {
		var g = ui.g();
		List<Shot> shots = director.shots();
		Widgets.iconButton(ui, "cinematic.prev", x, y, 16, 16, UiIcon.CHEVRON_LEFT, () -> {
			director.select(director.selectedIndex() - 1);
			select(0);
		});
		Component name = shot == null ? Component.translatable("pretupid.cinematic.no_shot") : Component.literal(shot.name());
		Component count = shots.isEmpty() ? Component.empty() : Component.literal((director.selectedIndex() + 1) + "/" + shots.size());
		int nameW = Math.clamp(w / 5, 70, 130);
		Draw.chamfer(g, x + 20, y, nameW, 16, Theme.SURFACE);
		Draw.text(g, font, Draw.ellipsize(font, name, nameW - 12 - font.width(count)), x + 25, y + 4, Theme.TEXT);
		Draw.text(g, font, count, x + 20 + nameW - 5 - font.width(count), y + 4, Theme.TEXT_DIM);
		Widgets.iconButton(ui, "cinematic.next", x + 24 + nameW, y, 16, 16, UiIcon.CHEVRON_RIGHT, () -> {
			director.select(director.selectedIndex() + 1);
			select(0);
		});
		int bx = x + 44 + nameW;
		bx = textButton(ui, "cinematic.new", bx, y, Component.translatable("pretupid.cinematic.new_shot"), Widgets.ButtonStyle.SECONDARY, () -> {
			director.newShot();
			select(-1);
		});
		bx = textButton(ui, "cinematic.templates", bx, y, Component.translatable("pretupid.cinematic.templates"), Widgets.ButtonStyle.SECONDARY, this::openTemplates);
		boolean canRender = shot != null && shot.keyframes().size() >= 2;
		Component renderLabel = Component.translatable("pretupid.cinematic.render", module.renderFps());
		int renderW = font.width(renderLabel) + 14;
		if (shot != null) {
			Component deleteLabel = Component.translatable("pretupid.cinematic.delete_shot");
			boolean fits = bx + font.width(deleteLabel) + 12 <= x + w - renderW - 6;
			Runnable delete = () -> {
				director.deleteShot();
				select(0);
			};
			if (fits) {
				textButton(ui, "cinematic.delete_shot", bx, y, deleteLabel, Widgets.ButtonStyle.DANGER, delete);
			} else {
				Widgets.button(ui, "cinematic.delete_shot", bx, y, 16, 16, Component.literal("×"), Widgets.ButtonStyle.DANGER, delete);
				if (ui.hovered(bx, y, 16, 16)) {
					ui.tooltip(deleteLabel);
				}
			}
		}
		Widgets.button(ui, "cinematic.render", x + w - renderW, y, renderW, 16, renderLabel, canRender ? Widgets.ButtonStyle.PRIMARY : Widgets.ButtonStyle.GHOST, () -> {
			if (canRender) {
				render();
			}
		});
		if (canRender && ui.hovered(x + w - renderW, y, renderW, 16)) {
			ui.tooltip(Component.translatable("pretupid.cinematic.render_tooltip", module.renderFps(), module.renderScale()));
		}
	}

	private int textButton(UiContext ui, String id, int x, int y, Component label, Widgets.ButtonStyle style, Runnable action) {
		int w = font.width(label) + 12;
		Widgets.button(ui, id, x, y, w, 16, label, style, action);
		return x + w + 4;
	}

	private void openTemplates() {
		openPopup(new Popup() {
			@Override
			public void draw(UiContext ui, int screenWidth, int screenHeight) {
				var g = ui.g();
				ShotTemplate[] templates = ShotTemplate.values();
				int popupW = 150;
				int popupH = 22 + templates.length * 20;
				int px = Math.max(6, screenWidth / 2 - popupW / 2);
				int py = Math.max(6, screenHeight - PANEL_HEIGHT - 12 - popupH);
				Draw.shadow(g, px, py, popupW, popupH, 4, 0.8f);
				Draw.glass(g, px, py, popupW, popupH, Theme.POPUP, Theme.WINDOW_BORDER);
				ui.hit(px, py, popupW, popupH, UiContext.Handler.onClick(() -> {
				}));
				Draw.text(g, font, Component.translatable("pretupid.cinematic.templates"), px + 8, py + 7, Theme.TEXT_MUTED);
				for (int i = 0; i < templates.length; i++) {
					ShotTemplate template = templates[i];
					Widgets.button(ui, "cinematic.template." + template, px + 6, py + 20 + i * 20, popupW - 12, 18,
						Component.translatable("pretupid.cinematic.template." + template.name().toLowerCase(Locale.ROOT)), Widgets.ButtonStyle.SECONDARY, () -> {
							director.addTemplate(template, minecraft);
							closePopup();
							select(0);
							director.preview(0);
						});
				}
			}
		});
	}

	private int timeToX(double seconds, int x, int w) {
		return x + (int) Math.round(seconds / timelineSeconds * w);
	}

	private double xToTime(double mouseX, int x, int w) {
		double raw = (mouseX - x) / w * timelineSeconds;
		return Math.max(0, Math.round(raw / SNAP) * SNAP);
	}

	private void drawTimeline(UiContext ui, Shot shot, int x, int y, int w) {
		var g = ui.g();
		double duration = shot == null ? 0 : shot.duration();
		timelineSeconds = Math.max(5.0, Math.ceil((duration + 2.0) / 5.0) * 5.0);
		Draw.chamfer(g, x, y, w, TIMELINE_HEIGHT, 0x70000000);
		Draw.border(g, x, y, w, TIMELINE_HEIGHT, Theme.SURFACE_BORDER);
		Draw.rect(g, x + 1, y + 1, w - 2, 10, 0x30FFFFFF);
		int step = timelineSeconds > 60 ? 10 : timelineSeconds > 20 ? 5 : 1;
		for (int second = 0; second <= timelineSeconds; second += step) {
			int tx = timeToX(second, x, w);
			boolean major = second % (step * 5) == 0;
			Draw.rect(g, tx, y + 1, 1, major ? 10 : 5, major ? 0x90FFFFFF : 0x50FFFFFF);
			if (second % (step * (timelineSeconds > 20 ? 2 : 1)) == 0 && tx + 14 < x + w) {
				Draw.scaled(g, font, Component.literal(second + "s"), tx + 2, y + 3, 0.6f, Theme.TEXT_DIM, false);
			}
		}
		int track = y + 12 + (TIMELINE_HEIGHT - 12) / 2;
		if (shot != null && duration > 0) {
			Draw.rect(g, x + 1, track - 4, timeToX(duration, x, w) - x, 8, Colors.withAlpha(Theme.accent(), 45));
			List<CameraKeyframe> keyframes = shot.keyframes();
			for (int i = 0; i + 1 < keyframes.size(); i++) {
				if (keyframes.get(i).ease() == CameraKeyframe.Ease.HOLD) {
					int from = timeToX(keyframes.get(i).time(), x, w);
					Draw.rect(g, from, track - 4, timeToX(keyframes.get(i + 1).time(), x, w) - from, 8, 0x40FACC15);
				}
			}
		}
		ui.interactive(x, y, w, TIMELINE_HEIGHT, new UiContext.Handler() {
			@Override
			public boolean click(double mouseX, double mouseY, int button) {
				if (button != 0 || shot == null || shot.keyframes().isEmpty()) {
					return false;
				}
				director.preview(xToTime(mouseX, x, w));
				return true;
			}

			@Override
			public void drag(double mouseX, double mouseY) {
				director.preview(xToTime(mouseX, x, w));
			}

			@Override
			public boolean clickSound() {
				return false;
			}
		});
		if (shot == null) {
			return;
		}
		List<CameraKeyframe> keyframes = shot.keyframes();
		for (int i = 0; i < keyframes.size(); i++) {
			int index = i;
			int kx = timeToX(keyframes.get(i).time(), x, w);
			boolean active = i == selected;
			boolean hover = ui.interactive(kx - 6, track - 7, 13, 14, new UiContext.Handler() {
				@Override
				public boolean click(double mouseX, double mouseY, int button) {
					if (button == 1) {
						select(index);
						deleteSelected();
						return true;
					}
					if (button != 0) {
						return false;
					}
					select(index);
					director.preview(keyframes.get(index).time());
					return true;
				}

				@Override
				public void drag(double mouseX, double mouseY) {
					if (selected < 0) {
						return;
					}
					time.set(Math.min(300, xToTime(mouseX, x, w)));
					Shot current = director.current();
					if (current != null && selected < current.keyframes().size()) {
						director.preview(current.keyframes().get(selected).time());
					}
				}
			});
			diamond(g, kx, track, active ? 6 : 5, active ? 0xFFFACC15 : hover ? Colors.brighten(Theme.accent(), 0.25f) : Theme.accent());
			if (hover) {
				ui.tooltip(Component.translatable("pretupid.cinematic.keyframe_tooltip", i + 1, String.format(Locale.ROOT, "%.2f", keyframes.get(i).time())));
			}
		}
		if (!keyframes.isEmpty()) {
			int px = timeToX(director.playhead(), x, w);
			Draw.rect(g, px, y - 2, 1, TIMELINE_HEIGHT + 4, 0xFFFFFFFF);
			Draw.rect(g, px - 3, y - 3, 7, 4, 0xFFFFFFFF);
		}
	}

	private static void diamond(Gfx g, int cx, int cy, int radius, int color) {
		for (int dy = -radius; dy <= radius; dy++) {
			int half = radius - Math.abs(dy);
			Draw.rect(g, cx - half - 1, cy + dy, half * 2 + 3, 1, 0xA0000000);
		}
		for (int dy = -radius + 1; dy <= radius - 1; dy++) {
			int half = radius - 1 - Math.abs(dy);
			Draw.rect(g, cx - half, cy + dy, half * 2 + 1, 1, color);
		}
	}

	private void drawTransport(UiContext ui, Shot shot, int x, int y, int w) {
		var g = ui.g();
		boolean canPlay = shot != null && shot.keyframes().size() >= 2;
		int bx = x;
		Widgets.button(ui, "cinematic.start", bx, y, 20, 18, Component.literal("|◀"), Widgets.ButtonStyle.GHOST, () -> director.preview(0));
		bx += 22;
		Widgets.button(ui, "cinematic.prev_key", bx, y, 20, 18, Component.literal("◀"), Widgets.ButtonStyle.GHOST, () -> jumpTo(selected <= 0 ? 0 : selected - 1));
		bx += 22;
		Widgets.button(ui, "cinematic.play", bx, y, 52, 18, Component.translatable(director.playing() ? "pretupid.cinematic.pause" : "pretupid.cinematic.play"),
			canPlay ? Widgets.ButtonStyle.PRIMARY : Widgets.ButtonStyle.GHOST, this::togglePlay);
		bx += 54;
		Widgets.button(ui, "cinematic.next_key", bx, y, 20, 18, Component.literal("▶"), Widgets.ButtonStyle.GHOST, () -> jumpTo(selected + 1));
		bx += 22;
		Widgets.button(ui, "cinematic.end", bx, y, 20, 18, Component.literal("▶|"), Widgets.ButtonStyle.GHOST, () -> {
			if (shot != null) {
				director.preview(shot.duration());
			}
		});
		bx += 26;
		double duration = shot == null ? 0 : shot.duration();
		String clock = String.format(Locale.ROOT, "%.2f / %.2fs", director.state() == CinematicDirector.State.IDLE ? 0.0 : director.playhead(), duration);
		Draw.text(g, font, clock, bx, y + 5, Theme.TEXT);
		int right = x + w;
		Component flyLabel = Component.translatable("pretupid.cinematic.fly");
		int flyW = font.width(flyLabel) + 14;
		Widgets.button(ui, "cinematic.fly", right - flyW, y, flyW, 18, flyLabel, Widgets.ButtonStyle.SECONDARY, this::fly);
		Component addLabel = Component.translatable("pretupid.cinematic.add_here");
		int addW = font.width(addLabel) + 14;
		Widgets.button(ui, "cinematic.add", right - flyW - 4 - addW, y, addW, 18, addLabel, Widgets.ButtonStyle.PRIMARY, this::addKeyframeHere);
	}

	private void drawShotOptions(UiContext ui, Shot shot, int x, int y, int w) {
		if (shot == null) {
			return;
		}
		Draw.text(ui.g(), font, Component.translatable("pretupid.cinematic.speed"), x, y + 5, Theme.TEXT_MUTED);
		int bx = x + font.width(Component.translatable("pretupid.cinematic.speed")) + 6;
		Component speed = Component.literal(formatSpeed(shot.speed()) + "×");
		Widgets.button(ui, "cinematic.speed", bx, y, 40, 18, speed, Widgets.ButtonStyle.SECONDARY, () -> {
			int current = 0;
			for (int i = 0; i < SPEEDS.length; i++) {
				if (Math.abs(SPEEDS[i] - shot.speed()) < 1e-3) {
					current = i;
				}
			}
			shot.setSpeed(SPEEDS[(current + 1) % SPEEDS.length]);
			director.markDirty();
		});
		bx += 48;
		bx = chip(ui, "cinematic.smooth", bx, y, Component.translatable("pretupid.cinematic.smooth"), shot.smooth(), () -> {
			shot.setSmooth(!shot.smooth());
			director.markDirty();
		});
		bx = chip(ui, "cinematic.loop", bx, y, Component.translatable("pretupid.cinematic.loop"), shot.loop(), () -> {
			shot.setLoop(!shot.loop());
			director.markDirty();
		});
		chip(ui, "cinematic.look", bx, y, Component.translatable("pretupid.cinematic.look_at_me"), shot.lookAtPlayer(), () -> {
			shot.setLookAtPlayer(!shot.lookAtPlayer());
			director.markDirty();
		});
	}

	private int chip(UiContext ui, String id, int x, int y, Component label, boolean on, Runnable action) {
		int w = font.width(label) + 12;
		Widgets.button(ui, id, x, y, w, 18, label, on ? Widgets.ButtonStyle.PRIMARY : Widgets.ButtonStyle.GHOST, action);
		return x + w + 4;
	}

	private static String formatSpeed(double speed) {
		return speed == Math.rint(speed) ? String.valueOf((int) speed) : String.valueOf(speed);
	}

	private void fly() {
		FreecamModule freecam = PretupidClient.get().modules().get(FreecamModule.class);
		if (!freecam.enabledSetting().get()) {
			freecam.setEnabled(true);
		}
		director.stop();
		Platform.setScreen(null);
		if (!freecam.isEnabled()) {
			freecam.explainUnavailable(minecraft);
			return;
		}
		if (!freecam.active()) {
			freecam.toggleActive(minecraft);
		}
	}

	private void drawInspector(UiContext ui, Shot shot, int x, int y, int w) {
		var g = ui.g();
		if (shot == null || selected < 0 || selected >= shot.keyframes().size()) {
			Component key = PretupidClient.get().keybinds().cinematicKeyframe().getTranslatedKeyMessage();
			Component text = shot == null || shot.keyframes().isEmpty()
				? Component.translatable("pretupid.cinematic.inspector_empty", key)
				: Component.translatable("pretupid.cinematic.inspector_pick");
			Draw.text(g, font, Draw.ellipsize(font, text, w), x, y + 6, Theme.TEXT_DIM);
			return;
		}
		CameraKeyframe keyframe = shot.keyframes().get(selected);
		Draw.text(g, font, Component.translatable("pretupid.cinematic.keyframe", selected + 1), x, y + 5, 0xFFFACC15);
		int bx = x + font.width(Component.translatable("pretupid.cinematic.keyframe", selected + 1)) + 10;
		Component ease = Component.translatable("pretupid.cinematic.ease." + keyframe.ease().name().toLowerCase(Locale.ROOT));
		int easeW = Math.max(70, font.width(ease) + 14);
		Widgets.button(ui, "cinematic.ease", bx, y + 1, easeW, 16, ease, Widgets.ButtonStyle.SECONDARY,
			() -> editSelected(current -> current.withEase(current.ease().next())));
		if (ui.hovered(bx, y + 1, easeW, 16)) {
			ui.tooltip(Component.translatable("pretupid.cinematic.ease"));
		}
		int right = x + w;
		Component deleteLabel = Component.translatable("pretupid.cinematic.delete");
		int deleteW = font.width(deleteLabel) + 12;
		Widgets.button(ui, "cinematic.delete_key", right - deleteW, y + 1, deleteW, 16, deleteLabel, Widgets.ButtonStyle.DANGER, this::deleteSelected);
		Component duplicateLabel = Component.translatable("pretupid.cinematic.duplicate");
		int duplicateW = font.width(duplicateLabel) + 12;
		int duplicateX = right - deleteW - 4 - duplicateW;
		Widgets.button(ui, "cinematic.duplicate", duplicateX, y + 1, duplicateW, 16, duplicateLabel, Widgets.ButtonStyle.SECONDARY, () -> {
			int index = director.duplicateKeyframe(selected);
			if (index >= 0) {
				select(index);
			}
		});
		Component viewLabel = Component.translatable("pretupid.cinematic.set_view");
		int viewW = font.width(viewLabel) + 12;
		Widgets.button(ui, "cinematic.set_view", duplicateX - 4 - viewW, y + 1, viewW, 16, viewLabel, Widgets.ButtonStyle.SECONDARY, () -> {
			var camera = Platform.camera();
			FreecamModule freecam = PretupidClient.get().modules().get(FreecamModule.class);
			if (director.controlsCamera() || camera == null) {
				return;
			}
			editSelected(current -> current.withView(freecam.active() ? freecam.position(1f) : Platform.cameraPosition(),
				freecam.active() ? freecam.yaw() : Platform.cameraYaw(camera), freecam.active() ? freecam.pitch() : Platform.cameraPitch(camera)));
		});
		int column = (w - 8) / 3;
		labeledSlider(ui, "cinematic.time", x, y + 24, column - 12, Component.translatable("pretupid.cinematic.time"), time);
		labeledSlider(ui, "cinematic.fov", x + column + 4, y + 24, column - 12, Component.translatable("pretupid.cinematic.fov"), fov);
		labeledSlider(ui, "cinematic.roll", x + 2 * (column + 4), y + 24, column - 12, Component.translatable("pretupid.cinematic.roll"), roll);
	}

	private void labeledSlider(UiContext ui, String id, int x, int y, int w, Component name, NumberSetting setting) {
		String value = setting.step() < 1 ? String.format(Locale.ROOT, "%.2f", setting.get()) : String.valueOf(Math.round(setting.get()));
		Component label = name.copy().append(" " + value + setting.unit());
		int labelWidth = Math.max(font.width(label), font.width(name.getString() + " 000.00s"));
		Draw.text(ui.g(), font, label, x, y + 2, Theme.TEXT_MUTED);
		Widgets.slider(ui, id, x + labelWidth + 4, y, Math.max(30, w - labelWidth - 4), setting);
	}
}

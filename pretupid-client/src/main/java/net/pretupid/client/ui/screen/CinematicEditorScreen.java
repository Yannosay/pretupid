package net.pretupid.client.ui.screen;

import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.cinematic.CameraKeyframe;
import net.pretupid.client.cinematic.CinematicDirector;
import net.pretupid.client.cinematic.CinematicOverlay;
import net.pretupid.client.cinematic.Shot;
import net.pretupid.client.module.visual.CinematicModule;
import net.pretupid.client.module.visual.FreecamModule;
import net.pretupid.client.setting.NumberSetting;
import net.pretupid.client.ui.Draw;
import net.pretupid.client.ui.PretupidScreen;
import net.pretupid.client.ui.Theme;
import net.pretupid.client.ui.UiContext;
import net.pretupid.client.ui.UiIcon;
import net.pretupid.client.ui.Widgets;
import net.pretupid.client.util.Colors;

public final class CinematicEditorScreen extends PretupidScreen {
	private static final int PANEL_HEIGHT = 146;
	private static final int TIMELINE_HEIGHT = 34;
	private static final double SNAP = 0.05;
	private static final long HINT_MS = 2500L;

	private final CinematicDirector director = PretupidClient.get().cinematic();
	private final NumberSetting fov = new NumberSetting("fov", 70, CameraKeyframe.MIN_FOV, CameraKeyframe.MAX_FOV, 1, "°");
	private final NumberSetting roll = new NumberSetting("roll", 0, -180, 180, 1, "°");
	private final NumberSetting time = new NumberSetting("time", 0, 0, 120, 0.05, "s");
	private int selected = -1;
	private boolean syncing;
	private long playStartedAt;
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

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
	}

	@Override
	public void removed() {
		director.stop();
		super.removed();
	}

	@Override
	protected boolean handleKey(KeyEvent event) {
		if (director.playing()) {
			director.pause();
			return true;
		}
		if (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_SPACE) {
			togglePlay();
			return true;
		}
		if ((event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_DELETE) && selected >= 0) {
			deleteSelected();
			return true;
		}
		return false;
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
		playStartedAt = System.currentTimeMillis();
	}

	private void deleteSelected() {
		director.removeKeyframe(selected);
		Shot shot = director.current();
		select(shot == null || shot.keyframes().isEmpty() ? -1 : Math.min(selected, shot.keyframes().size() - 1));
	}

	@Override
	protected void drawContent(UiContext ui) {
		var g = ui.g();
		CinematicModule module = PretupidClient.get().modules().get(CinematicModule.class);
		Shot shot = director.current();
		boolean playing = director.playing();
		if (module.letterbox() && director.controlsCamera() && playing) {
			CinematicOverlay.drawLetterbox(g, width, height);
		}
		if (playing) {
			long shown = System.currentTimeMillis() - playStartedAt;
			if (shown < HINT_MS) {
				int alpha = (int) Math.round(255 * Math.min(1.0, (HINT_MS - shown) / 600.0));
				Component hint = Component.translatable("pretupid.cinematic.playing_hint");
				Draw.centered(g, font, hint, width / 2, height - 18, Colors.withAlpha(0xFFFFFF, Math.max(8, alpha)));
			}
			return;
		}
		if (module.showsPath()) {
			CinematicOverlay.drawPath(g, font, shot, selected, director.playhead(), width, height);
		}
		drawHint(ui, shot);
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
		drawTimeline(ui, shot, x, panelY + 29, w);
		drawTransport(ui, shot, x, panelY + 29 + TIMELINE_HEIGHT + 6, w);
		drawInspector(ui, shot, x, panelY + 29 + TIMELINE_HEIGHT + 30, w);
	}

	private void drawHint(UiContext ui, Shot shot) {
		Component key = PretupidClient.get().keybinds().cinematicKeyframe().getTranslatedKeyMessage();
		Component freecam = PretupidClient.get().keybinds().freecam().getTranslatedKeyMessage();
		Component text = shot == null || shot.keyframes().isEmpty()
			? Component.translatable("pretupid.cinematic.empty_hint", freecam, key)
			: Component.translatable("pretupid.cinematic.hint", freecam, key);
		int y = 8;
		for (var line : font.split(text, Math.min(320, width - 24))) {
			int lineWidth = font.width(line);
			Draw.rect(ui.g(), 8, y - 2, lineWidth + 8, 12, 0x90000000);
			Draw.text(ui.g(), font, line, 12, y, 0xFFE6EAF0, false);
			y += 12;
		}
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
		int nameW = Math.clamp(w - 340, 56, 140);
		Draw.chamfer(g, x + 20, y, nameW, 16, Theme.SURFACE);
		Draw.text(g, font, Draw.ellipsize(font, name, nameW - 34), x + 25, y + 4, Theme.TEXT);
		Draw.text(g, font, count, x + 20 + nameW - 5 - font.width(count), y + 4, Theme.TEXT_DIM);
		Widgets.iconButton(ui, "cinematic.next", x + 24 + nameW, y, 16, 16, UiIcon.CHEVRON_RIGHT, () -> {
			director.select(director.selectedIndex() + 1);
			select(0);
		});
		int bx = x + 44 + nameW;
		Widgets.button(ui, "cinematic.new", bx, y, 54, 16, Component.translatable("pretupid.cinematic.new_shot"), Widgets.ButtonStyle.SECONDARY, () -> {
			director.newShot();
			select(-1);
		});
		Widgets.button(ui, "cinematic.delete_shot", bx + 58, y, 54, 16, Component.translatable("pretupid.cinematic.delete_shot"),
			shot == null ? Widgets.ButtonStyle.GHOST : Widgets.ButtonStyle.DANGER, () -> {
				if (shot != null) {
					director.deleteShot();
					select(0);
				}
			});
		int right = x + w;
		if (shot != null) {
			right = toggleLabel(ui, "cinematic.loop", right, y, Component.translatable("pretupid.cinematic.loop"), shot.loop(), () -> {
				shot.setLoop(!shot.loop());
				director.markDirty();
			});
			right = toggleLabel(ui, "cinematic.smooth", right - 10, y, Component.translatable("pretupid.cinematic.smooth"), shot.smooth(), () -> {
				shot.setSmooth(!shot.smooth());
				director.markDirty();
			});
		}
	}

	private int toggleLabel(UiContext ui, String id, int right, int y, Component label, boolean value, Runnable action) {
		int toggleX = right - 20;
		Widgets.toggle(ui, id, toggleX, y + 3, value, action);
		int labelX = toggleX - 4 - font.width(label);
		Draw.text(ui.g(), font, label, labelX, y + 4, Theme.TEXT_MUTED);
		return labelX;
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
		Draw.chamfer(g, x, y, w, TIMELINE_HEIGHT, 0x60000000);
		Draw.border(g, x, y, w, TIMELINE_HEIGHT, Theme.SURFACE_BORDER);
		int step = timelineSeconds > 60 ? 10 : timelineSeconds > 20 ? 5 : 1;
		for (int second = 0; second <= timelineSeconds; second += step) {
			int tx = timeToX(second, x, w);
			Draw.rect(g, tx, y + 1, 1, second % (step * 5) == 0 ? 6 : 4, 0x50FFFFFF);
			if (second % (step * (timelineSeconds > 20 ? 2 : 1)) == 0 && tx + 14 < x + w) {
				Draw.scaled(g, font, Component.literal(second + "s"), tx + 2, y + 2, 0.6f, Theme.TEXT_DIM, false);
			}
		}
		if (shot != null && duration > 0) {
			Draw.rect(g, x + 1, y + TIMELINE_HEIGHT / 2, timeToX(duration, x, w) - x, 1, Colors.withAlpha(Theme.accent(), 120));
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
		if (shot != null) {
			List<CameraKeyframe> keyframes = shot.keyframes();
			for (int i = 0; i < keyframes.size(); i++) {
				int index = i;
				int kx = timeToX(keyframes.get(i).time(), x, w);
				int ky = y + TIMELINE_HEIGHT / 2;
				boolean active = i == selected;
				boolean hover = ui.interactive(kx - 5, ky - 6, 11, 12, new UiContext.Handler() {
					private boolean moved;

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
						moved = false;
						director.preview(keyframes.get(index).time());
						return true;
					}

					@Override
					public void drag(double mouseX, double mouseY) {
						if (selected < 0) {
							return;
						}
						moved = true;
						time.set(Math.min(120, xToTime(mouseX, x, w)));
						Shot current = director.current();
						if (current != null && selected < current.keyframes().size()) {
							director.preview(current.keyframes().get(selected).time());
						}
					}
				});
				diamond(g, kx, ky, active ? 5 : 4, active ? 0xFFFACC15 : hover ? Colors.brighten(Theme.accent(), 0.25f) : Theme.accent());
				if (hover) {
					ui.tooltip(Component.translatable("pretupid.cinematic.keyframe_tooltip", i + 1, String.format(Locale.ROOT, "%.2f", keyframes.get(i).time())));
				}
			}
			if (!keyframes.isEmpty()) {
				int px = timeToX(director.playhead(), x, w);
				Draw.rect(g, px, y - 2, 1, TIMELINE_HEIGHT + 4, 0xFFFFFFFF);
				Draw.rect(g, px - 2, y - 3, 5, 3, 0xFFFFFFFF);
			}
		}
	}

	private static void diamond(GuiGraphicsExtractor g, int cx, int cy, int radius, int color) {
		Draw.rect(g, cx - radius - 1, cy, 1, 1, 0xA0000000);
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
		Widgets.button(ui, "cinematic.start", x, y, 20, 18, Component.literal("|◀"), Widgets.ButtonStyle.GHOST, () -> director.preview(0));
		Widgets.button(ui, "cinematic.play", x + 24, y, 56, 18, Component.translatable(canPlay ? "pretupid.cinematic.play" : "pretupid.cinematic.need_two"),
			canPlay ? Widgets.ButtonStyle.PRIMARY : Widgets.ButtonStyle.GHOST, this::togglePlay);
		Widgets.button(ui, "cinematic.stop", x + 84, y, 40, 18, Component.translatable("pretupid.cinematic.stop"), Widgets.ButtonStyle.SECONDARY, director::stop);
		double duration = shot == null ? 0 : shot.duration();
		String clock = String.format(Locale.ROOT, "%.2f / %.2fs", director.state() == CinematicDirector.State.IDLE ? 0.0 : director.playhead(), duration);
		Draw.text(g, font, clock, x + 130, y + 5, Theme.TEXT_MUTED);
		int right = x + w;
		Widgets.button(ui, "cinematic.fly", right - 64, y, 64, 18, Component.translatable("pretupid.cinematic.fly"), Widgets.ButtonStyle.SECONDARY, this::fly);
		Widgets.button(ui, "cinematic.add", right - 64 - 4 - 92, y, 92, 18, Component.translatable("pretupid.cinematic.add_here"), Widgets.ButtonStyle.PRIMARY, () -> {
			int index = director.addKeyframeAtCamera(minecraft, PretupidClient.get().modules().get(CinematicModule.class).spacing());
			select(index);
		});
	}

	private void fly() {
		FreecamModule freecam = PretupidClient.get().modules().get(FreecamModule.class);
		if (!freecam.enabledSetting().get()) {
			freecam.setEnabled(true);
		}
		director.stop();
		minecraft.gui.setScreen(null);
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
			Component text = Component.translatable(shot == null || shot.keyframes().isEmpty() ? "pretupid.cinematic.inspector_empty" : "pretupid.cinematic.inspector_pick");
			Draw.text(g, font, Draw.ellipsize(font, text, w), x, y + 6, Theme.TEXT_DIM);
			return;
		}
		CameraKeyframe keyframe = shot.keyframes().get(selected);
		Draw.text(g, font, Component.translatable("pretupid.cinematic.keyframe", selected + 1), x, y + 5, 0xFFFACC15);
		int column = (w - 8) / 3;
		labeledSlider(ui, "cinematic.time", x, y + 22, column - 12, Component.translatable("pretupid.cinematic.time"), time);
		labeledSlider(ui, "cinematic.fov", x + column + 4, y + 22, column - 12, Component.translatable("pretupid.cinematic.fov"), fov);
		labeledSlider(ui, "cinematic.roll", x + 2 * (column + 4), y + 22, column - 12, Component.translatable("pretupid.cinematic.roll"), roll);
		int right = x + w;
		Widgets.button(ui, "cinematic.delete_key", right - 46, y + 1, 46, 16, Component.translatable("pretupid.cinematic.delete"), Widgets.ButtonStyle.DANGER, this::deleteSelected);
		Widgets.button(ui, "cinematic.set_view", right - 46 - 4 - 70, y + 1, 70, 16, Component.translatable("pretupid.cinematic.set_view"), Widgets.ButtonStyle.SECONDARY, () -> {
			var camera = minecraft.gameRenderer.mainCamera();
			FreecamModule freecam = PretupidClient.get().modules().get(FreecamModule.class);
			if (director.controlsCamera() || camera == null) {
				return;
			}
			editSelected(current -> current.withView(freecam.active() ? freecam.position(1f) : camera.position(),
				freecam.active() ? freecam.yaw() : camera.yRot(), freecam.active() ? freecam.pitch() : camera.xRot()));
		});
		Widgets.button(ui, "cinematic.ease", right - 46 - 4 - 70 - 4 - 56, y + 1, 56, 16,
			Component.translatable(keyframe.linear() ? "pretupid.cinematic.linear" : "pretupid.cinematic.eased"), Widgets.ButtonStyle.GHOST,
			() -> editSelected(current -> current.withLinear(!current.linear())));
	}

	private void labeledSlider(UiContext ui, String id, int x, int y, int w, Component name, NumberSetting setting) {
		String value = setting.step() < 1 ? String.format(Locale.ROOT, "%.2f", setting.get()) : String.valueOf(Math.round(setting.get()));
		Component label = name.copy().append(" " + value + setting.unit());
		int labelWidth = Math.max(font.width(label), font.width(name.getString() + " 000.00s"));
		Draw.text(ui.g(), font, label, x, y + 2, Theme.TEXT_MUTED);
		Widgets.slider(ui, id, x + labelWidth + 4, y, Math.max(30, w - labelWidth - 4), setting);
	}
}

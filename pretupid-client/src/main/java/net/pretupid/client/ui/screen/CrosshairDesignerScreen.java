package net.pretupid.client.ui.screen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.module.visual.CrosshairModule;
import net.pretupid.client.setting.Setting;
import net.pretupid.client.ui.Draw;
import net.pretupid.client.ui.PretupidScreen;
import net.pretupid.client.ui.ScrollState;
import net.pretupid.client.ui.Theme;
import net.pretupid.client.ui.UiContext;
import net.pretupid.client.ui.Widgets;
import net.pretupid.client.util.Colors;

public final class CrosshairDesignerScreen extends PretupidScreen {
	private enum Backdrop {
		SKY(0xFF7FB2F0, 0xFFB9D6F7),
		GRASS(0xFF4C8A2E, 0xFF6DB342),
		STONE(0xFF6B6B6B, 0xFF8A8A8A),
		NIGHT(0xFF0B1020, 0xFF1C2440),
		SNOW(0xFFE8F0F6, 0xFFFFFFFF);

		private final int top;
		private final int bottom;

		Backdrop(int top, int bottom) {
			this.top = top;
			this.bottom = bottom;
		}
	}

	private static final int CELL = 9;

	private final CrosshairModule module;
	private final ScrollState scroll = new ScrollState();
	private boolean[] cells;
	private Backdrop backdrop = Backdrop.SKY;
	private boolean mirrorX = true;
	private boolean mirrorY = true;
	private boolean hoverTarget;
	private Boolean paintValue;
	private final List<boolean[]> history = new ArrayList<>();

	public CrosshairDesignerScreen(Screen parent) {
		super(Component.translatable("pretupid.crosshair.title"), parent);
		this.module = PretupidClient.get().modules().get(CrosshairModule.class);
		this.cells = CrosshairModule.decode(module.pattern.get());
	}

	private void commit() {
		module.pattern.set(CrosshairModule.encode(cells));
		if (module.shape.get() != CrosshairModule.Shape.CUSTOM) {
			module.shape.set(CrosshairModule.Shape.CUSTOM);
		}
		if (!module.isEnabled()) {
			module.setEnabled(true);
		}
	}

	private void remember() {
		history.add(cells.clone());
		while (history.size() > 50) {
			history.removeFirst();
		}
	}

	private void paint(int row, int column, boolean value) {
		int grid = CrosshairModule.GRID;
		set(row, column, value);
		if (mirrorX) {
			set(row, grid - 1 - column, value);
		}
		if (mirrorY) {
			set(grid - 1 - row, column, value);
		}
		if (mirrorX && mirrorY) {
			set(grid - 1 - row, grid - 1 - column, value);
		}
		commit();
	}

	private void set(int row, int column, boolean value) {
		cells[row * CrosshairModule.GRID + column] = value;
	}

	@Override
	protected void drawContent(UiContext ui) {
		var g = ui.g();
		int w = Math.min(460, width - 12);
		int h = Math.min(290, height - 12);
		int x = (width - w) / 2;
		int y = (height - h) / 2;
		Draw.shadow(g, x, y, w, h, 6, 1f);
		Draw.glass(g, x, y, w, h, Theme.WINDOW, Theme.WINDOW_BORDER);
		Draw.textShadow(g, font, title, x + 10, y + 8, Theme.TEXT);
		Draw.text(g, font, Draw.ellipsize(font, Component.translatable("pretupid.crosshair.hint"), w - 120), x + 10, y + 19, Theme.TEXT_DIM);
		Widgets.button(ui, "crosshair.done", x + w - 60, y + 6, 52, 16, Component.translatable("pretupid.ui.done"), Widgets.ButtonStyle.PRIMARY, this::onClose);
		int grid = CrosshairModule.GRID;
		int gridSize = grid * CELL;
		int gridX = x + 10;
		int gridY = y + 34;
		drawGrid(ui, gridX, gridY, gridSize);
		int toolsY = gridY + gridSize + 6;
		int toolWidth = (gridSize - 4) / 3;
		Widgets.button(ui, "crosshair.mirror_x", gridX, toolsY, toolWidth, 16, Component.translatable(mirrorX ? "pretupid.crosshair.mirror_x_on" : "pretupid.crosshair.mirror_x_off"),
			mirrorX ? Widgets.ButtonStyle.PRIMARY : Widgets.ButtonStyle.GHOST, () -> mirrorX = !mirrorX);
		Widgets.button(ui, "crosshair.mirror_y", gridX + toolWidth + 2, toolsY, toolWidth, 16, Component.translatable(mirrorY ? "pretupid.crosshair.mirror_y_on" : "pretupid.crosshair.mirror_y_off"),
			mirrorY ? Widgets.ButtonStyle.PRIMARY : Widgets.ButtonStyle.GHOST, () -> mirrorY = !mirrorY);
		Widgets.button(ui, "crosshair.clear", gridX + (toolWidth + 2) * 2, toolsY, toolWidth, 16, Component.translatable("pretupid.crosshair.clear"), Widgets.ButtonStyle.DANGER, () -> {
			remember();
			cells = new boolean[grid * grid];
			commit();
		});
		int toolsY2 = toolsY + 18;
		Widgets.button(ui, "crosshair.invert", gridX, toolsY2, toolWidth, 16, Component.translatable("pretupid.crosshair.invert"), Widgets.ButtonStyle.SECONDARY, () -> {
			remember();
			for (int i = 0; i < cells.length; i++) {
				cells[i] = !cells[i];
			}
			commit();
		});
		Widgets.button(ui, "crosshair.undo", gridX + toolWidth + 2, toolsY2, toolWidth, 16, Component.translatable("pretupid.crosshair.undo"),
			history.isEmpty() ? Widgets.ButtonStyle.GHOST : Widgets.ButtonStyle.SECONDARY, this::undo);
		Widgets.button(ui, "crosshair.preset", gridX + (toolWidth + 2) * 2, toolsY2, toolWidth, 16, Component.translatable("pretupid.crosshair.preset"), Widgets.ButtonStyle.SECONDARY,
			this::nextPreset);
		int previewX = gridX + gridSize + 10;
		int previewW = 120;
		drawPreview(ui, previewX, gridY, previewW, gridSize);
		int backdropY = gridY + gridSize + 6;
		int swatch = (previewW - 4 * 2) / 5;
		Backdrop[] backdrops = Backdrop.values();
		for (int i = 0; i < backdrops.length; i++) {
			Backdrop entry = backdrops[i];
			int sx = previewX + i * (swatch + 2);
			boolean hover = ui.interactive(sx, backdropY, swatch, 14, UiContext.Handler.onClick(() -> backdrop = entry));
			Draw.verticalGradient(g, sx, backdropY, swatch, 14, entry.top, entry.bottom);
			Draw.border(g, sx, backdropY, swatch, 14, entry == backdrop ? Theme.accent() : hover ? 0xFFFFFFFF : Theme.SURFACE_BORDER);
		}
		boolean targetHover = ui.interactive(previewX, backdropY + 18, previewW, 16, UiContext.Handler.onClick(() -> hoverTarget = !hoverTarget));
		Draw.chamfer(g, previewX, backdropY + 18, previewW, 16, targetHover ? Theme.SURFACE_HOVER : Theme.SURFACE);
		Draw.centered(g, font, Component.translatable(hoverTarget ? "pretupid.crosshair.target_on" : "pretupid.crosshair.target_off"), previewX + previewW / 2, backdropY + 22, Theme.TEXT_MUTED);
		int settingsX = previewX + previewW + 10;
		int settingsW = x + w - 8 - settingsX;
		drawSettings(ui, settingsX, gridY, settingsW, y + h - 8 - gridY);
	}

	private void drawGrid(UiContext ui, int gridX, int gridY, int gridSize) {
		var g = ui.g();
		int grid = CrosshairModule.GRID;
		Draw.rect(g, gridX - 1, gridY - 1, gridSize + 2, gridSize + 2, 0xFF000000);
		for (int row = 0; row < grid; row++) {
			for (int column = 0; column < grid; column++) {
				int cx = gridX + column * CELL;
				int cy = gridY + row * CELL;
				boolean center = row == grid / 2 || column == grid / 2;
				int base = (row + column) % 2 == 0 ? 0xFF1C2230 : 0xFF161B26;
				Draw.rect(g, cx, cy, CELL, CELL, center ? Colors.lerp(base, 0xFF2A3348, 0.5f) : base);
				if (cells[row * grid + column]) {
					Draw.rect(g, cx + 1, cy + 1, CELL - 2, CELL - 2, Colors.withAlpha(module.color.resolve(), 255));
				}
			}
		}
		ui.hit(gridX, gridY, gridSize, gridSize, new UiContext.Handler() {
			@Override
			public boolean click(double mouseX, double mouseY, int button) {
				int column = (int) ((mouseX - gridX) / CELL);
				int row = (int) ((mouseY - gridY) / CELL);
				if (row < 0 || column < 0 || row >= grid || column >= grid) {
					return false;
				}
				remember();
				paintValue = button == 0 ? !cells[row * grid + column] : Boolean.FALSE;
				paint(row, column, paintValue);
				return true;
			}

			@Override
			public void drag(double mouseX, double mouseY) {
				int column = (int) Math.floor((mouseX - gridX) / CELL);
				int row = (int) Math.floor((mouseY - gridY) / CELL);
				if (paintValue != null && row >= 0 && column >= 0 && row < grid && column < grid) {
					paint(row, column, paintValue);
				}
			}

			@Override
			public void release(double mouseX, double mouseY) {
				paintValue = null;
			}

			@Override
			public boolean clickSound() {
				return false;
			}
		});
		if (ui.hovered(gridX, gridY, gridSize, gridSize)) {
			int column = (ui.mouseX() - gridX) / CELL;
			int row = (ui.mouseY() - gridY) / CELL;
			Draw.border(g, gridX + column * CELL, gridY + row * CELL, CELL, CELL, 0xC0FFFFFF);
		}
	}

	private void drawPreview(UiContext ui, int x, int y, int w, int h) {
		var g = ui.g();
		Draw.verticalGradient(g, x, y, w, h, backdrop.top, backdrop.bottom);
		Draw.border(g, x, y, w, h, Theme.SURFACE_BORDER);
		int argb = hoverTarget && module.targetColor.enabled() ? module.targetColorValue.resolve() : module.color.resolve();
		ui.pushClip(x + 1, y + 1, w - 2, h - 2);
		g.pose().pushMatrix();
		float zoom = 2f;
		g.pose().translate(x + w / 2f, y + h / 2f);
		g.pose().scale(zoom, zoom);
		module.draw(g, 0, 0, argb, 0);
		g.pose().popMatrix();
		ui.popClip();
		Draw.text(g, font, Component.translatable("pretupid.crosshair.preview_zoom"), x + 3, y + h - 10, Colors.withAlpha(0xFFFFFF, 180));
	}

	private void drawSettings(UiContext ui, int x, int y, int w, int h) {
		List<Setting<?>> rows = new ArrayList<>();
		for (Setting<?> setting : module.settings()) {
			if (setting.isVisible()) {
				rows.add(setting);
			}
		}
		scroll.begin(ui, "crosshair.settings", x, y, w, h, (rows.size() + 1) * Widgets.ROW_HEIGHT);
		int rowY = y - scroll.offset();
		Widgets.settingRow(ui, this, module.enabledSetting(), x, rowY, w - 6);
		rowY += Widgets.ROW_HEIGHT;
		for (Setting<?> setting : rows) {
			Widgets.settingRow(ui, this, setting, x, rowY, w - 6);
			rowY += Widgets.ROW_HEIGHT;
		}
		scroll.end(ui, x, y, w, h);
	}

	private void undo() {
		if (history.isEmpty()) {
			return;
		}
		cells = history.removeLast();
		commit();
	}

	private int presetIndex;

	private void nextPreset() {
		remember();
		int grid = CrosshairModule.GRID;
		int c = grid / 2;
		boolean[] next = new boolean[grid * grid];
		switch (presetIndex++ % 5) {
			case 0 -> {
				for (int i = 0; i < grid; i++) {
					if (Math.abs(i - c) >= 2 && Math.abs(i - c) <= 5) {
						next[c * grid + i] = true;
						next[i * grid + c] = true;
					}
				}
			}
			case 1 -> {
				for (int i = 2; i <= 5; i++) {
					next[(c - i) * grid + c - i] = true;
					next[(c - i) * grid + c + i] = true;
					next[(c + i) * grid + c - i] = true;
					next[(c + i) * grid + c + i] = true;
				}
			}
			case 2 -> {
				for (int row = 0; row < grid; row++) {
					for (int column = 0; column < grid; column++) {
						double distance = Math.hypot(row - c, column - c);
						if (distance > 4.4 && distance < 5.6) {
							next[row * grid + column] = true;
						}
					}
				}
				next[c * grid + c] = true;
			}
			case 3 -> {
				next[c * grid + c] = true;
				next[(c - 1) * grid + c] = true;
				next[(c + 1) * grid + c] = true;
				next[c * grid + c - 1] = true;
				next[c * grid + c + 1] = true;
			}
			default -> {
				for (int i = 1; i <= 4; i++) {
					next[(c + i) * grid + c - i] = true;
					next[(c + i) * grid + c + i] = true;
				}
				next[c * grid + c] = true;
			}
		}
		cells = next;
		commit();
	}

	@Override
	protected boolean handleKey(KeyEvent event) {
		if (event.hasControlDown() && event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_Z) {
			undo();
			return true;
		}
		return false;
	}
}

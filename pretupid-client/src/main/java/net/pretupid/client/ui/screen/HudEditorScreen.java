package net.pretupid.client.ui.screen;

import net.pretupid.client.platform.Platform;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.config.ClientSettings;
import net.pretupid.client.hud.HudLayer;
import net.pretupid.client.hud.HudModule;
import net.pretupid.client.module.Module;
import net.pretupid.client.ui.Draw;
import net.pretupid.client.ui.Popup;
import net.pretupid.client.ui.PretupidScreen;
import net.pretupid.client.ui.Theme;
import net.pretupid.client.ui.UiContext;
import net.pretupid.client.ui.Widgets;
import net.pretupid.client.util.Colors;

public final class HudEditorScreen extends PretupidScreen {
	private static final int SNAP_DISTANCE = 4;
	private static final int EDGE_MARGIN = 2;

	private static final int HANDLE = 4;
	private static final int DELETE_SIZE = 12;

	private boolean snapping = true;
	private HudModule dragging;
	private HudModule hovered;
	private HudModule deleteTarget;
	private HudModule resizing;
	private HudModule selected;
	private double grabX;
	private double grabY;
	private final List<int[]> guides = new ArrayList<>();

	public HudEditorScreen(Screen parent) {
		super(Component.translatable("pretupid.hud_editor.title"), parent);
	}

	private List<HudModule> activeModules() {
		List<HudModule> result = new ArrayList<>();
		for (Module module : PretupidClient.get().modules().all()) {
			if (module.isEnabled() && module instanceof HudModule hud) {
				result.add(hud);
			}
		}
		return result;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		if (minecraft.level == null) {
			super.extractBackground(graphics, mouseX, mouseY, partialTick);
			return;
		}
		Draw.rect(graphics, 0, 0, width, height, 0x38000000);
	}

	@Override
	protected void drawContent(UiContext ui) {
		var g = ui.g();
		if (dragging != null || selected != null) {
			drawGrid(ui);
		}
		hovered = null;
		for (HudModule hud : activeModules()) {
			drawModule(ui, hud);
		}
		drawDeleteButton(ui);
		for (int[] guide : guides) {
			if (guide[0] == 0) {
				Draw.rect(g, guide[1], 0, 1, height, Theme.accent(170));
			} else {
				Draw.rect(g, 0, guide[1], width, 1, Theme.accent(170));
			}
		}
		drawToolbar(ui);
	}

	private void drawGrid(UiContext ui) {
		int spacing = 16;
		for (int x = spacing; x < width; x += spacing) {
			Draw.rect(ui.g(), x, 0, 1, height, 0x0CFFFFFF);
		}
		for (int y = spacing; y < height; y += spacing) {
			Draw.rect(ui.g(), 0, y, width, 1, 0x0CFFFFFF);
		}
		Draw.rect(ui.g(), width / 2, 0, 1, height, 0x18FFFFFF);
		Draw.rect(ui.g(), 0, height / 2, width, 1, 0x18FFFFFF);
	}

	private void drawModule(UiContext ui, HudModule hud) {
		var g = ui.g();
		HudLayer.Bounds bounds = HudLayer.draw(g, font, minecraft, hud, width, height, true);
		boolean hover = ui.interactive(bounds.x(), bounds.y(), bounds.width(), bounds.height(), new UiContext.Handler() {
			@Override
			public boolean click(double mouseX, double mouseY, int button) {
				if (button == 1) {
					Platform.setScreen(ClientMenuScreen.forModule(HudEditorScreen.this, hud));
					return true;
				}
				if (button == 2) {
					hud.position().reset();
					return true;
				}
				dragging = hud;
				selected = hud;
				grabX = mouseX - bounds.x();
				grabY = mouseY - bounds.y();
				return true;
			}

			@Override
			public void drag(double mouseX, double mouseY) {
				move(hud, mouseX - grabX, mouseY - grabY);
			}

			@Override
			public void release(double mouseX, double mouseY) {
				dragging = null;
				guides.clear();
				PretupidClient.get().config().markDirty();
			}

			@Override
			public boolean clickSound() {
				return false;
			}
		});
		if (hover) {
			hovered = hud;
			ui.setCursor(com.mojang.blaze3d.platform.cursor.CursorTypes.RESIZE_ALL);
		}
		float t = ui.animate("hud.outline." + hud.id(), hover || hud == dragging || hud == selected ? 1f : 0f, 16f);
		int border = Colors.lerp(0x30FFFFFF, Theme.accent(), t);
		Draw.border(g, bounds.x() - 1, bounds.y() - 1, bounds.width() + 2, bounds.height() + 2, border);
		if (hud == selected || hover || hud == resizing) {
			drawHandles(ui, hud, bounds);
		}
		if (t > 0.05f) {
			Component label = hud.name().copy().append(Component.literal(" " + Math.round(hud.scale() * 100) + "%"));
			int labelWidth = font.width(label) + 6;
			int labelY = bounds.y() > 48 ? bounds.y() - 12 : bounds.bottom() + 2;
			int labelX = Math.clamp(bounds.x(), 2, Math.max(2, width - labelWidth - 2));
			Draw.chamfer(g, labelX, labelY, labelWidth, 11, Colors.multiplyAlpha(Theme.accent(220), t));
			Draw.text(g, font, label, labelX + 3, labelY + 2, Colors.multiplyAlpha(0xFFFFFFFF, t));
		}
	}

	private int[] deleteButtonRect(HudModule hud) {
		HudLayer.Bounds bounds = HudLayer.bounds(hud, font, minecraft, width, height, true);
		int x = bounds.right() + HANDLE + 1;
		if (x + DELETE_SIZE > width - 2) {
			x = bounds.x() - HANDLE - 1 - DELETE_SIZE;
		}
		int y = Math.clamp(bounds.y(), 2, Math.max(2, height - DELETE_SIZE - 2));
		return new int[] {Math.max(2, x), y};
	}

	private void drawDeleteButton(UiContext ui) {
		HudModule candidate = dragging != null || resizing != null ? null : selected != null ? selected : hovered;
		if (candidate == null && deleteTarget != null && deleteTarget.isEnabled()) {
			int[] previous = deleteButtonRect(deleteTarget);
			if (ui.hovered(previous[0] - 2, previous[1] - 2, DELETE_SIZE + 4, DELETE_SIZE + 4)) {
				candidate = deleteTarget;
			}
		}
		deleteTarget = candidate;
		if (candidate == null || !candidate.isEnabled()) {
			return;
		}
		HudModule target = candidate;
		int[] rect = deleteButtonRect(target);
		int x = rect[0];
		int y = rect[1];
		boolean hover = ui.interactive(x, y, DELETE_SIZE, DELETE_SIZE, UiContext.Handler.onClick(() -> remove(target)));
		float t = ui.animate("hud.delete", hover ? 1f : 0f, 18f);
		var g = ui.g();
		Draw.rect(g, x - 1, y - 1, DELETE_SIZE + 2, DELETE_SIZE + 2, 0xC0000000);
		Draw.rect(g, x, y, DELETE_SIZE, DELETE_SIZE, Colors.lerp(0xFF3A1E22, Theme.DANGER, t));
		Draw.trash(g, x + 2, y + 2, Colors.lerp(Theme.DANGER, 0xFFFFFFFF, t));
		if (hover) {
			ui.tooltip(Component.translatable("pretupid.hud_editor.remove", target.name()));
		}
	}

	private void remove(HudModule hud) {
		hud.setEnabled(false);
		if (selected == hud) {
			selected = null;
		}
		deleteTarget = null;
		PretupidClient.get().config().markDirty();
	}

	private void drawHandles(UiContext ui, HudModule hud, HudLayer.Bounds bounds) {
		int[][] corners = {
			{bounds.x(), bounds.y()},
			{bounds.right(), bounds.y()},
			{bounds.x(), bounds.bottom()},
			{bounds.right(), bounds.bottom()}
		};
		for (int i = 0; i < corners.length; i++) {
			int cornerX = corners[i][0];
			int cornerY = corners[i][1];
			int anchorX = i % 2 == 0 ? bounds.right() : bounds.x();
			int anchorY = i < 2 ? bounds.bottom() : bounds.y();
			boolean leftEdge = i % 2 == 0;
			boolean topEdge = i < 2;
			boolean hover = ui.interactive(cornerX - HANDLE, cornerY - HANDLE, HANDLE * 2, HANDLE * 2, new UiContext.Handler() {
				@Override
				public boolean click(double mouseX, double mouseY, int button) {
					if (button != 0) {
						return false;
					}
					resizing = hud;
					selected = hud;
					return true;
				}

				@Override
				public void drag(double mouseX, double mouseY) {
					resize(hud, anchorX, anchorY, leftEdge, topEdge, mouseX, mouseY);
				}

				@Override
				public void release(double mouseX, double mouseY) {
					resizing = null;
					PretupidClient.get().config().markDirty();
				}

				@Override
				public boolean clickSound() {
					return false;
				}
			});
			if (hover) {
				ui.setCursor(com.mojang.blaze3d.platform.cursor.CursorTypes.RESIZE_ALL);
			}
			int color = hover || resizing == hud ? 0xFFFFFFFF : Theme.accent();
			Draw.rect(ui.g(), cornerX - 2, cornerY - 2, 4, 4, 0xFF000000);
			Draw.rect(ui.g(), cornerX - 1, cornerY - 1, 2, 2, color);
		}
	}

	private void resize(HudModule hud, int anchorX, int anchorY, boolean leftEdge, boolean topEdge, double mouseX, double mouseY) {
		float scale = hud.scale();
		HudLayer.Bounds current = HudLayer.bounds(hud, font, minecraft, width, height, true);
		double baseWidth = current.width() / scale;
		double baseHeight = current.height() / scale;
		double wantedWidth = Math.max(1.0, leftEdge ? anchorX - mouseX : mouseX - anchorX);
		double wantedHeight = Math.max(1.0, topEdge ? anchorY - mouseY : mouseY - anchorY);
		double next = Math.max(wantedWidth / baseWidth, wantedHeight / baseHeight);
		hud.setScale(Math.clamp(next, hud.minScale(), hud.maxScale()));
		HudLayer.Bounds resized = HudLayer.bounds(hud, font, minecraft, width, height, true);
		int x = leftEdge ? anchorX - resized.width() : anchorX;
		int y = topEdge ? anchorY - resized.height() : anchorY;
		hud.position().place(x, y, resized.width(), resized.height(), width, height);
	}

	private void move(HudModule hud, double rawX, double rawY) {
		HudLayer.Bounds current = HudLayer.bounds(hud, font, minecraft, width, height, true);
		int w = current.width();
		int h = current.height();
		int x = (int) Math.round(rawX);
		int y = (int) Math.round(rawY);
		guides.clear();
		if (snapping) {
			int[] snapX = snap(x, w, width, true, hud);
			int[] snapY = snap(y, h, height, false, hud);
			x = snapX[0];
			y = snapY[0];
			if (snapX[1] >= 0) {
				guides.add(new int[] {0, snapX[1]});
			}
			if (snapY[1] >= 0) {
				guides.add(new int[] {1, snapY[1]});
			}
		}
		hud.position().place(x, y, w, h, width, height);
	}

	private int[] snap(int position, int size, int screenSize, boolean horizontal, HudModule self) {
		List<int[]> targets = new ArrayList<>();
		targets.add(new int[] {EDGE_MARGIN, EDGE_MARGIN});
		targets.add(new int[] {screenSize - size - EDGE_MARGIN, screenSize - EDGE_MARGIN});
		targets.add(new int[] {(screenSize - size) / 2, screenSize / 2});
		for (HudModule other : activeModules()) {
			if (other == self) {
				continue;
			}
			HudLayer.Bounds bounds = HudLayer.bounds(other, font, minecraft, width, height, true);
			int start = horizontal ? bounds.x() : bounds.y();
			int end = horizontal ? bounds.right() : bounds.bottom();
			targets.add(new int[] {start, start});
			targets.add(new int[] {end - size, end});
			targets.add(new int[] {end + 2, end + 1});
			targets.add(new int[] {start - size - 2, start - 1});
		}
		int best = position;
		int guide = -1;
		int bestDistance = SNAP_DISTANCE + 1;
		for (int[] target : targets) {
			int distance = Math.abs(position - target[0]);
			if (distance < bestDistance) {
				bestDistance = distance;
				best = target[0];
				guide = target[1];
			}
		}
		return new int[] {best, guide};
	}

	private void drawToolbar(UiContext ui) {
		var g = ui.g();
		int buttonWidth = 66;
		int count = 4;
		int toolbarWidth = buttonWidth * count + 4 * (count - 1) + 8;
		int x = (width - toolbarWidth) / 2;
		boolean bottom = PretupidClient.get().settings().hudEditorToolbar.get() == ClientSettings.HudToolbarPosition.BOTTOM;
		int y = bottom ? height - 32 : 6;
		Draw.shadow(g, x, y, toolbarWidth, 26, 3, 0.8f);
		Draw.glass(g, x, y, toolbarWidth, 26, Theme.WINDOW, Theme.WINDOW_BORDER);
		int widgetsX = x + 4;
		Widgets.button(ui, "editor.widgets", widgetsX, y + 4, buttonWidth, 18, Component.translatable("pretupid.hud_editor.widgets"), Widgets.ButtonStyle.SECONDARY,
			() -> openPopup(new WidgetsPopup(widgetsX, bottom ? y - 4 : y + 30, bottom)));
		int bx = widgetsX + buttonWidth + 4;
		Widgets.button(ui, "editor.snap", bx, y + 4, buttonWidth, 18,
			Component.translatable(snapping ? "pretupid.hud_editor.snap_on" : "pretupid.hud_editor.snap_off"), Widgets.ButtonStyle.SECONDARY, () -> snapping = !snapping);
		bx += buttonWidth + 4;
		Widgets.button(ui, "editor.menu", bx, y + 4, buttonWidth, 18, Component.translatable("pretupid.hud_editor.menu"), Widgets.ButtonStyle.SECONDARY,
			() -> Platform.setScreen(parent instanceof ClientMenuScreen menu ? menu : new ClientMenuScreen(this)));
		bx += buttonWidth + 4;
		Widgets.button(ui, "editor.done", bx, y + 4, buttonWidth, 18, Component.translatable("pretupid.ui.done"), Widgets.ButtonStyle.PRIMARY, this::onClose);
	}

	@Override
	protected boolean handleKey(KeyEvent event) {
		if (PretupidClient.get().settings().hudEditorKey.matches(event.key()) && !hasPopup()) {
			onClose();
			return true;
		}
		if (selected == null) {
			return false;
		}
		int step = event.hasShiftDown() ? 5 : 1;
		int dx = 0;
		int dy = 0;
		if (event.isLeft()) {
			dx = -step;
		} else if (event.isRight()) {
			dx = step;
		} else if (event.isUp()) {
			dy = -step;
		} else if (event.isDown()) {
			dy = step;
		} else if (event.key() == InputConstants.KEY_DELETE || event.key() == InputConstants.KEY_BACKSPACE) {
			remove(selected);
			return true;
		} else {
			return false;
		}
		HudLayer.Bounds bounds = HudLayer.bounds(selected, font, minecraft, width, height, true);
		selected.position().place(bounds.x() + dx, bounds.y() + dy, bounds.width(), bounds.height(), width, height);
		PretupidClient.get().config().markDirty();
		return true;
	}

	@Override
	protected boolean onBackgroundClick(double mouseX, double mouseY, int button) {
		selected = null;
		return false;
	}

	private final class WidgetsPopup implements Popup {
		private final int anchorX;
		private final int anchorY;
		private final boolean above;

		private WidgetsPopup(int anchorX, int anchorY, boolean above) {
			this.anchorX = anchorX;
			this.anchorY = anchorY;
			this.above = above;
		}

		@Override
		public void draw(UiContext ui, int screenWidth, int screenHeight) {
			var g = ui.g();
			List<HudModule> all = new ArrayList<>();
			for (Module module : PretupidClient.get().modules().all()) {
				if (module instanceof HudModule hud) {
					all.add(hud);
				}
			}
			int columns = 2;
			int rowHeight = 20;
			int columnWidth = 132;
			int rows = (all.size() + columns - 1) / columns;
			int w = columns * columnWidth + 12;
			int h = rows * rowHeight + 12;
			int x = Math.clamp(anchorX, 4, Math.max(4, screenWidth - w - 4));
			int y = Math.clamp(above ? anchorY - h : anchorY, 4, Math.max(4, screenHeight - h - 4));
			Draw.shadow(g, x, y, w, h, 4, 1f);
			Draw.glass(g, x, y, w, h, Theme.POPUP, Theme.WINDOW_BORDER);
			ui.hit(x, y, w, h, new UiContext.Handler() {
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
			for (int i = 0; i < all.size(); i++) {
				HudModule hud = all.get(i);
				int cx = x + 6 + (i % columns) * columnWidth;
				int cy = y + 6 + (i / columns) * rowHeight;
				boolean hover = ui.interactive(cx, cy, columnWidth - 4, rowHeight - 2, UiContext.Handler.onClick(hud::toggle));
				Draw.chamfer(g, cx, cy, columnWidth - 4, rowHeight - 2, hover ? Theme.SURFACE_HOVER : Theme.SURFACE);
				hud.icon().draw(g, cx + 2, cy + 1);
				Draw.text(g, font, Draw.ellipsize(font, hud.name(), columnWidth - 52), cx + 21, cy + 5, hud.isEnabled() ? Theme.TEXT : Theme.TEXT_MUTED);
				Widgets.toggle(ui, "editor.widget." + hud.id(), cx + columnWidth - 28, cy + 4, hud.isEnabled(), hud::toggle);
			}
		}
	}
}

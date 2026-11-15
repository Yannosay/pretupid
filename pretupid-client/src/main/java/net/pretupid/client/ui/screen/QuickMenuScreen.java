package net.pretupid.client.ui.screen;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.config.ConfigStore;
import net.pretupid.client.module.Module;
import net.pretupid.client.ui.Draw;
import net.pretupid.client.ui.Easing;
import net.pretupid.client.ui.PretupidScreen;
import net.pretupid.client.ui.Theme;
import net.pretupid.client.ui.UiContext;
import net.pretupid.client.ui.UiIcon;
import net.pretupid.client.util.Colors;

public final class QuickMenuScreen extends PretupidScreen {
	private static final List<String> DEFAULT_MODULES = List.of(
		"fps_boost", "entity_culling", "zoom", "freelook", "toggle_sprint", "fullbright",
		"fps", "cps", "keystrokes", "coordinates", "armor", "clear_weather");
	private static final int MAX_TILES = 12;
	private static final int COLUMNS = 4;
	private static final int TILE_WIDTH = 78;
	private static final int TILE_HEIGHT = 44;
	private static final int GAP = 5;
	private static final int PADDING = 10;
	private static final int HEADER = 26;
	private static final int ACTION_HEIGHT = 18;
	private static final long TAP_MS = 300L;

	private final boolean holdMode;
	private final int holdKey;
	private final long createdAt = System.currentTimeMillis();
	private final List<Module> tiles;
	private boolean released;
	private boolean interacted;
	private int hovered = -1;
	private int lastHovered = -1;

	public QuickMenuScreen() {
		super(Component.translatable("pretupid.quick.title"), null);
		PretupidClient client = PretupidClient.get();
		InputConstants.Key key = KeyMappingHelper.getBoundKeyOf(client.keybinds().quickMenu());
		boolean keyboard = key.getType() == InputConstants.Type.KEYBOARD && key.getValue() != InputConstants.UNKNOWN.getValue();
		this.holdMode = keyboard;
		this.holdKey = key.getValue();
		this.tiles = collectTiles(client);
	}

	private static List<Module> collectTiles(PretupidClient client) {
		ConfigStore config = client.config();
		List<Module> result = new ArrayList<>();
		for (Module module : client.modules().all()) {
			if (config.isFavorite(module) && result.size() < MAX_TILES) {
				result.add(module);
			}
		}
		if (result.isEmpty()) {
			for (String id : DEFAULT_MODULES) {
				for (Module module : client.modules().all()) {
					if (module.id().equals(id) && result.size() < MAX_TILES) {
						result.add(module);
					}
				}
			}
		}
		return result;
	}

	private boolean usesFavorites() {
		ConfigStore config = PretupidClient.get().config();
		for (Module module : tiles) {
			if (config.isFavorite(module)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		float fade = Easing.outCubic(openProgress());
		Draw.rect(graphics, 0, 0, width, height, Colors.withAlpha(0, Math.round(120 * fade)));
	}

	@Override
	protected void drawContent(UiContext ui) {
		var g = ui.g();
		int rows = Math.max(1, (tiles.size() + COLUMNS - 1) / COLUMNS);
		int panelWidth = COLUMNS * TILE_WIDTH + (COLUMNS - 1) * GAP + PADDING * 2;
		int panelHeight = HEADER + rows * TILE_HEIGHT + (rows - 1) * GAP + GAP * 2 + ACTION_HEIGHT + PADDING * 2;
		float open = openProgress();
		float scale = 0.92f + 0.08f * Easing.outBack(open);
		int panelX = (width - panelWidth) / 2;
		int panelY = (height - panelHeight) / 2;

		g.pose().pushMatrix();
		g.pose().translate(width / 2f, height / 2f);
		g.pose().scale(scale, scale);
		g.pose().translate(-width / 2f, -height / 2f);

		Draw.shadow(g, panelX, panelY, panelWidth, panelHeight, 5, Easing.outCubic(open));
		Draw.glass(g, panelX, panelY, panelWidth, panelHeight, Theme.WINDOW, Theme.WINDOW_BORDER);
		Draw.rect(g, panelX + 1, panelY + 1, panelWidth - 2, 2, Theme.accent(200));
		Draw.textShadow(g, font, title, panelX + PADDING, panelY + 9, Theme.TEXT);
		Component hint = Component.translatable(holdMode && !released ? "pretupid.quick.hint_hold" : "pretupid.quick.hint_click");
		Component fittedHint = Draw.ellipsize(font, hint, Math.round((panelWidth - PADDING * 2 - font.width(title) - 8) / 0.75f));
		Draw.scaled(g, font, fittedHint, panelX + panelWidth - PADDING - font.width(fittedHint) * 0.75f, panelY + 11, 0.75f, Theme.TEXT_DIM, false);

		int gridY = panelY + HEADER;
		hovered = -1;
		for (int i = 0; i < tiles.size(); i++) {
			int column = i % COLUMNS;
			int row = i / COLUMNS;
			int tileX = panelX + PADDING + column * (TILE_WIDTH + GAP);
			int tileY = gridY + row * (TILE_HEIGHT + GAP);
			drawTile(ui, tiles.get(i), i, tileX, tileY);
		}
		if (tiles.isEmpty()) {
			Draw.centered(g, font, Component.translatable("pretupid.quick.empty"), width / 2, gridY + 16, Theme.TEXT_DIM);
		}
		if (hovered != lastHovered) {
			if (hovered >= 0 && PretupidClient.get().settings().uiSounds.enabled()) {
				minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.8f, 0.18f));
			}
			lastHovered = hovered;
		}

		int actionY = panelY + panelHeight - PADDING - ACTION_HEIGHT;
		int actionWidth = (panelWidth - PADDING * 2 - GAP * 3) / 4;
		int actionX = panelX + PADDING;
		drawAction(ui, "edit_hud", actionX, actionY, actionWidth, UiIcon.GEAR, () -> {
			if (minecraft.level != null) {
				minecraft.gui.setScreen(new HudEditorScreen(null));
			}
		});
		actionX += actionWidth + GAP;
		drawAction(ui, "all_modules", actionX, actionY, actionWidth, UiIcon.STAR, () -> minecraft.gui.setScreen(new ClientMenuScreen(null)));
		actionX += actionWidth + GAP;
		drawAction(ui, "emotes", actionX, actionY, actionWidth, null, () -> minecraft.gui.setScreen(new EmoteWheelScreen()));
		actionX += actionWidth + GAP;
		drawAction(ui, "benchmark", actionX, actionY, actionWidth, null, () -> {
			minecraft.gui.setScreen(null);
			PretupidClient.get().benchmark().start(minecraft);
		});

		if (!usesFavorites() && !tiles.isEmpty()) {
			Component tip = Component.translatable("pretupid.quick.favorites_tip");
			Draw.scaled(g, font, tip, width / 2f - font.width(tip) * 0.375f, panelY + panelHeight + 6, 0.75f, Theme.TEXT_DIM, true);
		}
		g.pose().popMatrix();

		if (holdMode && !released && !InputConstants.isKeyDown(holdKey)) {
			released = true;
			boolean tapped = System.currentTimeMillis() - createdAt <= TAP_MS;
			if (!tapped) {
				if (hovered >= 0 && !interacted) {
					toggle(tiles.get(hovered));
				}
				minecraft.execute(this::onClose);
			}
		}
	}

	private void drawTile(UiContext ui, Module module, int index, int x, int y) {
		var g = ui.g();
		String id = "quick." + module.id();
		float appear = ui.animateFrom(id + ".appear", 0f, 1f, 9f + Math.max(0, 10 - index));
		int offsetY = Math.round((1f - Easing.outCubic(appear)) * 10f);
		y += offsetY;
		boolean hover = ui.interactive(x, y, TILE_WIDTH, TILE_HEIGHT, new UiContext.Handler() {
			@Override
			public boolean click(double mouseX, double mouseY, int button) {
				interacted = true;
				if (button == 1) {
					minecraft.gui.setScreen(ClientMenuScreen.forModule(null, module));
				} else if (button == 0) {
					toggle(module);
				} else {
					return false;
				}
				return true;
			}
		});
		if (hover) {
			hovered = index;
			ui.tooltip(module.isPaused() ? module.pauseReason() : module.description());
		}
		float t = ui.animate(id + ".hover", hover ? 1f : 0f, 18f);
		float on = ui.animate(id + ".on", module.isEnabled() ? 1f : 0f, 12f);
		int lift = Math.round(t * 2f);
		y -= lift;
		int fill = Colors.lerp(Theme.SURFACE, Theme.SURFACE_HOVER, t);
		fill = Colors.lerp(fill, Theme.accent(110), on * 0.75f);
		Draw.shadow(g, x, y, TILE_WIDTH, TILE_HEIGHT, 2, 0.3f + 0.5f * t);
		Draw.chamfer(g, x, y, TILE_WIDTH, TILE_HEIGHT, fill);
		Draw.border(g, x, y, TILE_WIDTH, TILE_HEIGHT, Colors.lerp(Colors.lerp(Theme.SURFACE_BORDER, Theme.accent(120), t), Theme.accent(), on));
		g.pose().pushMatrix();
		g.pose().translate(x + TILE_WIDTH / 2f - 8, y + 6 - t);
		module.icon().draw(g, 0, 0);
		g.pose().popMatrix();
		Component name = Draw.ellipsize(font, module.name(), Math.round((TILE_WIDTH - 6) / 0.85f));
		Draw.scaled(g, font, name, x + TILE_WIDTH / 2f - font.width(name) * 0.425f, y + 26, 0.85f, Colors.lerp(Theme.TEXT_MUTED, 0xFFFFFFFF, Math.max(on, t)), false);
		int barWidth = Math.round((TILE_WIDTH - 20) * on);
		Draw.rect(g, x + 10, y + TILE_HEIGHT - 6, TILE_WIDTH - 20, 2, Colors.withAlpha(0xFFFFFF, 26));
		if (barWidth > 0) {
			Draw.rect(g, x + TILE_WIDTH / 2 - barWidth / 2, y + TILE_HEIGHT - 6, barWidth, 2, module.isPaused() ? Theme.WARNING : Theme.accentText());
		}
		if (index < 9) {
			Draw.scaled(g, font, Component.literal(String.valueOf(index + 1)), x + 4, y + 4, 0.7f, Theme.TEXT_DIM, false);
		}
	}

	private void drawAction(UiContext ui, String key, int x, int y, int w, UiIcon icon, Runnable action) {
		var g = ui.g();
		String id = "quick.action." + key;
		boolean hover = ui.interactive(x, y, w, ACTION_HEIGHT, UiContext.Handler.onClick(() -> {
			interacted = true;
			action.run();
		}));
		float t = ui.animate(id, hover ? 1f : 0f, 18f);
		Draw.chamfer(g, x, y, w, ACTION_HEIGHT, Colors.lerp(Theme.SURFACE, Theme.accent(150), t));
		Component label = Draw.ellipsize(font, Component.translatable("pretupid.quick.action." + key), w - (icon == null ? 6 : 18));
		int labelWidth = font.width(label) + (icon == null ? 0 : 12);
		int labelX = x + (w - labelWidth) / 2;
		if (icon != null) {
			icon.draw(g, labelX, y + 5, Colors.lerp(Theme.TEXT_MUTED, 0xFFFFFFFF, t));
			labelX += 12;
		}
		Draw.text(g, font, label, labelX, y + 5, Colors.lerp(Theme.TEXT, 0xFFFFFFFF, t));
	}

	private void toggle(Module module) {
		module.toggle();
		PretupidClient.get().notifications().moduleToggled(module);
		if (PretupidClient.get().settings().uiSounds.enabled()) {
			minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), module.isEnabled() ? 1.3f : 0.9f, 0.35f));
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		interacted = true;
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	protected boolean handleKey(KeyEvent event) {
		int key = event.key();
		for (int i = 0; i < Math.min(9, tiles.size()); i++) {
			if (key == InputConstants.KEY_1 + i) {
				interacted = true;
				toggle(tiles.get(i));
				return true;
			}
		}
		if ((!holdMode || released) && PretupidClient.get().keybinds().quickMenu().matches(event)) {
			onClose();
			return true;
		}
		return false;
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(null);
	}
}

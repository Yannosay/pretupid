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
import net.pretupid.client.cosmetic.Catalog;
import net.pretupid.client.ui.Draw;
import net.pretupid.client.ui.Easing;
import net.pretupid.client.ui.PretupidScreen;
import net.pretupid.client.ui.Theme;
import net.pretupid.client.ui.UiContext;
import net.pretupid.client.util.Colors;

public final class EmoteWheelScreen extends PretupidScreen {
	private static final int MAX_SLOTS = 8;
	private static final int RADIUS = 68;
	private static final int SLOT_WIDTH = 70;
	private static final int SLOT_HEIGHT = 22;
	private static final int DEAD_ZONE = 16;
	private static final int CENTER_WIDTH = 84;
	private static final int CENTER_HEIGHT = 28;

	private final boolean holdMode;
	private final int holdKey;
	private int selected = -1;
	private int lastSelected = -1;
	private boolean released;
	private final long createdAt = System.currentTimeMillis();
	private static final long TAP_MS = 300L;

	public EmoteWheelScreen() {
		super(Component.translatable("pretupid.emotes.title"), null);
		PretupidClient client = PretupidClient.get();
		InputConstants.Key key = KeyMappingHelper.getBoundKeyOf(client.keybinds().emoteWheel());
		boolean keyboard = key.getType() == InputConstants.Type.KEYBOARD && key.getValue() != InputConstants.UNKNOWN.getValue();
		this.holdMode = client.settings().emoteWheelHold.enabled() && keyboard;
		this.holdKey = key.getValue();
	}

	private List<Catalog.EmoteEntry> slots() {
		Catalog catalog = PretupidClient.get().cosmetics().catalog();
		List<Catalog.EmoteEntry> result = new ArrayList<>();
		for (String id : PretupidClient.get().settings().emoteWheel.get().split(",")) {
			catalog.emote(id.trim()).filter(entry -> !result.contains(entry)).ifPresent(result::add);
			if (result.size() >= MAX_SLOTS) {
				return result;
			}
		}
		for (Catalog.EmoteEntry entry : catalog.emotes()) {
			if (result.size() >= MAX_SLOTS) {
				break;
			}
			if (!result.contains(entry)) {
				result.add(entry);
			}
		}
		return result;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		float fade = Easing.outCubic(openProgress());
		Draw.rect(graphics, 0, 0, width, height, Colors.withAlpha(0, Math.round(110 * fade)));
	}

	private int selectionAt(int count, double mouseX, double mouseY) {
		if (count == 0) {
			return -1;
		}
		double dx = mouseX - width / 2.0;
		double dy = (mouseY - height / 2.0) / 0.8;
		if (dx * dx + dy * dy < DEAD_ZONE * DEAD_ZONE) {
			return -1;
		}
		double angle = Math.atan2(dy, dx) + Math.PI / 2;
		double sector = Math.PI * 2 / count;
		return Math.floorMod((int) Math.floor((angle + sector / 2) / sector), count);
	}

	@Override
	protected void drawContent(UiContext ui) {
		var g = ui.g();
		List<Catalog.EmoteEntry> slots = slots();
		int cx = width / 2;
		int cy = height / 2;
		float open = Easing.outBack(openProgress());
		selected = selectionAt(slots.size(), ui.mouseX(), ui.mouseY());
		if (selected != lastSelected) {
			if (selected >= 0 && PretupidClient.get().settings().uiSounds.enabled()) {
				minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.7f, 0.25f));
			}
			lastSelected = selected;
		}

		if (selected >= 0) {
			double angle = -Math.PI / 2 + Math.PI * 2 * selected / slots.size();
			for (int step = 1; step <= 6; step++) {
				float distance = DEAD_ZONE + (RADIUS - DEAD_ZONE - 12) * step / 6f;
				int px = cx + (int) Math.round(Math.cos(angle) * distance * open);
				int py = cy + (int) Math.round(Math.sin(angle) * distance * 0.8 * open);
				Draw.rect(g, px - 1, py - 1, 2, 2, Theme.accent(60 + step * 25));
			}
		}

		for (int i = 0; i < slots.size(); i++) {
			Catalog.EmoteEntry emote = slots.get(i);
			double angle = -Math.PI / 2 + Math.PI * 2 * i / slots.size();
			boolean active = i == selected;
			float t = ui.animate("emote.slot." + emote.id(), active ? 1f : 0f, 20f);
			float distance = RADIUS * open + t * 6f;
			int sx = cx + (int) Math.round(Math.cos(angle) * distance) - SLOT_WIDTH / 2;
			int sy = cy + (int) Math.round(Math.sin(angle) * distance * 0.8) - SLOT_HEIGHT / 2;
			ui.hit(sx, sy, SLOT_WIDTH, SLOT_HEIGHT, UiContext.Handler.onClick(() -> play(emote)));
			Draw.shadow(g, sx, sy, SLOT_WIDTH, SLOT_HEIGHT, 3, 0.6f + 0.4f * t);
			Draw.glass(g, sx, sy, SLOT_WIDTH, SLOT_HEIGHT, Colors.lerp(Theme.WINDOW, Theme.accent(215), t), Colors.lerp(Theme.WINDOW_BORDER, Theme.accent(), t));
			Draw.text(g, font, Component.literal(String.valueOf(i + 1)), sx + 4, sy + 7, Colors.lerp(Theme.TEXT_DIM, 0xFFFFFFFF, t));
			Draw.centered(g, font, Draw.ellipsize(font, Component.literal(emote.name()), SLOT_WIDTH - 18), sx + SLOT_WIDTH / 2 + 4, sy + 7, Colors.lerp(Theme.TEXT, 0xFFFFFFFF, t));
		}

		int centerX = cx - CENTER_WIDTH / 2;
		int centerY = cy - CENTER_HEIGHT / 2;
		Draw.shadow(g, centerX, centerY, CENTER_WIDTH, CENTER_HEIGHT, 3, 0.7f);
		Draw.glass(g, centerX, centerY, CENTER_WIDTH, CENTER_HEIGHT, Theme.WINDOW, Theme.WINDOW_BORDER);
		Component title;
		if (slots.isEmpty()) {
			title = Component.translatable("pretupid.emotes.empty");
		} else if (selected >= 0) {
			title = Component.literal(slots.get(selected).name());
		} else {
			title = Component.translatable("pretupid.emotes.title");
		}
		Draw.centered(g, font, Draw.ellipsize(font, title, CENTER_WIDTH - 8), cx, centerY + 5, selected >= 0 ? Theme.accentText() : Theme.TEXT);
		Component hint = Component.translatable(holdMode && !released ? "pretupid.emotes.hint_hold" : "pretupid.emotes.hint_click");
		Draw.scaled(g, font, Draw.ellipsize(font, hint, Math.round((CENTER_WIDTH - 8) / 0.75f)), cx - font.width(hint) * 0.375f, centerY + 17, 0.75f, Theme.TEXT_DIM, false);

		if (holdMode && !released && !InputConstants.isKeyDown(holdKey)) {
			released = true;
			if (selected >= 0 && selected < slots.size()) {
				play(slots.get(selected));
			} else if (System.currentTimeMillis() - createdAt > TAP_MS) {
				minecraft.execute(this::onClose);
			}
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		List<Catalog.EmoteEntry> slots = slots();
		int index = selectionAt(slots.size(), event.x(), event.y());
		if (index >= 0) {
			play(slots.get(index));
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	private void play(Catalog.EmoteEntry emote) {
		minecraft.gui.setScreen(null);
		PretupidClient.get().emotes().playLocal(emote);
	}

	@Override
	protected boolean handleKey(KeyEvent event) {
		int key = event.key();
		List<Catalog.EmoteEntry> slots = slots();
		for (int i = 0; i < slots.size(); i++) {
			if (key == InputConstants.KEY_1 + i) {
				play(slots.get(i));
				return true;
			}
		}
		if ((!holdMode || released) && PretupidClient.get().keybinds().emoteWheel().matches(event)) {
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

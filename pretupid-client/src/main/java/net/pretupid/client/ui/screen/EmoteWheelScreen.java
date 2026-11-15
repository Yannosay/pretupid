package net.pretupid.client.ui.screen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.cosmetic.Catalog;
import net.pretupid.client.ui.Draw;
import net.pretupid.client.ui.PretupidScreen;
import net.pretupid.client.ui.Theme;
import net.pretupid.client.ui.UiContext;
import net.pretupid.client.util.Colors;

public final class EmoteWheelScreen extends PretupidScreen {
	private static final int MAX_SLOTS = 8;
	private static final int RADIUS = 62;
	private static final int SLOT_WIDTH = 64;
	private static final int SLOT_HEIGHT = 20;

	public EmoteWheelScreen() {
		super(Component.translatable("pretupid.emotes.title"), null);
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
		Draw.rect(graphics, 0, 0, width, height, 0x50000000);
	}

	@Override
	protected void drawContent(UiContext ui) {
		var g = ui.g();
		List<Catalog.EmoteEntry> slots = slots();
		int cx = width / 2;
		int cy = height / 2;
		Draw.chamfer(g, cx - 34, cy - 10, 68, 20, Theme.WINDOW);
		Draw.centered(g, font, slots.isEmpty() ? Component.translatable("pretupid.emotes.empty") : Component.translatable("pretupid.emotes.title"), cx, cy - 4, Theme.TEXT_MUTED);
		float open = net.pretupid.client.ui.Easing.outBack(openProgress());
		for (int i = 0; i < slots.size(); i++) {
			Catalog.EmoteEntry emote = slots.get(i);
			double angle = -Math.PI / 2 + Math.PI * 2 * i / slots.size();
			int sx = cx + (int) Math.round(Math.cos(angle) * RADIUS * open) - SLOT_WIDTH / 2;
			int sy = cy + (int) Math.round(Math.sin(angle) * RADIUS * 0.8 * open) - SLOT_HEIGHT / 2;
			boolean hover = ui.interactive(sx, sy, SLOT_WIDTH, SLOT_HEIGHT, UiContext.Handler.onClick(() -> play(emote)));
			float t = ui.animate("emote.slot." + emote.id(), hover ? 1f : 0f, 18f);
			Draw.shadow(g, sx, sy, SLOT_WIDTH, SLOT_HEIGHT, 2, 0.6f);
			Draw.glass(g, sx, sy, SLOT_WIDTH, SLOT_HEIGHT, Colors.lerp(Theme.WINDOW, Theme.accent(200), t), Theme.WINDOW_BORDER);
			Draw.text(g, font, Component.literal(String.valueOf(i + 1)), sx + 4, sy + 6, Theme.TEXT_DIM);
			Draw.centered(g, font, Draw.ellipsize(font, Component.literal(emote.name()), SLOT_WIDTH - 16), sx + SLOT_WIDTH / 2 + 4, sy + 6, Theme.TEXT);
		}
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
			if (key == com.mojang.blaze3d.platform.InputConstants.KEY_1 + i) {
				play(slots.get(i));
				return true;
			}
		}
		if (PretupidClient.get().keybinds().emoteWheel().matches(event)) {
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

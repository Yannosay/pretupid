package net.pretupid.client.ui.screen;

import net.pretupid.client.platform.Gfx;
import net.pretupid.client.platform.Platform;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.cosmetic.Catalog;
import net.pretupid.client.cosmetic.EmoteWheel;
import net.pretupid.client.ui.Draw;
import net.pretupid.client.ui.Easing;
import net.pretupid.client.ui.PretupidScreen;
import net.pretupid.client.ui.Theme;
import net.pretupid.client.ui.UiContext;
import net.pretupid.client.util.Colors;

public final class EmoteWheelScreen extends PretupidScreen {
	private static final int RADIUS = 72;
	private static final int RING_RADIUS = 34;
	private static final int RING_DOTS = 36;
	private static final int SLOT_WIDTH = 74;
	private static final int SLOT_HEIGHT = 26;
	private static final int DEAD_ZONE = 18;
	private static final int CENTER_SIZE = 46;
	private static final float SQUASH = 0.82f;
	private static final long STAGGER_MS = 35L;
	private static final long POP_MS = 260L;
	private static final long TAP_MS = 300L;

	private final boolean holdMode;
	private final int holdKey;
	private final long createdAt = System.currentTimeMillis();
	private int selected = -1;
	private int lastSelected = -1;
	private boolean released;
	private float pointerAngle = Float.NaN;
	private long lastFrame = System.nanoTime();

	public EmoteWheelScreen() {
		super(Component.translatable("pretupid.emotes.title"), null);
		PretupidClient client = PretupidClient.get();
		InputConstants.Key key = Platform.boundKey(client.keybinds().emoteWheel());
		boolean keyboard = key.getType() == Platform.keyboard() && key.getValue() != InputConstants.UNKNOWN.getValue();
		this.holdMode = client.settings().emoteWheelHold.enabled() && keyboard && Platform.isKeyDown(key.getValue());
		this.holdKey = key.getValue();
	}

	private List<Optional<Catalog.EmoteEntry>> slots() {
		PretupidClient client = PretupidClient.get();
		return client.emoteWheel().slots(client.cosmetics().catalog());
	}

	private static double slotAngle(int index) {
		return -Math.PI / 2 + Math.PI * 2 * index / EmoteWheel.SIZE;
	}

	@Override
	protected void renderScreenBackground(Gfx graphics, int mouseX, int mouseY, float partialTick) {
		float fade = Easing.outCubic(openProgress());
		Draw.rect(graphics, 0, 0, width, height, Colors.withAlpha(0, Math.round(120 * fade)));
	}

	private int selectionAt(List<Optional<Catalog.EmoteEntry>> slots, double mouseX, double mouseY) {
		double dx = mouseX - width / 2.0;
		double dy = (mouseY - height / 2.0) / SQUASH;
		if (dx * dx + dy * dy < DEAD_ZONE * DEAD_ZONE) {
			return -1;
		}
		double angle = Math.atan2(dy, dx) + Math.PI / 2;
		double sector = Math.PI * 2 / EmoteWheel.SIZE;
		int index = Math.floorMod((int) Math.floor((angle + sector / 2) / sector), EmoteWheel.SIZE);
		return slots.get(index).isPresent() ? index : -1;
	}

	private float entrance(int index) {
		long age = System.currentTimeMillis() - createdAt - index * STAGGER_MS;
		return Easing.outBack(Math.clamp(age / (float) POP_MS, 0f, 1f));
	}

	@Override
	protected void drawContent(UiContext ui) {
		var g = ui.g();
		long now = System.nanoTime();
		float delta = Math.min(0.1f, (now - lastFrame) / 1_000_000_000f);
		lastFrame = now;
		List<Optional<Catalog.EmoteEntry>> slots = slots();
		int cx = width / 2;
		int cy = height / 2;
		float open = Easing.outCubic(openProgress());
		selected = selectionAt(slots, ui.mouseX(), ui.mouseY());
		if (selected != lastSelected) {
			if (selected >= 0 && PretupidClient.get().settings().uiSounds.enabled()) {
				minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.45f + selected * 0.09f, 0.22f));
			}
			lastSelected = selected;
		}

		drawRing(ui, cx, cy, open, delta);

		for (int i = 0; i < EmoteWheel.SIZE; i++) {
			drawSlot(ui, slots.get(i), i, cx, cy);
		}

		drawCenter(ui, slots, cx, cy, open);

		if (holdMode && !released && !Platform.isKeyDown(holdKey)) {
			released = true;
			if (selected >= 0) {
				slots.get(selected).ifPresent(this::play);
			} else if (System.currentTimeMillis() - createdAt > TAP_MS) {
				minecraft.execute(this::onClose);
			}
		}
	}

	private void drawRing(UiContext ui, int cx, int cy, float open, float delta) {
		var g = ui.g();
		float radius = RING_RADIUS * open;
		if (selected >= 0) {
			float target = (float) slotAngle(selected);
			if (Float.isNaN(pointerAngle)) {
				pointerAngle = target;
			}
			float difference = (float) Math.atan2(Math.sin(target - pointerAngle), Math.cos(target - pointerAngle));
			pointerAngle += difference * Math.min(1f, delta * 18f);
		}
		double sector = Math.PI * 2 / EmoteWheel.SIZE;
		for (int dot = 0; dot < RING_DOTS; dot++) {
			double angle = Math.PI * 2 * dot / RING_DOTS - Math.PI / 2;
			int px = cx + (int) Math.round(Math.cos(angle) * radius);
			int py = cy + (int) Math.round(Math.sin(angle) * radius * SQUASH);
			float glow = 0f;
			if (selected >= 0 && !Float.isNaN(pointerAngle)) {
				double distance = Math.abs(Math.atan2(Math.sin(angle - pointerAngle), Math.cos(angle - pointerAngle)));
				glow = (float) Math.max(0, 1 - distance / (sector * 0.6));
			}
			int size = glow > 0.5f ? 3 : 2;
			int color = glow > 0f ? Colors.lerp(Colors.withAlpha(0xFFFFFF, 50), Theme.accent(), glow) : Colors.withAlpha(0xFFFFFF, Math.round(50 * open));
			Draw.rect(g, px - size / 2, py - size / 2, size, size, color);
		}
		if (selected >= 0 && !Float.isNaN(pointerAngle)) {
			for (int step = 0; step < 3; step++) {
				float distance = radius + 6 + step * 4;
				int px = cx + (int) Math.round(Math.cos(pointerAngle) * distance);
				int py = cy + (int) Math.round(Math.sin(pointerAngle) * distance * SQUASH);
				int size = 4 - step;
				Draw.rect(g, px - size / 2, py - size / 2, size, size, Theme.accent(255 - step * 70));
			}
		}
	}

	private void drawSlot(UiContext ui, Optional<Catalog.EmoteEntry> slot, int index, int cx, int cy) {
		var g = ui.g();
		float appear = entrance(index);
		if (appear <= 0.01f) {
			return;
		}
		boolean active = index == selected;
		float t = ui.animate("emote.slot." + index, active ? 1f : 0f, 22f);
		double angle = slotAngle(index);
		float distance = RADIUS * Math.min(1f, appear) + t * 7f;
		int centerX = cx + (int) Math.round(Math.cos(angle) * distance);
		int centerY = cy + (int) Math.round(Math.sin(angle) * distance * SQUASH);
		float scale = Math.max(0.2f, appear) * (1f + 0.12f * t);
		g.pose().pushMatrix();
		g.pose().translate(centerX, centerY);
		g.pose().scale(scale, scale);
		int sx = -SLOT_WIDTH / 2;
		int sy = -SLOT_HEIGHT / 2;
		if (slot.isEmpty()) {
			Draw.chamfer(g, sx, sy, SLOT_WIDTH, SLOT_HEIGHT, Colors.withAlpha(0x000000, 70));
			Draw.border(g, sx, sy, SLOT_WIDTH, SLOT_HEIGHT, Colors.withAlpha(0xFFFFFF, 28));
			Draw.centered(g, font, Component.translatable("pretupid.emotes.empty_slot"), 0, -4, Theme.TEXT_DIM);
		} else {
			Catalog.EmoteEntry emote = slot.get();
			ui.hit(centerX - SLOT_WIDTH / 2, centerY - SLOT_HEIGHT / 2, SLOT_WIDTH, SLOT_HEIGHT, UiContext.Handler.onClick(() -> play(emote)));
			Draw.shadow(g, sx, sy, SLOT_WIDTH, SLOT_HEIGHT, 3, 0.55f + 0.45f * t);
			if (t > 0.01f) {
				Draw.chamfer(g, sx - 2, sy - 2, SLOT_WIDTH + 4, SLOT_HEIGHT + 4, Theme.accent(Math.round(70 * t)));
			}
			Draw.glass(g, sx, sy, SLOT_WIDTH, SLOT_HEIGHT, Colors.lerp(Theme.WINDOW, Theme.accent(225), t), Colors.lerp(Theme.WINDOW_BORDER, Theme.accent(), t));
			Draw.chamfer(g, sx + 3, sy + 3, 11, SLOT_HEIGHT - 6, Colors.lerp(Colors.withAlpha(0xFFFFFF, 22), Colors.withAlpha(0xFFFFFF, 60), t));
			Draw.centered(g, font, Component.literal(String.valueOf(index + 1)), sx + 9, -4, Colors.lerp(Theme.TEXT_MUTED, 0xFFFFFFFF, t));
			Component name = Draw.ellipsize(font, Component.literal(emote.name()), SLOT_WIDTH - 22);
			Draw.centered(g, font, name, sx + 16 + (SLOT_WIDTH - 18) / 2, -4, Colors.lerp(Theme.TEXT, 0xFFFFFFFF, t));
		}
		g.pose().popMatrix();
	}

	private void drawCenter(UiContext ui, List<Optional<Catalog.EmoteEntry>> slots, int cx, int cy, float open) {
		var g = ui.g();
		float t = ui.animate("emote.center", selected >= 0 ? 1f : 0f, 18f);
		int size = Math.round(CENTER_SIZE * (0.6f + 0.4f * open));
		int x = cx - size / 2;
		int y = cy - Math.round(size * SQUASH) / 2;
		int h = Math.round(size * SQUASH);
		Draw.shadow(g, x, y, size, h, 3, 0.7f);
		Draw.glass(g, x, y, size, h, Colors.lerp(Theme.WINDOW, Colors.lerp(Theme.WINDOW, Theme.accent(200), 0.35f), t), Colors.lerp(Theme.WINDOW_BORDER, Theme.accent(), t));
		boolean empty = slots.stream().noneMatch(Optional::isPresent);
		Component title;
		if (empty) {
			title = Component.translatable("pretupid.emotes.empty");
		} else if (selected >= 0) {
			title = Component.literal(slots.get(selected).map(Catalog.EmoteEntry::name).orElse(""));
		} else {
			title = Component.translatable("pretupid.emotes.title");
		}
		int labelWidth = 120;
		int labelY = cy + Math.round(RADIUS * SQUASH) + SLOT_HEIGHT / 2 + 12;
		Draw.shadow(g, cx - labelWidth / 2, labelY, labelWidth, 14, 2, 0.5f * open);
		Draw.chamfer(g, cx - labelWidth / 2, labelY, labelWidth, 14, Colors.withAlpha(0x0B0E14, Math.round(200 * open)));
		Draw.centered(g, font, Draw.ellipsize(font, title, labelWidth - 8), cx, labelY + 3, selected >= 0 ? Theme.accentText() : Theme.TEXT);
		if (!holdMode || released) {
			Component hint = Component.translatable("pretupid.emotes.hint_click");
			float hintScale = 0.75f;
			Draw.scaled(g, font, hint, cx - font.width(hint) * hintScale / 2f, labelY + 18, hintScale, Theme.TEXT_DIM, false);
		}
		if (selected >= 0) {
			Component number = Component.literal(String.valueOf(selected + 1));
			float numberScale = 2f * (1f + 0.25f * (1f - t));
			Draw.scaled(g, font, number, cx - font.width(number) * numberScale / 2f, cy - 4 * numberScale, numberScale, Colors.lerp(Theme.TEXT, 0xFFFFFFFF, t), true);
		} else {
			int diamond = Colors.withAlpha(0xFFFFFF, Math.round(130 * open));
			Draw.rect(g, cx - 1, cy - 4, 2, 8, diamond);
			Draw.rect(g, cx - 3, cy - 2, 6, 4, diamond);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		List<Optional<Catalog.EmoteEntry>> slots = slots();
		int index = selectionAt(slots, event.x(), event.y());
		if (index >= 0) {
			slots.get(index).ifPresent(this::play);
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	private void play(Catalog.EmoteEntry emote) {
		if (PretupidClient.get().settings().uiSounds.enabled()) {
			minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.6f, 0.35f));
		}
		Platform.setScreen(null);
		PretupidClient.get().emotes().playLocal(emote);
	}

	@Override
	protected boolean handleKey(KeyEvent event) {
		int key = event.key();
		List<Optional<Catalog.EmoteEntry>> slots = slots();
		for (int i = 0; i < EmoteWheel.SIZE; i++) {
			if (key == InputConstants.KEY_1 + i && slots.get(i).isPresent()) {
				play(slots.get(i).get());
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
		Platform.setScreen(null);
	}
}

package net.pretupid.client.ui;

import net.pretupid.client.platform.Gfx;
import net.pretupid.client.platform.Platform;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.platform.PlatformScreen;
import net.pretupid.client.setting.KeySetting;

public abstract class PretupidScreen extends PlatformScreen {
	protected final UiContext ui = new UiContext();
	protected final Screen parent;
	private long openedAt;
	private long lastFrame;
	private UiContext.Handler dragging;
	private Popup popup;
	private KeySetting capturing;

	protected PretupidScreen(Component title, Screen parent) {
		super(title);
		this.parent = parent;
	}

	@Override
	protected void init() {
		if (openedAt == 0L) {
			openedAt = System.currentTimeMillis();
		}
		lastFrame = System.nanoTime();
	}

	protected float openProgress() {
		if (!PretupidClient.get().settings().animations.enabled()) {
			return 1f;
		}
		float duration = 260f / PretupidClient.get().settings().animationSpeed.floatValue();
		return Math.clamp((System.currentTimeMillis() - openedAt) / duration, 0f, 1f);
	}

	@Override
	protected void renderScreenBackground(Gfx graphics, int mouseX, int mouseY, float partialTick) {
		boolean inWorld = minecraft.level != null;
		if (!inWorld) {
			drawPanorama(graphics, partialTick);
		}
		if (PretupidClient.get().settings().menuBlur.enabled()) {
			drawBlurredBackground(graphics);
		}
		float fade = Easing.outCubic(openProgress());
		Draw.verticalGradient(graphics, 0, 0, width, height,
			net.pretupid.client.util.Colors.multiplyAlpha(Theme.BACKDROP_TOP, fade),
			net.pretupid.client.util.Colors.multiplyAlpha(Theme.BACKDROP_BOTTOM, fade));
	}

	@Override
	protected final void renderScreen(Gfx graphics, int mouseX, int mouseY, float partialTick) {
		long now = System.nanoTime();
		float delta = (now - lastFrame) / 1_000_000_000f;
		lastFrame = now;
		var settings = PretupidClient.get().settings();
		ui.begin(graphics, font, mouseX, mouseY, delta, settings.animations.enabled(), settings.animationSpeed.floatValue());
		ui.setActiveLayer(popup != null ? 1 : 0);
		ui.setLayer(0);
		drawContent(ui);
		if (popup != null) {
			graphics.nextStratum();
			ui.setLayer(1);
			popup.draw(ui, width, height);
			ui.setLayer(0);
		}
		ui.end();
	}

	protected abstract void drawContent(UiContext ui);

	public void openPopup(Popup popup) {
		ui.blurInputs();
		this.popup = popup;
	}

	public void closePopup() {
		this.popup = null;
	}

	public boolean hasPopup() {
		return popup != null;
	}

	public void captureKey(KeySetting setting) {
		ui.blurInputs();
		this.capturing = setting;
	}

	public boolean isCapturing(KeySetting setting) {
		return capturing == setting;
	}

	public void playClick() {
		if (PretupidClient.get().settings().uiSounds.enabled()) {
			minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
		}
	}

	private static int normalizeButton(int raw) {
		if (raw == InputConstants.MOUSE_BUTTON_LEFT) {
			return 0;
		}
		if (raw == InputConstants.MOUSE_BUTTON_RIGHT) {
			return 1;
		}
		return 2;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int button = normalizeButton(event.button());
		if (capturing != null) {
			capturing = null;
			return true;
		}
		ui.blurInputs();
		for (UiContext.Hit hit : ui.hitsAt(event.x(), event.y())) {
			if (hit.handler().click(event.x(), event.y(), button)) {
				dragging = hit.handler();
				if (hit.handler().clickSound()) {
					playClick();
				}
				return true;
			}
		}
		if (popup != null && !ui.anyHitInLayer(event.x(), event.y(), 1)) {
			closePopup();
			return true;
		}
		return onBackgroundClick(event.x(), event.y(), button);
	}

	protected boolean onBackgroundClick(double mouseX, double mouseY, int button) {
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (dragging != null) {
			dragging.drag(event.x(), event.y());
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging != null) {
			UiContext.Handler handler = dragging;
			dragging = null;
			handler.release(event.x(), event.y());
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		for (UiContext.Hit hit : ui.hitsAt(mouseX, mouseY)) {
			if (hit.handler().scroll(scrollY)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (capturing != null) {
			int key = event.key();
			if (key == InputConstants.KEY_ESCAPE) {
				capturing = null;
			} else if (key == InputConstants.KEY_BACKSPACE || key == InputConstants.KEY_DELETE) {
				capturing.set(KeySetting.UNBOUND);
				capturing = null;
			} else {
				capturing.set(key);
				capturing = null;
			}
			playClick();
			return true;
		}
		TextInput input = ui.focusedInput();
		if (input != null && input.keyPressed(event)) {
			return true;
		}
		if (popup != null) {
			if (popup.keyPressed(event)) {
				return true;
			}
			if (event.isEscape()) {
				closePopup();
				return true;
			}
		}
		if (handleKey(event)) {
			return true;
		}
		return super.keyPressed(event);
	}

	protected boolean handleKey(KeyEvent event) {
		return false;
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		TextInput input = ui.focusedInput();
		if (input != null) {
			return input.charTyped(event.codepointAsString());
		}
		if (popup != null && popup.charTyped(event.codepointAsString())) {
			return true;
		}
		return super.charTyped(event);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		PretupidClient.get().config().saveAsync();
		Platform.setScreen(parent);
	}
}

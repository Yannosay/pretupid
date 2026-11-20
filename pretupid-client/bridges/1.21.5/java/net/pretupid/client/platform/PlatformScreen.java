package net.pretupid.client.platform;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.Util;
import net.pretupid.client.platform.input.CharacterEvent;
import net.pretupid.client.platform.input.KeyEvent;
import net.pretupid.client.platform.input.MouseButtonEvent;
import net.pretupid.client.platform.input.MouseButtonInfo;
import org.lwjgl.glfw.GLFW;

public abstract class PlatformScreen extends Screen {
	private long lastClick;
	private int lastButton = -1;
	private double lastX;
	private double lastY;

	protected PlatformScreen(Component title) {
		super(title);
	}

	@Override
	public final void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		renderScreen(Gfx.of(graphics), mouseX, mouseY, partialTick);
	}

	@Override
	public final void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		renderScreenBackground(Gfx.of(graphics), mouseX, mouseY, partialTick);
	}

	protected abstract void renderScreen(Gfx graphics, int mouseX, int mouseY, float partialTick);

	protected void renderScreenBackground(Gfx graphics, int mouseX, int mouseY, float partialTick) {
		super.renderBackground(graphics.raw(), mouseX, mouseY, partialTick);
	}

	protected void drawPanorama(Gfx graphics, float partialTick) {
		renderPanorama(graphics.raw(), partialTick);
	}

	protected void drawBlurredBackground(Gfx graphics) {
		renderBlurredBackground();
	}

	private static int heldModifiers() {
		int modifiers = 0;
		if (Screen.hasShiftDown()) {
			modifiers |= GLFW.GLFW_MOD_SHIFT;
		}
		if (Screen.hasControlDown()) {
			modifiers |= net.minecraft.client.Minecraft.ON_OSX ? GLFW.GLFW_MOD_SUPER : GLFW.GLFW_MOD_CONTROL;
		}
		if (Screen.hasAltDown()) {
			modifiers |= GLFW.GLFW_MOD_ALT;
		}
		return modifiers;
	}

	private static MouseButtonEvent event(double x, double y, int button) {
		return new MouseButtonEvent(x, y, new MouseButtonInfo(button, heldModifiers()));
	}

	@Override
	public final boolean mouseClicked(double x, double y, int button) {
		long now = Util.getMillis();
		boolean doubleClick = button == lastButton && now - lastClick < 250L && Math.abs(x - lastX) < 4 && Math.abs(y - lastY) < 4;
		lastClick = now;
		lastButton = button;
		lastX = x;
		lastY = y;
		return mouseClicked(event(x, y, button), doubleClick);
	}

	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		return super.mouseClicked(event.x(), event.y(), event.button());
	}

	@Override
	public final boolean mouseReleased(double x, double y, int button) {
		return mouseReleased(event(x, y, button));
	}

	public boolean mouseReleased(MouseButtonEvent event) {
		return super.mouseReleased(event.x(), event.y(), event.button());
	}

	@Override
	public final boolean mouseDragged(double x, double y, int button, double dragX, double dragY) {
		return mouseDragged(event(x, y, button), dragX, dragY);
	}

	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		return super.mouseDragged(event.x(), event.y(), event.button(), dragX, dragY);
	}

	@Override
	public final boolean keyPressed(int key, int scancode, int modifiers) {
		return keyPressed(new KeyEvent(key, scancode, modifiers));
	}

	public boolean keyPressed(KeyEvent event) {
		return super.keyPressed(event.key(), event.scancode(), event.modifiers());
	}

	@Override
	public final boolean keyReleased(int key, int scancode, int modifiers) {
		return keyReleased(new KeyEvent(key, scancode, modifiers));
	}

	public boolean keyReleased(KeyEvent event) {
		return super.keyReleased(event.key(), event.scancode(), event.modifiers());
	}

	@Override
	public final boolean charTyped(char character, int modifiers) {
		return charTyped(new CharacterEvent(character, modifiers));
	}

	public boolean charTyped(CharacterEvent event) {
		return super.charTyped((char) event.codepoint(), event.modifiers());
	}
}

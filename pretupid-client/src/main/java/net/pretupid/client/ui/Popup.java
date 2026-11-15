package net.pretupid.client.ui;

import net.minecraft.client.input.KeyEvent;

public interface Popup {
	void draw(UiContext ui, int screenWidth, int screenHeight);

	default boolean keyPressed(KeyEvent event) {
		return false;
	}

	default boolean charTyped(String text) {
		return false;
	}
}

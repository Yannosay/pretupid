package net.pretupid.client.ui.screen;

import net.minecraft.client.input.KeyEvent;
import net.pretupid.client.ui.UiContext;

public interface MenuPage {
	void draw(UiContext ui, int x, int y, int w, int h);

	default void onOpen() {
	}

	default boolean keyPressed(KeyEvent event) {
		return false;
	}

	default boolean onFilesDropped(java.util.List<java.nio.file.Path> paths) {
		return false;
	}

	default void release() {
	}
}

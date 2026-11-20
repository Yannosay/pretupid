package net.pretupid.client.platform.input;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public interface InputWithModifiers {
	int input();

	int modifiers();

	default boolean isSelection() {
		return input() == GLFW.GLFW_KEY_SPACE || input() == GLFW.GLFW_KEY_ENTER || input() == GLFW.GLFW_KEY_KP_ENTER;
	}

	default boolean isConfirmation() {
		return input() == GLFW.GLFW_KEY_ENTER || input() == GLFW.GLFW_KEY_KP_ENTER;
	}

	default boolean isEscape() {
		return input() == GLFW.GLFW_KEY_ESCAPE;
	}

	default boolean isLeft() {
		return input() == GLFW.GLFW_KEY_LEFT;
	}

	default boolean isRight() {
		return input() == GLFW.GLFW_KEY_RIGHT;
	}

	default boolean isUp() {
		return input() == GLFW.GLFW_KEY_UP;
	}

	default boolean isDown() {
		return input() == GLFW.GLFW_KEY_DOWN;
	}

	default boolean isCycleFocus() {
		return input() == GLFW.GLFW_KEY_TAB;
	}

	default boolean hasAltDown() {
		return (modifiers() & GLFW.GLFW_MOD_ALT) != 0;
	}

	default boolean hasShiftDown() {
		return (modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
	}

	default boolean hasControlDown() {
		return (modifiers() & (Minecraft.ON_OSX ? GLFW.GLFW_MOD_SUPER : GLFW.GLFW_MOD_CONTROL)) != 0;
	}

	default boolean isSelectAll() {
		return input() == GLFW.GLFW_KEY_A && hasControlDown() && !hasShiftDown() && !hasAltDown();
	}

	default boolean isCopy() {
		return input() == GLFW.GLFW_KEY_C && hasControlDown() && !hasShiftDown() && !hasAltDown();
	}

	default boolean isPaste() {
		return input() == GLFW.GLFW_KEY_V && hasControlDown() && !hasShiftDown() && !hasAltDown();
	}

	default boolean isCut() {
		return input() == GLFW.GLFW_KEY_X && hasControlDown() && !hasShiftDown() && !hasAltDown();
	}
}

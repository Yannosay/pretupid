package net.pretupid.client.platform.input;

public record KeyEvent(int key, int scancode, int modifiers) implements InputWithModifiers {
	@Override
	public int input() {
		return key;
	}
}

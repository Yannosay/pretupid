package net.pretupid.client.ui;

import java.util.function.Consumer;
import java.util.function.IntPredicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.pretupid.client.util.Colors;

public final class TextInput {
	private final int maxLength;
	private final IntPredicate allowed;
	private String value = "";
	private int cursor;
	private boolean selectAll;
	private boolean focused;
	private long lastEdit;
	private Consumer<String> onChange = text -> {
	};
	private Runnable onSubmit = () -> {
	};

	public TextInput(int maxLength, IntPredicate allowed) {
		this.maxLength = maxLength;
		this.allowed = allowed;
	}

	public TextInput(int maxLength) {
		this(maxLength, codePoint -> codePoint >= 0x20 && codePoint != 0x7F && codePoint != 0xA7);
	}

	public TextInput onChange(Consumer<String> listener) {
		this.onChange = listener;
		return this;
	}

	public TextInput onSubmit(Runnable listener) {
		this.onSubmit = listener;
		return this;
	}

	public String value() {
		return value;
	}

	public void setValue(String text) {
		String filtered = filter(text);
		if (!filtered.equals(value)) {
			value = filtered;
			cursor = value.length();
			onChange.accept(value);
		}
		cursor = Math.min(cursor, value.length());
	}

	public void setValueSilently(String text) {
		value = filter(text);
		cursor = value.length();
	}

	public boolean focused() {
		return focused;
	}

	public void setFocused(boolean focused) {
		if (this.focused != focused) {
			var manager = Minecraft.getInstance().textInputManager();
			if (focused) {
				manager.startTextInput(this);
			} else {
				manager.stopTextInput(this);
			}
		}
		this.focused = focused;
		this.selectAll = false;
		this.lastEdit = System.currentTimeMillis();
		if (focused) {
			cursor = value.length();
		}
	}

	public void selectAll() {
		selectAll = true;
		cursor = value.length();
	}

	public boolean charTyped(String text) {
		if (!focused) {
			return false;
		}
		insert(text);
		return true;
	}

	public boolean keyPressed(KeyEvent event) {
		if (!focused) {
			return false;
		}
		if (event.isSelectAll()) {
			selectAll();
			return true;
		}
		if (event.isPaste()) {
			insert(Minecraft.getInstance().keyboardHandler.getClipboard());
			return true;
		}
		if (event.isCopy()) {
			Minecraft.getInstance().keyboardHandler.setClipboard(value);
			return true;
		}
		if (event.isCut()) {
			Minecraft.getInstance().keyboardHandler.setClipboard(value);
			replaceAll("");
			return true;
		}
		if (event.isConfirmation()) {
			onSubmit.run();
			return true;
		}
		if (event.isEscape()) {
			setFocused(false);
			return true;
		}
		int key = event.key();
		if (key == com.mojang.blaze3d.platform.InputConstants.KEY_BACKSPACE) {
			if (selectAll) {
				replaceAll("");
			} else if (cursor > 0) {
				int start = value.offsetByCodePoints(cursor, -1);
				update(value.substring(0, start) + value.substring(cursor), start);
			}
			return true;
		}
		if (key == com.mojang.blaze3d.platform.InputConstants.KEY_DELETE) {
			if (selectAll) {
				replaceAll("");
			} else if (cursor < value.length()) {
				int end = value.offsetByCodePoints(cursor, 1);
				update(value.substring(0, cursor) + value.substring(end), cursor);
			}
			return true;
		}
		if (event.isLeft()) {
			selectAll = false;
			cursor = cursor > 0 ? value.offsetByCodePoints(cursor, -1) : 0;
			lastEdit = System.currentTimeMillis();
			return true;
		}
		if (event.isRight()) {
			selectAll = false;
			cursor = cursor < value.length() ? value.offsetByCodePoints(cursor, 1) : value.length();
			lastEdit = System.currentTimeMillis();
			return true;
		}
		if (key == com.mojang.blaze3d.platform.InputConstants.KEY_HOME) {
			selectAll = false;
			cursor = 0;
			return true;
		}
		if (key == com.mojang.blaze3d.platform.InputConstants.KEY_END) {
			selectAll = false;
			cursor = value.length();
			return true;
		}
		return !event.isCycleFocus();
	}

	private void insert(String text) {
		String filtered = filter(text);
		if (filtered.isEmpty()) {
			return;
		}
		if (selectAll) {
			replaceAll(filtered);
			return;
		}
		String next = value.substring(0, cursor) + filtered + value.substring(cursor);
		if (next.codePointCount(0, next.length()) > maxLength) {
			return;
		}
		update(next, cursor + filtered.length());
	}

	private void replaceAll(String text) {
		selectAll = false;
		update(filter(text), filter(text).length());
	}

	private void update(String next, int nextCursor) {
		selectAll = false;
		value = next;
		cursor = Math.clamp(nextCursor, 0, value.length());
		lastEdit = System.currentTimeMillis();
		onChange.accept(value);
	}

	private String filter(String text) {
		StringBuilder builder = new StringBuilder();
		text.codePoints().filter(allowed).limit(maxLength).forEach(builder::appendCodePoint);
		return builder.toString();
	}

	public void draw(UiContext ui, int x, int y, int w, int h, Component placeholder) {
		var g = ui.g();
		var font = ui.font();
		ui.registerInput(this);
		boolean hover = ui.interactive(x, y, w, h, new UiContext.Handler() {
			@Override
			public boolean click(double mouseX, double mouseY, int button) {
				if (button == 1) {
					setValue("");
				}
				setFocused(true);
				return true;
			}

			@Override
			public boolean clickSound() {
				return false;
			}
		});
		float focus = ui.animate("text." + System.identityHashCode(this), focused ? 1f : 0f, 14f);
		Draw.chamfer(g, x, y, w, h, Theme.INPUT);
		int border = Colors.lerp(hover ? Theme.SURFACE_HOVER : Theme.SURFACE_BORDER, Theme.accent(), focus);
		Draw.border(g, x, y, w, h, border);
		if (hover) {
			ui.setCursor(com.mojang.blaze3d.platform.cursor.CursorTypes.IBEAM);
		}
		int textX = x + 5;
		int textY = y + (h - 8) / 2;
		int innerWidth = w - 10;
		ui.pushClip(x + 2, y, w - 4, h);
		if (value.isEmpty() && !focused) {
			Draw.text(g, font, placeholder, textX, textY, Theme.TEXT_DIM);
		} else {
			String before = value.substring(0, cursor);
			int caretOffset = font.width(before);
			int scroll = Math.max(0, caretOffset - innerWidth + 2);
			if (selectAll && !value.isEmpty()) {
				Draw.rect(g, textX - scroll - 1, textY - 1, font.width(value) + 2, 10, Theme.accent(110));
			}
			Draw.text(g, font, value, textX - scroll, textY, Theme.TEXT);
			boolean blink = (System.currentTimeMillis() - lastEdit) % 1000 < 500;
			if (focused && blink) {
				Draw.rect(g, textX - scroll + caretOffset, textY - 1, 1, 10, Theme.TEXT);
			}
		}
		ui.popClip();
	}
}

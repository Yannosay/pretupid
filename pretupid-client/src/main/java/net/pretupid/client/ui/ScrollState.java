package net.pretupid.client.ui;

import net.pretupid.client.util.Colors;

public final class ScrollState {
	private float offset;
	private float target;
	private int contentHeight;
	private int viewportHeight;
	private boolean draggingBar;
	private double dragStartY;
	private float dragStartTarget;

	public int offset() {
		return Math.round(offset);
	}

	public void reset() {
		offset = 0f;
		target = 0f;
	}

	public void begin(UiContext ui, String id, int x, int y, int w, int h, int contentHeight) {
		this.contentHeight = contentHeight;
		this.viewportHeight = h;
		target = Math.clamp(target, 0f, maxScroll());
		offset = Easing.approach(offset, target, 22f, ui.deltaSeconds());
		offset = Math.clamp(offset, 0f, maxScroll());
		ui.hit(x, y, w, h, UiContext.Handler.onScroll(amount -> {
			if (maxScroll() <= 0) {
				return false;
			}
			target = Math.clamp(target - (float) amount * 24f, 0f, maxScroll());
			return true;
		}));
		ui.pushClip(x, y, w, h);
	}

	public void end(UiContext ui, int x, int y, int w, int h) {
		ui.popClip();
		float max = maxScroll();
		if (max <= 0) {
			return;
		}
		int trackX = x + w - 3;
		int barHeight = Math.max(16, Math.round(h * (h / (float) contentHeight)));
		int barY = y + Math.round((h - barHeight) * (offset / max));
		boolean hover = ui.interactive(trackX - 2, y, 6, h, new UiContext.Handler() {
			@Override
			public boolean click(double mouseX, double mouseY, int button) {
				if (button != 0) {
					return false;
				}
				draggingBar = true;
				dragStartY = mouseY;
				dragStartTarget = target;
				return true;
			}

			@Override
			public void drag(double mouseX, double mouseY) {
				if (draggingBar) {
					float ratio = (float) (mouseY - dragStartY) / Math.max(1, h - barHeight);
					target = Math.clamp(dragStartTarget + ratio * max, 0f, max);
					offset = target;
				}
			}

			@Override
			public void release(double mouseX, double mouseY) {
				draggingBar = false;
			}

			@Override
			public boolean clickSound() {
				return false;
			}
		});
		float emphasis = ui.animate("scroll." + System.identityHashCode(this), hover || draggingBar ? 1f : 0f, 12f);
		Draw.rect(ui.g(), trackX, y + 1, 2, h - 2, 0x10FFFFFF);
		Draw.rect(ui.g(), trackX, barY, 2, barHeight, Colors.lerp(0x50FFFFFF, Theme.accent(), emphasis));
	}

	public void scrollTo(int y) {
		target = Math.clamp(y, 0f, maxScroll());
	}

	private float maxScroll() {
		return Math.max(0, contentHeight - viewportHeight);
	}
}

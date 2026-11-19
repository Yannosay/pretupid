package net.pretupid.client.ui;

import net.pretupid.client.platform.Gfx;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

public final class UiContext {
	private final Map<String, Float> animations = new HashMap<>();
	private final ArrayDeque<Rect> clips = new ArrayDeque<>();
	private List<Hit> building = new ArrayList<>();
	private List<Hit> active = new ArrayList<>();
	private List<TextInput> inputsBuilding = new ArrayList<>();
	private List<TextInput> inputs = new ArrayList<>();
	private Gfx graphics;
	private Font font;
	private int mouseX;
	private int mouseY;
	private float deltaSeconds;
	private float speed = 1f;
	private boolean animate = true;
	private int layer;
	private int activeLayer;
	private Component tooltip;
	private Gfx.Cursor cursor = Gfx.Cursor.ARROW;

	public void begin(Gfx graphics, Font font, int mouseX, int mouseY, float deltaSeconds, boolean animate, float speed) {
		this.graphics = graphics;
		this.font = font;
		this.mouseX = mouseX;
		this.mouseY = mouseY;
		this.deltaSeconds = Math.clamp(deltaSeconds, 0f, 0.25f);
		this.animate = animate;
		this.speed = speed;
		this.layer = 0;
		this.tooltip = null;
		this.cursor = Gfx.Cursor.ARROW;
		this.clips.clear();
		this.building = new ArrayList<>();
		this.inputsBuilding = new ArrayList<>();
	}

	public void end() {
		active = building;
		for (TextInput input : inputs) {
			if (input.focused() && !inputsBuilding.contains(input)) {
				input.setFocused(false);
			}
		}
		inputs = inputsBuilding;
		if (tooltip != null) {
			graphics.tooltip(font, tooltip, mouseX, mouseY);
		}
		if (cursor != Gfx.Cursor.ARROW) {
			graphics.cursor(cursor);
		}
	}

	public Gfx g() {
		return graphics;
	}

	public Font font() {
		return font;
	}

	public int mouseX() {
		return mouseX;
	}

	public int mouseY() {
		return mouseY;
	}

	public float deltaSeconds() {
		return deltaSeconds;
	}

	public void setLayer(int layer) {
		this.layer = layer;
	}

	public int layer() {
		return layer;
	}

	public void setActiveLayer(int activeLayer) {
		this.activeLayer = activeLayer;
	}

	public int activeLayer() {
		return activeLayer;
	}

	public float animate(String id, float target, float rate) {
		if (!animate) {
			animations.put(id, target);
			return target;
		}
		float current = animations.getOrDefault(id, target);
		float next = Easing.approach(current, target, rate * speed, deltaSeconds);
		animations.put(id, next);
		return next;
	}

	public boolean animationsEnabled() {
		return animate;
	}

	public float animateFrom(String id, float initial, float target, float rate) {
		animations.putIfAbsent(id, initial);
		return animate(id, target, rate);
	}

	public void resetAnimation(String id) {
		animations.remove(id);
	}

	public void setAnimation(String id, float value) {
		animations.put(id, value);
	}

	public boolean hovered(int x, int y, int w, int h) {
		if (layer < activeLayer) {
			return false;
		}
		if (mouseX < x || mouseY < y || mouseX >= x + w || mouseY >= y + h) {
			return false;
		}
		Rect clip = clips.peek();
		return clip == null || clip.contains(mouseX, mouseY);
	}

	public void hit(int x, int y, int w, int h, Handler handler) {
		building.add(new Hit(x, y, w, h, clips.peek(), layer, handler));
	}

	public boolean interactive(int x, int y, int w, int h, Handler handler) {
		hit(x, y, w, h, handler);
		boolean hover = hovered(x, y, w, h);
		if (hover && handler.pointer()) {
			cursor = Gfx.Cursor.POINTING_HAND;
		}
		return hover;
	}

	public void registerInput(TextInput input) {
		inputsBuilding.add(input);
	}

	public TextInput focusedInput() {
		for (TextInput input : inputs) {
			if (input.focused()) {
				return input;
			}
		}
		return null;
	}

	public void blurInputs() {
		for (TextInput input : inputs) {
			input.setFocused(false);
		}
	}

	public void setCursor(Gfx.Cursor type) {
		this.cursor = type;
	}

	public void tooltip(Component text) {
		this.tooltip = text;
	}

	public void pushClip(int x, int y, int w, int h) {
		Rect requested = new Rect(x, y, Math.max(0, w), Math.max(0, h));
		Rect parent = clips.peek();
		Rect effective = parent == null ? requested : parent.intersect(requested);
		clips.push(effective);
		graphics.enableScissor(effective.x(), effective.y(), effective.x() + effective.w(), effective.y() + effective.h());
	}

	public void popClip() {
		clips.pop();
		graphics.disableScissor();
	}

	public Hit findHit(double x, double y) {
		for (int i = active.size() - 1; i >= 0; i--) {
			Hit hit = active.get(i);
			if (hit.layer() >= activeLayer && hit.contains(x, y)) {
				return hit;
			}
		}
		return null;
	}

	public List<Hit> hitsAt(double x, double y) {
		List<Hit> result = new ArrayList<>();
		for (int i = active.size() - 1; i >= 0; i--) {
			Hit hit = active.get(i);
			if (hit.layer() >= activeLayer && hit.contains(x, y)) {
				result.add(hit);
			}
		}
		return result;
	}

	public boolean anyHitInLayer(double x, double y, int targetLayer) {
		for (Hit hit : active) {
			if (hit.layer() == targetLayer && hit.contains(x, y)) {
				return true;
			}
		}
		return false;
	}

	public record Rect(int x, int y, int w, int h) {
		public boolean contains(double px, double py) {
			return px >= x && py >= y && px < x + w && py < y + h;
		}

		public Rect intersect(Rect other) {
			int nx = Math.max(x, other.x);
			int ny = Math.max(y, other.y);
			int nr = Math.min(x + w, other.x + other.w);
			int nb = Math.min(y + h, other.y + other.h);
			return new Rect(nx, ny, Math.max(0, nr - nx), Math.max(0, nb - ny));
		}
	}

	public record Hit(int x, int y, int w, int h, Rect clip, int layer, Handler handler) {
		public boolean contains(double px, double py) {
			if (px < x || py < y || px >= x + w || py >= y + h) {
				return false;
			}
			return clip == null || clip.contains(px, py);
		}
	}

	public interface Handler {
		default boolean click(double mouseX, double mouseY, int button) {
			return false;
		}

		default void drag(double mouseX, double mouseY) {
		}

		default void release(double mouseX, double mouseY) {
		}

		default boolean scroll(double amount) {
			return false;
		}

		default boolean pointer() {
			return true;
		}

		default boolean clickSound() {
			return true;
		}

		static Handler onClick(Runnable action) {
			return new Handler() {
				@Override
				public boolean click(double mouseX, double mouseY, int button) {
					if (button != 0) {
						return false;
					}
					action.run();
					return true;
				}
			};
		}

		static Handler onScroll(java.util.function.DoublePredicate action) {
			return new Handler() {
				@Override
				public boolean scroll(double amount) {
					return action.test(amount);
				}

				@Override
				public boolean pointer() {
					return false;
				}
			};
		}
	}
}

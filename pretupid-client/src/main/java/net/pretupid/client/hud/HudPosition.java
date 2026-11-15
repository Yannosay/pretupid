package net.pretupid.client.hud;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public final class HudPosition {
	public enum Anchor {
		START,
		CENTER,
		END
	}

	private final Anchor defaultAnchorX;
	private final Anchor defaultAnchorY;
	private final int defaultOffsetX;
	private final int defaultOffsetY;
	private Anchor anchorX;
	private Anchor anchorY;
	private int offsetX;
	private int offsetY;

	public HudPosition(Anchor anchorX, int offsetX, Anchor anchorY, int offsetY) {
		this.defaultAnchorX = anchorX;
		this.defaultAnchorY = anchorY;
		this.defaultOffsetX = offsetX;
		this.defaultOffsetY = offsetY;
		reset();
	}

	public void reset() {
		anchorX = defaultAnchorX;
		anchorY = defaultAnchorY;
		offsetX = defaultOffsetX;
		offsetY = defaultOffsetY;
	}

	public boolean isDefault() {
		return anchorX == defaultAnchorX && anchorY == defaultAnchorY && offsetX == defaultOffsetX && offsetY == defaultOffsetY;
	}

	public int resolveX(int screenWidth, int width) {
		int x = switch (anchorX) {
			case START -> offsetX;
			case CENTER -> (screenWidth - width) / 2 + offsetX;
			case END -> screenWidth - width - offsetX;
		};
		return Math.clamp(x, 0, Math.max(0, screenWidth - width));
	}

	public int resolveY(int screenHeight, int height) {
		int y = switch (anchorY) {
			case START -> offsetY;
			case CENTER -> (screenHeight - height) / 2 + offsetY;
			case END -> screenHeight - height - offsetY;
		};
		return Math.clamp(y, 0, Math.max(0, screenHeight - height));
	}

	public void place(int x, int y, int width, int height, int screenWidth, int screenHeight) {
		int clampedX = Math.clamp(x, 0, Math.max(0, screenWidth - width));
		int clampedY = Math.clamp(y, 0, Math.max(0, screenHeight - height));
		int centerX = clampedX + width / 2;
		int centerY = clampedY + height / 2;
		anchorX = centerX < screenWidth / 3 ? Anchor.START : centerX > screenWidth * 2 / 3 ? Anchor.END : Anchor.CENTER;
		anchorY = centerY < screenHeight / 3 ? Anchor.START : centerY > screenHeight * 2 / 3 ? Anchor.END : Anchor.CENTER;
		offsetX = switch (anchorX) {
			case START -> clampedX;
			case CENTER -> clampedX - (screenWidth - width) / 2;
			case END -> screenWidth - width - clampedX;
		};
		offsetY = switch (anchorY) {
			case START -> clampedY;
			case CENTER -> clampedY - (screenHeight - height) / 2;
			case END -> screenHeight - height - clampedY;
		};
	}

	public JsonObject toJson() {
		JsonObject object = new JsonObject();
		object.addProperty("anchorX", anchorX.name());
		object.addProperty("anchorY", anchorY.name());
		object.addProperty("offsetX", offsetX);
		object.addProperty("offsetY", offsetY);
		return object;
	}

	public void fromJson(JsonElement element) {
		if (element == null || !element.isJsonObject()) {
			return;
		}
		JsonObject object = element.getAsJsonObject();
		anchorX = parse(object.get("anchorX"), anchorX);
		anchorY = parse(object.get("anchorY"), anchorY);
		if (object.has("offsetX") && object.get("offsetX").isJsonPrimitive()) {
			offsetX = Math.clamp(object.get("offsetX").getAsInt(), -4096, 4096);
		}
		if (object.has("offsetY") && object.get("offsetY").isJsonPrimitive()) {
			offsetY = Math.clamp(object.get("offsetY").getAsInt(), -4096, 4096);
		}
	}

	private static Anchor parse(JsonElement element, Anchor fallback) {
		if (element == null || !element.isJsonPrimitive()) {
			return fallback;
		}
		for (Anchor anchor : Anchor.values()) {
			if (anchor.name().equalsIgnoreCase(element.getAsString())) {
				return anchor;
			}
		}
		return fallback;
	}
}

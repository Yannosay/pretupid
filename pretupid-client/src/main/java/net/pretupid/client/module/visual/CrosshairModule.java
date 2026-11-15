package net.pretupid.client.module.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.pretupid.client.module.Category;
import net.pretupid.client.module.Module;
import net.pretupid.client.setting.BooleanSetting;
import net.pretupid.client.setting.ColorSetting;
import net.pretupid.client.setting.EnumSetting;
import net.pretupid.client.setting.NumberSetting;
import net.pretupid.client.setting.TextSetting;
import net.pretupid.client.ui.Draw;

public final class CrosshairModule extends Module {
	public static final int GRID = 15;
	private static final int PATTERN_CHARS = (GRID * GRID + 3) / 4;

	public enum Shape {
		CROSS,
		DOT,
		CIRCLE,
		T_SHAPE,
		SQUARE,
		CUSTOM
	}

	public final EnumSetting<Shape> shape = add(new EnumSetting<>("shape", Shape.CROSS));
	public final NumberSetting size = add(new NumberSetting("size", 5, 1, 16, 1, "px"));
	public final NumberSetting gap = add(new NumberSetting("gap", 2, 0, 8, 1, "px"));
	public final NumberSetting thickness = add(new NumberSetting("thickness", 1, 1, 4, 1, "px"));
	public final NumberSetting pixelScale = add(new NumberSetting("pixel_scale", 1, 1, 4, 1, "x"));
	public final BooleanSetting centerDot = add(new BooleanSetting("center_dot", false));
	public final BooleanSetting outline = add(new BooleanSetting("outline", true));
	public final ColorSetting outlineColor = add(new ColorSetting("outline_color", 0x90000000, true));
	public final ColorSetting color = add(new ColorSetting("color", 0xFFFFFFFF, true));
	public final BooleanSetting targetColor = add(new BooleanSetting("target_color", true));
	public final ColorSetting targetColorValue = add(new ColorSetting("target_color_value", 0xFFF87171));
	public final BooleanSetting dynamic = add(new BooleanSetting("dynamic", false));
	public final TextSetting pattern = add(new TextSetting("pattern", defaultPattern(), PATTERN_CHARS));

	private float spread;

	public CrosshairModule() {
		super("crosshair", Category.VISUAL, "bow", false);
		pattern.visibleWhen(() -> false);
		size.visibleWhen(() -> shape.get() != Shape.CUSTOM && shape.get() != Shape.DOT);
		gap.visibleWhen(() -> shape.get() != Shape.CUSTOM && shape.get() != Shape.DOT);
		thickness.visibleWhen(() -> shape.get() != Shape.CUSTOM);
		pixelScale.visibleWhen(() -> shape.get() == Shape.CUSTOM);
		centerDot.visibleWhen(() -> shape.get() != Shape.CUSTOM && shape.get() != Shape.DOT);
		outlineColor.visibleWhen(outline::enabled);
		targetColorValue.visibleWhen(targetColor::enabled);
	}

	private static String defaultPattern() {
		boolean[] cells = new boolean[GRID * GRID];
		int center = GRID / 2;
		for (int i = 0; i < GRID; i++) {
			if (Math.abs(i - center) >= 2 && Math.abs(i - center) <= 5) {
				cells[center * GRID + i] = true;
				cells[i * GRID + center] = true;
			}
		}
		return encode(cells);
	}

	public static String encode(boolean[] cells) {
		StringBuilder builder = new StringBuilder(PATTERN_CHARS);
		for (int i = 0; i < PATTERN_CHARS; i++) {
			int nibble = 0;
			for (int bit = 0; bit < 4; bit++) {
				int index = i * 4 + bit;
				if (index < cells.length && cells[index]) {
					nibble |= 1 << (3 - bit);
				}
			}
			builder.append(Character.forDigit(nibble, 16));
		}
		return builder.toString();
	}

	public static boolean[] decode(String text) {
		boolean[] cells = new boolean[GRID * GRID];
		for (int i = 0; i < Math.min(text.length(), PATTERN_CHARS); i++) {
			int nibble = Character.digit(text.charAt(i), 16);
			if (nibble < 0) {
				continue;
			}
			for (int bit = 0; bit < 4; bit++) {
				int index = i * 4 + bit;
				if (index < cells.length) {
					cells[index] = (nibble & (1 << (3 - bit))) != 0;
				}
			}
		}
		return cells;
	}

	public boolean replacesVanilla(Minecraft minecraft) {
		return isEnabled()
			&& minecraft.options.getCameraType().isFirstPerson()
			&& minecraft.player != null
			&& !minecraft.player.isSpectator()
			&& !minecraft.getDebugOverlay().showDebugScreen();
	}

	@Override
	public void onTick(Minecraft minecraft) {
		if (!dynamic.enabled() || minecraft.player == null) {
			spread = 0f;
			return;
		}
		Vec3 motion = minecraft.player.getDeltaMovement();
		float target = (float) Math.min(4.0, Math.sqrt(motion.x * motion.x + motion.z * motion.z) * 14.0);
		if (!minecraft.player.onGround()) {
			target = Math.max(target, 2.5f);
		}
		if (minecraft.player.isSwinging()) {
			target = Math.max(target, 3f);
		}
		spread += (target - spread) * 0.35f;
	}

	public void render(GuiGraphicsExtractor graphics, Minecraft minecraft) {
		int argb = targetColor.enabled() && minecraft.hitResult instanceof EntityHitResult ? targetColorValue.resolve() : color.resolve();
		draw(graphics, graphics.guiWidth() / 2, graphics.guiHeight() / 2, argb, Math.round(spread));
	}

	public void draw(GuiGraphicsExtractor graphics, int cx, int cy, int argb, int extraGap) {
		int length = size.intValue();
		int spacing = gap.intValue() + extraGap;
		int stroke = thickness.intValue();
		int half = stroke / 2;
		int odd = stroke % 2 == 0 ? 0 : 1;
		switch (shape.get()) {
			case CROSS -> {
				bar(graphics, cx - half, cy - spacing - length, stroke, length, argb);
				bar(graphics, cx - half, cy + spacing + odd, stroke, length, argb);
				bar(graphics, cx - spacing - length, cy - half, length, stroke, argb);
				bar(graphics, cx + spacing + odd, cy - half, length, stroke, argb);
			}
			case T_SHAPE -> {
				bar(graphics, cx - half, cy + spacing + odd, stroke, length, argb);
				bar(graphics, cx - spacing - length, cy - half, length, stroke, argb);
				bar(graphics, cx + spacing + odd, cy - half, length, stroke, argb);
			}
			case DOT -> bar(graphics, cx - half - 1, cy - half - 1, stroke + 2, stroke + 2, argb);
			case SQUARE -> {
				int radius = spacing + length / 2;
				bar(graphics, cx - radius, cy - radius, radius * 2 + 1, stroke, argb);
				bar(graphics, cx - radius, cy + radius - stroke + 1, radius * 2 + 1, stroke, argb);
				bar(graphics, cx - radius, cy - radius + stroke, stroke, radius * 2 + 1 - stroke * 2, argb);
				bar(graphics, cx + radius - stroke + 1, cy - radius + stroke, stroke, radius * 2 + 1 - stroke * 2, argb);
			}
			case CIRCLE -> circle(graphics, cx, cy, spacing + length / 2 + 1, stroke, argb);
			case CUSTOM -> custom(graphics, cx, cy, argb, extraGap);
		}
		if (centerDot.enabled() && shape.get() != Shape.DOT && shape.get() != Shape.CUSTOM) {
			bar(graphics, cx - half, cy - half, stroke, stroke, argb);
		}
	}

	private void custom(GuiGraphicsExtractor graphics, int cx, int cy, int argb, int extraGap) {
		boolean[] cells = decode(pattern.get());
		int scale = pixelScale.intValue();
		int center = GRID / 2;
		int outlineArgb = outlineColor.resolve();
		if (outline.enabled()) {
			for (int row = 0; row < GRID; row++) {
				for (int column = 0; column < GRID; column++) {
					if (cells[row * GRID + column]) {
						int[] position = cellPosition(cx, cy, row, column, center, scale, extraGap);
						Draw.rect(graphics, position[0] - 1, position[1] - 1, scale + 2, scale + 2, outlineArgb);
					}
				}
			}
		}
		for (int row = 0; row < GRID; row++) {
			for (int column = 0; column < GRID; column++) {
				if (cells[row * GRID + column]) {
					int[] position = cellPosition(cx, cy, row, column, center, scale, extraGap);
					Draw.rect(graphics, position[0], position[1], scale, scale, argb);
				}
			}
		}
	}

	private static int[] cellPosition(int cx, int cy, int row, int column, int center, int scale, int extraGap) {
		int dx = column - center;
		int dy = row - center;
		int pushX = dx == 0 ? 0 : Integer.signum(dx) * extraGap;
		int pushY = dy == 0 ? 0 : Integer.signum(dy) * extraGap;
		return new int[] {cx + dx * scale - scale / 2 + pushX, cy + dy * scale - scale / 2 + pushY};
	}

	private void bar(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int argb) {
		if (outline.enabled()) {
			Draw.rect(graphics, x - 1, y - 1, w + 2, h + 2, outlineColor.resolve());
		}
		Draw.rect(graphics, x, y, w, h, argb);
	}

	private void circle(GuiGraphicsExtractor graphics, int cx, int cy, int radius, int stroke, int argb) {
		int inner = Math.max(0, radius - stroke);
		int outlineArgb = outlineColor.resolve();
		for (int dy = -radius - 1; dy <= radius + 1; dy++) {
			for (int dx = -radius - 1; dx <= radius + 1; dx++) {
				double distance = Math.sqrt(dx * dx + dy * dy);
				if (distance <= radius + 0.5 && distance > inner - 0.5) {
					Draw.rect(graphics, cx + dx, cy + dy, 1, 1, argb);
				} else if (outline.enabled() && distance <= radius + 1.5 && distance > inner - 1.5) {
					Draw.rect(graphics, cx + dx, cy + dy, 1, 1, outlineArgb);
				}
			}
		}
	}
}

package net.pretupid.client.platform;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;

public final class Gfx {
	public enum Cursor {
		ARROW,
		POINTING_HAND,
		IBEAM,
		RESIZE_EW,
		RESIZE_ALL
	}

	private final GuiGraphicsExtractor graphics;

	private Gfx(GuiGraphicsExtractor graphics) {
		this.graphics = graphics;
	}

	public static Gfx of(GuiGraphicsExtractor graphics) {
		return new Gfx(graphics);
	}

	public GuiGraphicsExtractor raw() {
		return graphics;
	}

	public Matrix3x2fStack pose() {
		return graphics.pose();
	}

	public int guiWidth() {
		return graphics.guiWidth();
	}

	public int guiHeight() {
		return graphics.guiHeight();
	}

	public void fill(int x1, int y1, int x2, int y2, int color) {
		graphics.fill(x1, y1, x2, y2, color);
	}

	public void fillGradient(int x1, int y1, int x2, int y2, int top, int bottom) {
		graphics.fillGradient(x1, y1, x2, y2, top, bottom);
	}

	public void text(Font font, String text, int x, int y, int color, boolean shadow) {
		graphics.text(font, text, x, y, color, shadow);
	}

	public void text(Font font, Component text, int x, int y, int color, boolean shadow) {
		graphics.text(font, text, x, y, color, shadow);
	}

	public void text(Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow) {
		graphics.text(font, text, x, y, color, shadow);
	}

	public void blit(Identifier texture, int x, int y, float u, float v, int width, int height, int textureWidth, int textureHeight) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, width, height, textureWidth, textureHeight);
	}

	public void blit(Identifier texture, int x, int y, float u, float v, int width, int height, int textureWidth, int textureHeight, int color) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, width, height, textureWidth, textureHeight, color);
	}

	public void blit(Identifier texture, int x, int y, float u, float v, int width, int height, int regionWidth, int regionHeight, int textureWidth, int textureHeight) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, width, height, regionWidth, regionHeight, textureWidth, textureHeight);
	}

	public void blit(Identifier texture, int x, int y, float u, float v, int width, int height, int regionWidth, int regionHeight, int textureWidth, int textureHeight, int color) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, width, height, regionWidth, regionHeight, textureWidth, textureHeight, color);
	}

	public void blit(Identifier texture, int x1, int y1, int x2, int y2, float u1, float v1, float u2, float v2) {
		graphics.blit(texture, x1, y1, x2, y2, u1, v1, u2, v2);
	}

	public void blitSprite(Identifier sprite, int x, int y, int width, int height, int color) {
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, width, height, color);
	}

	public void item(ItemStack stack, int x, int y) {
		graphics.item(stack, x, y);
	}

	public void entity(LivingEntity entity, int x1, int y1, int x2, int y2, int size, float yOffset, float mouseX, float mouseY) {
		InventoryScreen.extractEntityInInventoryFollowsMouse(graphics, x1, y1, x2, y2, size, yOffset, mouseX, mouseY, entity);
	}

	public void enableScissor(int x1, int y1, int x2, int y2) {
		graphics.enableScissor(x1, y1, x2, y2);
	}

	public void disableScissor() {
		graphics.disableScissor();
	}

	public void nextStratum() {
		graphics.nextStratum();
	}

	public void tooltip(Font font, Component text, int x, int y) {
		graphics.setTooltipForNextFrame(font, text, x, y);
	}

	public void cursor(Cursor cursor) {
		graphics.requestCursor(switch (cursor) {
			case ARROW -> CursorTypes.ARROW;
			case POINTING_HAND -> CursorTypes.POINTING_HAND;
			case IBEAM -> CursorTypes.IBEAM;
			case RESIZE_EW -> CursorTypes.RESIZE_EW;
			case RESIZE_ALL -> CursorTypes.RESIZE_ALL;
		});
	}
}

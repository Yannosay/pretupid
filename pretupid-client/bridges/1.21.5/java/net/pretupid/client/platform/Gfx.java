package net.pretupid.client.platform;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.client.Minecraft;
import net.pretupid.client.mixin.platform.GuiGraphicsInvoker;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public final class Gfx {
	public enum Cursor {
		ARROW,
		POINTING_HAND,
		IBEAM,
		RESIZE_EW,
		RESIZE_ALL
	}

	private final GuiGraphics graphics;

	private Gfx(GuiGraphics graphics) {
		this.graphics = graphics;
	}

	public static Gfx of(GuiGraphics graphics) {
		return new Gfx(graphics);
	}

	public GuiGraphics raw() {
		return graphics;
	}

	public GuiPose pose() {
		return new GuiPose(graphics.pose());
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
		graphics.drawString(font, text, x, y, color, shadow);
	}

	public void text(Font font, Component text, int x, int y, int color, boolean shadow) {
		graphics.drawString(font, text, x, y, color, shadow);
	}

	public void text(Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow) {
		graphics.drawString(font, text, x, y, color, shadow);
	}

	public void blit(Identifier texture, int x, int y, float u, float v, int width, int height, int textureWidth, int textureHeight) {
		graphics.blit(RenderType::guiTextured, texture, x, y, u, v, width, height, textureWidth, textureHeight);
	}

	public void blit(Identifier texture, int x, int y, float u, float v, int width, int height, int textureWidth, int textureHeight, int color) {
		graphics.blit(RenderType::guiTextured, texture, x, y, u, v, width, height, textureWidth, textureHeight, color);
	}

	public void blit(Identifier texture, int x, int y, float u, float v, int width, int height, int regionWidth, int regionHeight, int textureWidth, int textureHeight) {
		graphics.blit(RenderType::guiTextured, texture, x, y, u, v, width, height, regionWidth, regionHeight, textureWidth, textureHeight);
	}

	public void blit(Identifier texture, int x, int y, float u, float v, int width, int height, int regionWidth, int regionHeight, int textureWidth, int textureHeight, int color) {
		graphics.blit(RenderType::guiTextured, texture, x, y, u, v, width, height, regionWidth, regionHeight, textureWidth, textureHeight, color);
	}

	public void blit(Identifier texture, int x1, int y1, int x2, int y2, float u1, float v1, float u2, float v2) {
		((GuiGraphicsInvoker) graphics).pretupid$innerBlit(RenderType::guiTextured, texture, x1, x2, y1, y2, u1, u2, v1, v2, -1);
	}

	public void blitSprite(Identifier sprite, int x, int y, int width, int height, int color) {
		graphics.blitSprite(RenderType::guiTextured, sprite, x, y, width, height, color);
	}

	public void item(ItemStack stack, int x, int y) {
		graphics.renderItem(stack, x, y);
	}

	public void entity(LivingEntity entity, int x1, int y1, int x2, int y2, int size, float yOffset, float mouseX, float mouseY) {
		InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, x1, y1, x2, y2, size, yOffset, mouseX, mouseY, entity);
	}

	public void enableScissor(int x1, int y1, int x2, int y2) {
		graphics.enableScissor(x1, y1, x2, y2);
	}

	public void disableScissor() {
		graphics.disableScissor();
	}

	public void nextStratum() {
		graphics.flush();
	}

	public void tooltip(Font font, Component text, int x, int y) {
		graphics.renderTooltip(font, text, x, y);
	}

	public void cursor(Cursor cursor) {
	}

	public void mobEffect(Holder<MobEffect> effect, int x, int y, int width, int height, int color) {
		graphics.blitSprite(RenderType::guiTextured, Minecraft.getInstance().getMobEffectTextures().get(effect), x, y, width, height, color);
	}
}

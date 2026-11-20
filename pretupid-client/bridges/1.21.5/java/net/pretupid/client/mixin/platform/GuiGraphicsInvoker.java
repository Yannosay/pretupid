package net.pretupid.client.mixin.platform;

import java.util.function.Function;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GuiGraphics.class)
public interface GuiGraphicsInvoker {
	@Invoker("innerBlit")
	void pretupid$innerBlit(Function<ResourceLocation, RenderType> renderType, ResourceLocation texture, int x1, int x2, int y1, int y2, float u1, float u2, float v1, float v2, int color);
}

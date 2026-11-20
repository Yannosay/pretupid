package net.pretupid.client.mixin.platform;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.pretupid.client.platform.PlatformHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ScreenBackgroundMixin {
	@Inject(method = "renderBackground", at = @At("TAIL"))
	private void pretupid$afterBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		PlatformHud.drawAfterBackground((Screen) (Object) this, graphics);
	}
}

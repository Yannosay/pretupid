package net.pretupid.client.mixin.platform;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.pretupid.client.platform.Gfx;
import net.pretupid.client.platform.PlatformHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class GuiLayersMixin {
	@Unique
	private boolean pretupid$vanillaCrosshair;

	@Invoker("renderCrosshair")
	abstract void pretupid$renderCrosshair(GuiGraphics graphics, DeltaTracker deltaTracker);

	@Inject(method = "renderChat", at = @At("HEAD"))
	private void pretupid$beforeChat(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
		PlatformHud.drawBeforeChat(graphics, deltaTracker);
	}

	@Inject(method = "render", at = @At("TAIL"))
	private void pretupid$last(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
		PlatformHud.drawLast(graphics, deltaTracker);
	}

	@Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
	private void pretupid$crosshair(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
		PlatformHud.Replacement replacement = PlatformHud.crosshair();
		if (replacement == null || pretupid$vanillaCrosshair) {
			return;
		}
		ci.cancel();
		replacement.draw(Gfx.of(graphics), deltaTracker, () -> {
			pretupid$vanillaCrosshair = true;
			try {
				pretupid$renderCrosshair(graphics, deltaTracker);
			} finally {
				pretupid$vanillaCrosshair = false;
			}
		});
	}
}

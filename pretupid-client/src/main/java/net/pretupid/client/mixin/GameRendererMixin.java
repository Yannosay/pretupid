package net.pretupid.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;

import net.pretupid.client.PretupidClient;
import net.pretupid.client.module.visual.NoHurtCamModule;
import net.pretupid.client.module.visual.ZoomModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
	private void pretupid$disableHurtCam(CallbackInfo ci) {
		PretupidClient client = PretupidClient.getOrNull();
		if (client != null && client.modules().get(NoHurtCamModule.class).isEnabled()) {
			ci.cancel();
		}
	}

	@Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
	private void pretupid$hideHandWhileZoomed(CallbackInfo ci) {
		PretupidClient client = PretupidClient.getOrNull();
		if (client == null) {
			return;
		}
		if (client.cinematic().controlsCamera() || client.screenies().stagedView() != null
			|| client.modules().get(net.pretupid.client.module.visual.FreecamModule.class).active()) {
			ci.cancel();
			return;
		}
		ZoomModule zoom = client.modules().get(ZoomModule.class);
		if (zoom.isEnabled() && zoom.hideHand()) {
			ci.cancel();
		}
	}
}

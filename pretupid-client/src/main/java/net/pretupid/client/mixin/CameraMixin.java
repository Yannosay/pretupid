package net.pretupid.client.mixin;

import net.minecraft.client.Camera;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.module.visual.ZoomModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraMixin {
	@Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
	private void pretupid$applyZoom(float partialTick, CallbackInfoReturnable<Float> cir) {
		PretupidClient client = PretupidClient.getOrNull();
		if (client == null) {
			return;
		}
		ZoomModule zoom = client.modules().get(ZoomModule.class);
		if (zoom.isEnabled()) {
			float multiplier = zoom.fovMultiplier();
			if (multiplier < 1f) {
				cir.setReturnValue(cir.getReturnValueF() * multiplier);
			}
		}
	}
}

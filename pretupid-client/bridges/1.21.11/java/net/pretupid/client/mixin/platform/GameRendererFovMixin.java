package net.pretupid.client.mixin.platform;

import net.minecraft.client.renderer.GameRenderer;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.cinematic.Shot;
import net.pretupid.client.module.visual.CinematicModule;
import net.pretupid.client.module.visual.ZoomModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererFovMixin {
	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
	private void pretupid$applyZoom(CallbackInfoReturnable<Float> cir) {
		PretupidClient client = PretupidClient.getOrNull();
		if (client == null) {
			return;
		}
		if (pretupid$cinematicActive()) {
			Shot.View view = client.cinematic().view();
			if (view != null) {
				cir.setReturnValue(view.fov());
				return;
			}
		}
		ZoomModule zoom = client.modules().get(ZoomModule.class);
		if (zoom.isEnabled()) {
			float multiplier = zoom.fovMultiplier();
			if (multiplier < 1f) {
				cir.setReturnValue(cir.getReturnValueF() * multiplier);
			}
		}
	}

	private static boolean pretupid$cinematicActive() {
		PretupidClient client = PretupidClient.getOrNull();
		return client != null && client.modules().get(CinematicModule.class).isEnabled() && client.cinematic().controlsCamera();
	}
}

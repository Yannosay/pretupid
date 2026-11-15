package net.pretupid.client.mixin;

import net.minecraft.client.Camera;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.module.visual.FreelookModule;
import net.pretupid.client.module.visual.ZoomModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraMixin {
	@ModifyVariable(method = "setRotation", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private float pretupid$freelookYaw(float yaw) {
		FreelookModule freelook = pretupid$freelook();
		return freelook == null ? yaw : freelook.yaw();
	}

	@ModifyVariable(method = "setRotation", at = @At("HEAD"), argsOnly = true, ordinal = 1)
	private float pretupid$freelookPitch(float pitch) {
		FreelookModule freelook = pretupid$freelook();
		return freelook == null ? pitch : freelook.pitch();
	}

	private static FreelookModule pretupid$freelook() {
		PretupidClient client = PretupidClient.getOrNull();
		if (client == null) {
			return null;
		}
		FreelookModule freelook = client.modules().get(FreelookModule.class);
		return freelook.isEnabled() && freelook.active() ? freelook : null;
	}

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

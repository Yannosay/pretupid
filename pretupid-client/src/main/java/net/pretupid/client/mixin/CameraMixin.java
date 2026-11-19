package net.pretupid.client.mixin;

import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import net.pretupid.client.module.visual.FreecamModule;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.module.visual.FreelookModule;
import net.pretupid.client.module.visual.ZoomModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow
	private boolean detached;

	@Shadow
	protected abstract void setPosition(Vec3 position);

	@Shadow
	protected abstract void setRotation(float yaw, float pitch);

	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void pretupid$freecam(float partialTick, CallbackInfo ci) {
		FreecamModule freecam = pretupid$freecam();
		if (freecam == null) {
			return;
		}
		setRotation(freecam.yaw(), freecam.pitch());
		setPosition(freecam.position(partialTick));
		detached = true;
	}

	@Inject(method = "isDetached", at = @At("HEAD"), cancellable = true)
	private void pretupid$freecamDetached(CallbackInfoReturnable<Boolean> cir) {
		if (pretupid$freecam() != null) {
			cir.setReturnValue(true);
		}
	}

	private static FreecamModule pretupid$freecam() {
		PretupidClient client = PretupidClient.getOrNull();
		if (client == null) {
			return null;
		}
		FreecamModule freecam = client.modules().get(FreecamModule.class);
		return freecam.isEnabled() && freecam.active() ? freecam : null;
	}
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

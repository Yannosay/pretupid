package net.pretupid.client.mixin.platform;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import net.pretupid.client.cinematic.Shot;
import net.pretupid.client.module.visual.CinematicModule;
import net.pretupid.client.module.visual.FreecamModule;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Final;
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
	@Final
	private Quaternionf rotation;

	@Shadow
	protected abstract void setPosition(Vec3 position);

	@Shadow
	protected abstract void setRotation(float yaw, float pitch);

	@Inject(method = "setup", at = @At("TAIL"))
	private void pretupid$freecam(CallbackInfo ci, @Local(argsOnly = true) float partialTick) {
		Shot.View shot = pretupid$shot();
		if (shot != null) {
			setRotation(shot.yaw(), shot.pitch());
			if (shot.roll() != 0f) {
				rotation.rotateZ((float) Math.toRadians(shot.roll()));
			}
			setPosition(shot.position());
			detached = true;
			return;
		}
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
		if (pretupid$freecam() != null || pretupid$cinematicActive()) {
			cir.setReturnValue(true);
		}
	}

	private static boolean pretupid$cinematicActive() {
		PretupidClient client = PretupidClient.getOrNull();
		return client != null && client.modules().get(CinematicModule.class).isEnabled() && client.cinematic().controlsCamera();
	}

	private static Shot.View pretupid$shot() {
		return pretupid$cinematicActive() ? PretupidClient.get().cinematic().view() : null;
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
}

package net.pretupid.client.mixin;

import net.minecraft.client.Minecraft;
import net.pretupid.client.PretupidClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.pretupid.client.module.visual.FreecamModule;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@Inject(method = "runTick", at = @At("HEAD"))
	private void pretupid$beginFrame(boolean advanceGameTime, CallbackInfo ci) {
		PretupidClient client = PretupidClient.getOrNull();
		if (client != null) {
			client.onFrame((Minecraft) (Object) this);
		}
	}

	@Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
	private void pretupid$freecamAttack(CallbackInfoReturnable<Boolean> cir) {
		if (pretupid$freecamActive()) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
	private void pretupid$freecamContinueAttack(boolean down, CallbackInfo ci) {
		if (pretupid$freecamActive()) {
			ci.cancel();
		}
	}

	@Inject(method = {"startUseItem", "pickBlockOrEntity"}, at = @At("HEAD"), cancellable = true)
	private void pretupid$freecamUse(CallbackInfo ci) {
		if (pretupid$freecamActive()) {
			ci.cancel();
		}
	}

	private static boolean pretupid$freecamActive() {
		PretupidClient client = PretupidClient.getOrNull();
		return client != null && client.modules().get(FreecamModule.class).active();
	}
}

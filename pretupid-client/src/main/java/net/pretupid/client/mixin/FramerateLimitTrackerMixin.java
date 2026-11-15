package net.pretupid.client.mixin;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import net.minecraft.client.Minecraft;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.module.performance.DynamicFpsModule;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FramerateLimitTracker.class)
public abstract class FramerateLimitTrackerMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Inject(method = "getFramerateLimit", at = @At("RETURN"), cancellable = true)
	private void pretupid$applyDynamicLimit(CallbackInfoReturnable<Integer> cir) {
		PretupidClient client = PretupidClient.getOrNull();
		if (client == null) {
			return;
		}
		DynamicFpsModule module = client.modules().get(DynamicFpsModule.class);
		if (module.isEnabled()) {
			cir.setReturnValue(module.limit(minecraft, cir.getReturnValueI()));
		}
	}

	@Inject(method = "onInputReceived", at = @At("HEAD"))
	private void pretupid$recordInput(CallbackInfo ci) {
		PretupidClient client = PretupidClient.getOrNull();
		if (client != null) {
			client.modules().get(DynamicFpsModule.class).recordInput();
		}
	}
}

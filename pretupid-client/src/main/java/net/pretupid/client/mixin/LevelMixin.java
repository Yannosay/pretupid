package net.pretupid.client.mixin;

import net.minecraft.world.level.Level;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.module.visual.ClearWeatherModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class LevelMixin {
	@Shadow
	public abstract boolean isClientSide();

	@Inject(method = "getRainLevel", at = @At("HEAD"), cancellable = true)
	private void pretupid$clearRain(float partialTick, CallbackInfoReturnable<Float> cir) {
		if (!isClientSide()) {
			return;
		}
		PretupidClient client = PretupidClient.getOrNull();
		if (client != null && client.modules().get(ClearWeatherModule.class).hideRain()) {
			cir.setReturnValue(0.0F);
		}
	}

	@Inject(method = "getThunderLevel", at = @At("HEAD"), cancellable = true)
	private void pretupid$clearThunder(float partialTick, CallbackInfoReturnable<Float> cir) {
		if (!isClientSide()) {
			return;
		}
		PretupidClient client = PretupidClient.getOrNull();
		if (client != null && client.modules().get(ClearWeatherModule.class).hideThunder()) {
			cir.setReturnValue(0.0F);
		}
	}
}

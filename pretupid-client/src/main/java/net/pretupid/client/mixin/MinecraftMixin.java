package net.pretupid.client.mixin;

import net.minecraft.client.Minecraft;
import net.pretupid.client.PretupidClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@Inject(method = "runTick", at = @At("HEAD"))
	private void pretupid$beginFrame(boolean advanceGameTime, CallbackInfo ci) {
		PretupidClient client = PretupidClient.getOrNull();
		if (client != null) {
			client.onFrame((Minecraft) (Object) this);
		}
	}
}

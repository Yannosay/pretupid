package net.pretupid.client.mixin;

import net.minecraft.client.sounds.MusicManager;
import net.pretupid.client.PretupidClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MusicManager.class)
public abstract class MusicManagerMixin {
	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void pretupid$holdForMusicPlayer(CallbackInfo callback) {
		PretupidClient client = PretupidClient.getOrNull();
		if (client != null && client.music().overridesVanilla()) {
			callback.cancel();
		}
	}
}

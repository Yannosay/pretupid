package net.pretupid.client.mixin;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.module.performance.ParticleLimiterModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {
	@Inject(method = "add", at = @At("HEAD"), cancellable = true)
	private void pretupid$limitParticles(Particle particle, CallbackInfo ci) {
		PretupidClient client = PretupidClient.getOrNull();
		if (client == null) {
			return;
		}
		ParticleLimiterModule module = client.modules().get(ParticleLimiterModule.class);
		if (module.isEnabled() && !module.allowSpawn()) {
			ci.cancel();
		}
	}
}

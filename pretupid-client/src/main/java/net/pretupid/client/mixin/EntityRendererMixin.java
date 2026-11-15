package net.pretupid.client.mixin;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.module.performance.EntityCullingModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	private void pretupid$cullDistantEntities(Entity entity, Frustum frustum, double cameraX, double cameraY, double cameraZ, float partialTick, CallbackInfoReturnable<Boolean> cir) {
		PretupidClient client = PretupidClient.getOrNull();
		if (client == null) {
			return;
		}
		EntityCullingModule module = client.modules().get(EntityCullingModule.class);
		if (module.isEnabled() && module.shouldCull(entity, cameraX, cameraY, cameraZ)) {
			cir.setReturnValue(false);
		}
	}
}

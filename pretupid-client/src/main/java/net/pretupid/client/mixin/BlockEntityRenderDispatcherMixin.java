package net.pretupid.client.mixin;

import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.module.performance.EntityCullingModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderDispatcherMixin {
	@Inject(method = "tryExtractRenderState", at = @At("HEAD"), cancellable = true)
	private <E extends BlockEntity, S extends BlockEntityRenderState> void pretupid$cullDistantBlockEntities(
		E blockEntity, float partialTick, ModelFeatureRenderer.CrumblingOverlay crumbling, boolean outline, CallbackInfoReturnable<S> cir) {
		PretupidClient client = PretupidClient.getOrNull();
		if (client == null) {
			return;
		}
		EntityCullingModule module = client.modules().get(EntityCullingModule.class);
		if (module.isEnabled() && module.shouldCullBlockEntity(blockEntity)) {
			cir.setReturnValue(null);
		}
	}
}

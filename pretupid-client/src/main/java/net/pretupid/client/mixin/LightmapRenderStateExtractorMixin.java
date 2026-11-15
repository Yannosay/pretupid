package net.pretupid.client.mixin;

import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.module.visual.FullbrightModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightmapRenderStateExtractor.class)
public abstract class LightmapRenderStateExtractorMixin {
	@Inject(method = "extract", at = @At("TAIL"))
	private void pretupid$fullbright(LightmapRenderState state, float partialTick, CallbackInfo ci) {
		PretupidClient client = PretupidClient.getOrNull();
		if (client != null) {
			client.modules().get(FullbrightModule.class).apply(state);
		}
	}
}

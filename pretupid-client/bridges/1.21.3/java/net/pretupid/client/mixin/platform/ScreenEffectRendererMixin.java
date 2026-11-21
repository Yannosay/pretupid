package net.pretupid.client.mixin.platform;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.module.visual.LowFireModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenEffectRenderer.class)
public abstract class ScreenEffectRendererMixin {
	@Inject(method = "renderFire", at = @At("HEAD"))
	private static void pretupid$lowerFire(Minecraft minecraft, PoseStack poseStack, CallbackInfo ci) {
		poseStack.pushPose();
		PretupidClient client = PretupidClient.getOrNull();
		if (client != null) {
			LowFireModule module = client.modules().get(LowFireModule.class);
			if (module.isEnabled()) {
				poseStack.translate(0.0F, -module.offset(), 0.0F);
			}
		}
	}

	@Inject(method = "renderFire", at = @At("RETURN"))
	private static void pretupid$restoreFire(Minecraft minecraft, PoseStack poseStack, CallbackInfo ci) {
		poseStack.popPose();
	}
}

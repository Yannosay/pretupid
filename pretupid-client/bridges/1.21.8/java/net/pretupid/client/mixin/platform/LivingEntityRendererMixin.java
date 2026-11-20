package net.pretupid.client.mixin.platform;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.module.visual.OwnNametagModule;
import net.pretupid.client.platform.Platform;
import net.pretupid.client.cosmetic.CosmeticLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
	@Inject(method = "render(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
		at = @At("HEAD"))
	private void pretupid$submitPet(LivingEntityRenderState state, PoseStack poseStack, MultiBufferSource buffers, int light, CallbackInfo ci) {
		if (state instanceof PlayerRenderState player) {
			CosmeticLayer.submitPet(poseStack, buffers, player, light);
		}
	}

	@Inject(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z", at = @At("HEAD"), cancellable = true)
	private void pretupid$showOwnNametag(LivingEntity entity, double distance, CallbackInfoReturnable<Boolean> cir) {
		Minecraft minecraft = Minecraft.getInstance();
		if (entity != minecraft.player || minecraft.options.getCameraType().isFirstPerson() || Platform.hudHidden()) {
			return;
		}
		PretupidClient client = PretupidClient.getOrNull();
		if (client != null && client.modules().get(OwnNametagModule.class).isEnabled() && !entity.isInvisible()) {
			cir.setReturnValue(true);
		}
	}
}

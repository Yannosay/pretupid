package net.pretupid.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.pretupid.client.cosmetic.CosmeticStateHolder;
import net.pretupid.client.cosmetic.model.PhysicsCapeModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(CapeLayer.class)
public abstract class CapeLayerMixin {
	@Unique
	private PhysicsCapeModel pretupid$physicsModel;

	@WrapOperation(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/AvatarRenderState;FF)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;III)V"))
	private void pretupid$physicsCape(SubmitNodeCollector collector, Model<?> model, Object state, PoseStack poseStack, RenderType renderType, int light, int overlay,
		int outline, Operation<Void> original) {
		if (state instanceof CosmeticStateHolder holder && holder.pretupid$capeAngles() != null) {
			if (pretupid$physicsModel == null) {
				pretupid$physicsModel = PhysicsCapeModel.create();
			}
			original.call(collector, pretupid$physicsModel, state, poseStack, renderType, light, overlay, outline);
			return;
		}
		original.call(collector, model, state, poseStack, renderType, light, overlay, outline);
	}
}

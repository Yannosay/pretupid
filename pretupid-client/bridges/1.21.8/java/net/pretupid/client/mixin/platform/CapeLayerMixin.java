package net.pretupid.client.mixin.platform;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.pretupid.client.cosmetic.CosmeticStateHolder;
import net.pretupid.client.cosmetic.model.PhysicsCapeModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(CapeLayer.class)
public abstract class CapeLayerMixin {
	@Unique
	private PhysicsCapeModel pretupid$physicsModel;

	@WrapOperation(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/renderer/entity/state/PlayerRenderState;FF)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/HumanoidModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;II)V"))
	private void pretupid$physicsCape(HumanoidModel<?> model, PoseStack poseStack, VertexConsumer consumer, int light, int overlay, Operation<Void> original,
		@Local(argsOnly = true) PlayerRenderState state) {
		if (state instanceof CosmeticStateHolder holder && holder.pretupid$capeAngles() != null) {
			if (pretupid$physicsModel == null) {
				pretupid$physicsModel = PhysicsCapeModel.create();
			}
			pretupid$physicsModel.setupAnim(state);
			original.call(pretupid$physicsModel, poseStack, consumer, light, overlay);
			return;
		}
		original.call(model, poseStack, consumer, light, overlay);
	}
}

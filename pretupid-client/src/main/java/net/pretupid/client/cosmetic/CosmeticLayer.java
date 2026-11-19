package net.pretupid.client.cosmetic;

import net.pretupid.client.platform.Platform;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.pretupid.client.cosmetic.model.CosmeticModels;

public final class CosmeticLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	public CosmeticLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
		super(parent);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
		if (state.isInvisible || !(state instanceof CosmeticStateHolder holder)) {
			return;
		}
		String hat = holder.pretupid$hat();
		if (hat != null) {
			draw(CosmeticModels.get(Catalog.WearableKind.HAT, hat), poseStack, collector, light, state);
		}
		String back = holder.pretupid$back();
		if (back != null && !state.chestEquipment.has(DataComponents.GLIDER)) {
			draw(CosmeticModels.get(Catalog.WearableKind.BACK, back), poseStack, collector, light, state);
		}
	}

	public static void submitPet(PoseStack poseStack, SubmitNodeCollector collector, AvatarRenderState state) {
		if (state.isInvisible || !(state instanceof CosmeticStateHolder holder) || holder.pretupid$pet() == null) {
			return;
		}
		PetFollower.Placement placement = holder.pretupid$petPlacement();
		CosmeticModels.Entry entry = placement == null ? null : CosmeticModels.get(Catalog.WearableKind.PET, holder.pretupid$pet());
		if (entry == null) {
			return;
		}
		poseStack.pushPose();
		poseStack.translate(placement.x(), placement.y(), placement.z());
		Platform.rotateDegrees(poseStack, Axis.YP, 180f - placement.yaw());
		poseStack.scale(-state.scale, -state.scale, state.scale);
		draw(entry, poseStack, collector, state.lightCoords, state);
		poseStack.popPose();
	}

	private static void draw(CosmeticModels.Entry entry, PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state) {
		if (entry == null) {
			return;
		}
		Platform.submitModel(collector, entry.model(), state, poseStack, entry.model().renderType(entry.texture()), light, OverlayTexture.NO_OVERLAY, state.outlineColor);
	}
}

package net.pretupid.client.cosmetic;

import com.mojang.blaze3d.vertex.PoseStack;
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
		String back = holder.pretupid$back();
		if (back != null && !state.chestEquipment.has(DataComponents.GLIDER)) {
			draw(CosmeticModels.back(back), poseStack, collector, light, state);
		}
		String pet = holder.pretupid$pet();
		if (pet != null) {
			draw(CosmeticModels.pet(pet), poseStack, collector, light, state);
		}
	}

	private static void draw(CosmeticModels.Entry entry, PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state) {
		if (entry == null) {
			return;
		}
		collector.submitModel(entry.model(), state, poseStack, entry.model().renderType(entry.texture()), light, OverlayTexture.NO_OVERLAY, state.outlineColor);
	}
}

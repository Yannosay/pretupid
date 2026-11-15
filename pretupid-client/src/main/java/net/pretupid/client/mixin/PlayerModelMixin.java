package net.pretupid.client.mixin;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.pretupid.client.cosmetic.EmoteAnimation;
import net.pretupid.client.cosmetic.EmotePoseHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
	private void pretupid$applyEmote(AvatarRenderState state, CallbackInfo ci) {
		if (!(state instanceof EmotePoseHolder holder)) {
			return;
		}
		float[][] pose = holder.pretupid$emotePose();
		if (pose == null) {
			return;
		}
		HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
		float weight = pose[pose.length - 1][0];
		pretupid$blend(model.head, pose[EmoteAnimation.Part.HEAD.ordinal()], weight);
		pretupid$blend(model.body, pose[EmoteAnimation.Part.BODY.ordinal()], weight);
		pretupid$blend(model.rightArm, pose[EmoteAnimation.Part.RIGHT_ARM.ordinal()], weight);
		pretupid$blend(model.leftArm, pose[EmoteAnimation.Part.LEFT_ARM.ordinal()], weight);
		pretupid$blend(model.rightLeg, pose[EmoteAnimation.Part.RIGHT_LEG.ordinal()], weight);
		pretupid$blend(model.leftLeg, pose[EmoteAnimation.Part.LEFT_LEG.ordinal()], weight);
	}

	@Unique
	private static void pretupid$blend(ModelPart part, float[] rotation, float weight) {
		if (rotation == null) {
			return;
		}
		part.xRot += (rotation[0] - part.xRot) * weight;
		part.yRot += (rotation[1] - part.yRot) * weight;
		part.zRot += (rotation[2] - part.zRot) * weight;
	}
}

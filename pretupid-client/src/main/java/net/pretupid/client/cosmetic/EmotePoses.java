package net.pretupid.client.cosmetic;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;

public final class EmotePoses {
	private EmotePoses() {
	}

	public static void apply(HumanoidModel<?> model, AvatarRenderState state) {
		if (!(state instanceof EmotePoseHolder holder)) {
			return;
		}
		float[][] pose = holder.pretupid$emotePose();
		if (pose == null) {
			return;
		}
		float weight = pose[pose.length - 1][0];
		blend(model.head, pose[EmoteAnimation.Part.HEAD.ordinal()], weight);
		blend(model.body, pose[EmoteAnimation.Part.BODY.ordinal()], weight);
		blend(model.rightArm, pose[EmoteAnimation.Part.RIGHT_ARM.ordinal()], weight);
		blend(model.leftArm, pose[EmoteAnimation.Part.LEFT_ARM.ordinal()], weight);
		float legWeight = weight * (1f - Math.clamp(state.walkAnimationSpeed * 2.5f, 0f, 1f));
		blend(model.rightLeg, pose[EmoteAnimation.Part.RIGHT_LEG.ordinal()], legWeight);
		blend(model.leftLeg, pose[EmoteAnimation.Part.LEFT_LEG.ordinal()], legWeight);
	}

	private static void blend(ModelPart part, float[] rotation, float weight) {
		if (rotation == null) {
			return;
		}
		part.xRot += (rotation[0] - part.xRot) * weight;
		part.yRot += (rotation[1] - part.yRot) * weight;
		part.zRot += (rotation[2] - part.zRot) * weight;
	}
}

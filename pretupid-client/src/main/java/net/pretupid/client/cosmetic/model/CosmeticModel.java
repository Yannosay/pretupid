package net.pretupid.client.cosmetic.model;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.pretupid.client.cosmetic.EmotePoses;

public final class CosmeticModel extends HumanoidModel<AvatarRenderState> {
	private static final float DEGREES = (float) (Math.PI / 180.0);

	private record Animated(ModelPart part, List<Shape.Channel> channels) {
	}

	private final List<Animated> animated = new ArrayList<>();

	public CosmeticModel(ModelPart root, Shape shape) {
		super(root, RenderTypes::entityCutout);
		ModelPart anchor = switch (shape.anchor()) {
			case HEAD -> head;
			case BODY -> body;
			case ROOT -> root;
		};
		for (Shape.Node node : shape.nodes()) {
			index(anchor, node);
		}
	}

	private void index(ModelPart parent, Shape.Node node) {
		ModelPart part = parent.getChild(node.name());
		if (!node.channels().isEmpty()) {
			animated.add(new Animated(part, List.copyOf(node.channels())));
		}
		for (Shape.Node child : node.children()) {
			index(part, child);
		}
	}

	@Override
	public void setupAnim(AvatarRenderState state) {
		resetPose();
		super.setupAnim(state);
		EmotePoses.apply(this, state);
		if (animated.isEmpty()) {
			return;
		}
		float time = state.ageInTicks;
		float motion = Mth.clamp(state.walkAnimationSpeed, 0f, 1f);
		boolean crouching = state.isCrouching;
		for (Animated entry : animated) {
			ModelPart part = entry.part();
			for (Shape.Channel channel : entry.channels()) {
				float value = channel.value(time, motion, crouching);
				switch (channel.property()) {
					case ROT_X -> part.xRot += value * DEGREES;
					case ROT_Y -> part.yRot += value * DEGREES;
					case ROT_Z -> part.zRot += value * DEGREES;
					case X -> part.x += value;
					case Y -> part.y += value;
					case Z -> part.z += value;
					case SCALE -> {
						part.xScale *= value;
						part.yScale *= value;
						part.zScale *= value;
					}
					case SCALE_X -> part.xScale *= value;
					case SCALE_Y -> part.yScale *= value;
					case SCALE_Z -> part.zScale *= value;
				}
			}
		}
	}
}

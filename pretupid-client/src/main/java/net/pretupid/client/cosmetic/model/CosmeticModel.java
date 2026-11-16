package net.pretupid.client.cosmetic.model;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.pretupid.client.cosmetic.EmotePoses;

public final class CosmeticModel extends HumanoidModel<AvatarRenderState> {
	@FunctionalInterface
	public interface Animator {
		void animate(CosmeticModel model, AvatarRenderState state);
	}

	private final Map<String, ModelPart> parts = new HashMap<>();
	private final Animator animator;

	public CosmeticModel(ModelPart root, Shape shape, Animator animator) {
		super(root, RenderTypes::entityCutout);
		this.animator = animator;
		ModelPart anchor = shape.anchor() == Shape.Anchor.BODY ? body : root;
		for (Shape.Node node : shape.nodes()) {
			index(anchor, node);
		}
	}

	private void index(ModelPart parent, Shape.Node node) {
		ModelPart part = parent.getChild(node.name);
		parts.put(node.name, part);
		for (Shape.Node child : node.children) {
			index(part, child);
		}
	}

	public ModelPart part(String name) {
		ModelPart part = parts.get(name);
		if (part == null) {
			throw new IllegalArgumentException("Unknown cosmetic part " + name);
		}
		return part;
	}

	@Override
	public void setupAnim(AvatarRenderState state) {
		resetPose();
		super.setupAnim(state);
		EmotePoses.apply(this, state);
		animator.animate(this, state);
	}
}

package net.pretupid.client.cosmetic.model;

import java.util.EnumSet;
import java.util.Set;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.Direction;
import net.pretupid.client.cosmetic.CapePhysics;
import net.pretupid.client.cosmetic.CosmeticStateHolder;
import org.joml.Quaternionf;

public final class PhysicsCapeModel extends HumanoidModel<AvatarRenderState> {
	private static final float DEGREES = (float) (Math.PI / 180.0);
	private static final Set<Direction> SIDES = EnumSet.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST);

	private final ModelPart[] segments = new ModelPart[CapePhysics.SEGMENTS];

	private PhysicsCapeModel(ModelPart root) {
		super(root, RenderTypes::entitySolid);
		ModelPart part = body;
		for (int i = 0; i < segments.length; i++) {
			part = part.getChild("cape_" + i);
			segments[i] = part;
		}
	}

	public static PhysicsCapeModel create() {
		MeshDefinition mesh = ShapeBaker.humanoidSkeleton();
		PartDefinition parent = mesh.getRoot().getChild("body");
		int height = CapePhysics.SEGMENT_HEIGHT;
		for (int i = 0; i < CapePhysics.SEGMENTS; i++) {
			boolean first = i == 0;
			boolean last = i == CapePhysics.SEGMENTS - 1;
			Set<Direction> faces = EnumSet.copyOf(SIDES);
			if (first) {
				faces.add(Direction.DOWN);
			}
			CubeListBuilder cubes = CubeListBuilder.create().texOffs(0, i * height).addBox(-5f, 0f, -1f, 10f, height, 1f, faces);
			if (last) {
				cubes.texOffs(0, 0).addBox(-5f, height, -1f, 10f, 0f, 1f, EnumSet.of(Direction.UP));
			}
			PartPose pose = first ? PartPose.offsetAndRotation(0f, 0f, 2f, 0f, (float) Math.PI, 0f) : PartPose.offset(0f, height, 0f);
			parent = parent.addOrReplaceChild("cape_" + i, cubes, pose);
		}
		return new PhysicsCapeModel(LayerDefinition.create(mesh, 64, 32).bakeRoot());
	}

	@Override
	public void setupAnim(AvatarRenderState state) {
		super.setupAnim(state);
		float[] angles = state instanceof CosmeticStateHolder holder ? holder.pretupid$capeAngles() : null;
		float root = angles == null ? 6f + state.capeLean / 2f + state.capeFlap : angles[0];
		segments[0].rotateBy(new Quaternionf()
			.rotateY((float) -Math.PI)
			.rotateX(root * DEGREES)
			.rotateZ(state.capeLean2 / 2f * DEGREES)
			.rotateY((180f - state.capeLean2 / 2f) * DEGREES));
		if (angles == null) {
			return;
		}
		for (int i = 1; i < segments.length; i++) {
			segments[i].xRot = -(angles[i] - angles[i - 1]) * DEGREES;
		}
	}
}

package net.pretupid.client.cosmetic.model;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

public final class ShapeBaker {
	private static final int MAX_TEXTURE_SIZE = 256;
	private static final int PADDING = 1;

	private ShapeBaker() {
	}

	public record Baked(ModelPart root, NativeImage texture) {
	}

	public static Baked bake(Shape shape) {
		List<Shape.Box> boxes = new ArrayList<>();
		for (Shape.Node node : shape.nodes()) {
			collect(node, boxes);
		}
		int size = 32;
		Map<Shape.Box, int[]> layout = pack(boxes, size);
		while (layout == null && size < MAX_TEXTURE_SIZE) {
			size *= 2;
			layout = pack(boxes, size);
		}
		if (layout == null) {
			throw new IllegalStateException("Cosmetic shape does not fit into a " + MAX_TEXTURE_SIZE + " pixel texture");
		}
		MeshDefinition mesh = humanoidSkeleton();
		PartDefinition anchor = shape.anchor() == Shape.Anchor.BODY ? mesh.getRoot().getChild("body") : mesh.getRoot();
		for (Shape.Node node : shape.nodes()) {
			addNode(anchor, node, layout);
		}
		NativeImage texture = new NativeImage(size, size, true);
		for (Shape.Box box : boxes) {
			int[] uv = layout.get(box);
			paint(texture, uv[0], uv[1], box);
		}
		return new Baked(LayerDefinition.create(mesh, size, size).bakeRoot(), texture);
	}

	private static MeshDefinition humanoidSkeleton() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
		head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
		root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
		root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-5f, 2f, 0f));
		root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(5f, 2f, 0f));
		root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-1.9f, 12f, 0f));
		root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(1.9f, 12f, 0f));
		return mesh;
	}

	private static void collect(Shape.Node node, List<Shape.Box> boxes) {
		boxes.addAll(node.boxes);
		for (Shape.Node child : node.children) {
			collect(child, boxes);
		}
	}

	private static void addNode(PartDefinition parent, Shape.Node node, Map<Shape.Box, int[]> layout) {
		CubeListBuilder cubes = CubeListBuilder.create();
		for (Shape.Box box : node.boxes) {
			int[] uv = layout.get(box);
			cubes.texOffs(uv[0], uv[1]).addBox(box.x(), box.y(), box.z(), box.width(), box.height(), box.depth());
		}
		PartDefinition part = parent.addOrReplaceChild(node.name, cubes, PartPose.offsetAndRotation(node.x, node.y, node.z, node.xRot, node.yRot, node.zRot));
		for (Shape.Node child : node.children) {
			addNode(part, child, layout);
		}
	}

	private static Map<Shape.Box, int[]> pack(List<Shape.Box> boxes, int size) {
		List<Shape.Box> order = new ArrayList<>(boxes);
		order.sort((a, b) -> Integer.compare(footprintHeight(b), footprintHeight(a)));
		Map<Shape.Box, int[]> layout = new IdentityHashMap<>();
		int x = 0;
		int y = 0;
		int rowHeight = 0;
		for (Shape.Box box : order) {
			int width = footprintWidth(box) + PADDING;
			int height = footprintHeight(box) + PADDING;
			if (width > size) {
				return null;
			}
			if (x + width > size) {
				x = 0;
				y += rowHeight;
				rowHeight = 0;
			}
			if (y + height > size) {
				return null;
			}
			layout.put(box, new int[] {x, y});
			x += width;
			rowHeight = Math.max(rowHeight, height);
		}
		return layout;
	}

	private static int footprintWidth(Shape.Box box) {
		return 2 * (box.width() + box.depth());
	}

	private static int footprintHeight(Shape.Box box) {
		return box.depth() + box.height();
	}

	private static void paint(NativeImage image, int u, int v, Shape.Box box) {
		int w = box.width();
		int h = box.height();
		int d = box.depth();
		paintFace(image, box, Pattern.Face.TOP, u + d, v, w, d);
		paintFace(image, box, Pattern.Face.BOTTOM, u + d + w, v, w, d);
		paintFace(image, box, Pattern.Face.EAST, u, v + d, d, h);
		paintFace(image, box, Pattern.Face.NORTH, u + d, v + d, w, h);
		paintFace(image, box, Pattern.Face.WEST, u + d + w, v + d, d, h);
		paintFace(image, box, Pattern.Face.SOUTH, u + 2 * d + w, v + d, w, h);
	}

	private static void paintFace(NativeImage image, Shape.Box box, Pattern.Face face, int left, int top, int width, int height) {
		Pattern pattern = box.pattern();
		for (int fy = 0; fy < height; fy++) {
			for (int fx = 0; fx < width; fx++) {
				int px = left + fx;
				int py = top + fy;
				int hash = hash(px, py);
				int color = pattern.useAccent(face, fx, fy, width, height, hash) ? box.accent() : box.color();
				if (pattern.shaded()) {
					float light = face.light;
					boolean edge = width > 2 && height > 2 && (fx == 0 || fy == 0 || fx == width - 1 || fy == height - 1);
					if (edge && pattern != Pattern.RIM) {
						light *= 0.88f;
					}
					light *= 1f + ((hash & 7) - 3.5f) * 0.008f;
					color = scale(color, light);
				}
				image.setPixel(px, py, color);
			}
		}
	}

	private static int hash(int x, int y) {
		int h = x * 374761393 + y * 668265263;
		h = (h ^ (h >>> 13)) * 1274126177;
		return h ^ (h >>> 16);
	}

	private static int scale(int argb, float factor) {
		int a = argb >>> 24;
		int r = Math.min(255, Math.round(((argb >> 16) & 0xFF) * factor));
		int g = Math.min(255, Math.round(((argb >> 8) & 0xFF) * factor));
		int b = Math.min(255, Math.round((argb & 0xFF) * factor));
		return a << 24 | r << 16 | g << 8 | b;
	}
}

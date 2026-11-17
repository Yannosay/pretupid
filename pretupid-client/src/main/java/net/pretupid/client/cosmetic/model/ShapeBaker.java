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
	private static final int MAX_TEXTURE_SIZE = 512;
	private static final int PADDING = 1;

	private ShapeBaker() {
	}

	public record Baked(ModelPart root, NativeImage texture) {
	}

	public static Baked bake(Shape shape, NativeImage uploaded) {
		List<Shape.Box> painted = new ArrayList<>();
		for (Shape.Node node : shape.nodes()) {
			collect(node, painted, uploaded != null);
		}
		int baseWidth = uploaded == null ? 0 : uploaded.getWidth();
		int baseHeight = uploaded == null ? 0 : uploaded.getHeight();
		int width = Math.max(32, baseWidth);
		Map<Shape.Box, int[]> layout = null;
		int height = baseHeight;
		while (width <= MAX_TEXTURE_SIZE) {
			layout = pack(painted, width, baseHeight, MAX_TEXTURE_SIZE);
			if (layout != null) {
				height = Math.max(baseHeight, usedHeight(layout, painted));
				if (height <= width * 2) {
					break;
				}
			}
			width *= 2;
		}
		if (layout == null) {
			throw new IllegalStateException("Cosmetic shape does not fit into a " + MAX_TEXTURE_SIZE + " pixel texture");
		}
		height = Math.max(16, nextPowerOfTwo(Math.max(height, 1)));
		MeshDefinition mesh = humanoidSkeleton();
		PartDefinition anchor = switch (shape.anchor()) {
			case HEAD -> mesh.getRoot().getChild("head");
			case BODY -> mesh.getRoot().getChild("body");
			case ROOT -> mesh.getRoot();
		};
		for (Shape.Node node : shape.nodes()) {
			addNode(anchor, node, layout);
		}
		NativeImage texture = new NativeImage(width, height, true);
		if (uploaded != null) {
			for (int y = 0; y < baseHeight; y++) {
				for (int x = 0; x < baseWidth; x++) {
					texture.setPixel(x, y, uploaded.getPixel(x, y));
				}
			}
		}
		for (Shape.Box box : painted) {
			int[] uv = layout.get(box);
			paint(texture, uv[0], uv[1], box);
		}
		return new Baked(LayerDefinition.create(mesh, width, height).bakeRoot(), texture);
	}

	static MeshDefinition humanoidSkeleton() {
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

	private static int nextPowerOfTwo(int value) {
		int result = 1;
		while (result < value) {
			result <<= 1;
		}
		return result;
	}

	private static void collect(Shape.Node node, List<Shape.Box> painted, boolean textured) {
		for (Shape.Box box : node.boxes) {
			if (!textured || !box.textured()) {
				painted.add(box);
			}
		}
		for (Shape.Node child : node.children) {
			collect(child, painted, textured);
		}
	}

	private static void addNode(PartDefinition parent, Shape.Node node, Map<Shape.Box, int[]> layout) {
		CubeListBuilder cubes = CubeListBuilder.create();
		for (Shape.Box box : node.boxes) {
			int[] uv = layout.get(box);
			int u = uv != null ? uv[0] : box.u();
			int v = uv != null ? uv[1] : box.v();
			cubes.mirror(box.mirror()).texOffs(u, v).addBox(box.x(), box.y(), box.z(), box.width(), box.height(), box.depth());
		}
		PartDefinition part = parent.addOrReplaceChild(node.name, cubes, PartPose.offsetAndRotation(node.x, node.y, node.z, node.xRot, node.yRot, node.zRot));
		for (Shape.Node child : node.children) {
			addNode(part, child, layout);
		}
	}

	private static int usedHeight(Map<Shape.Box, int[]> layout, List<Shape.Box> boxes) {
		int used = 0;
		for (Shape.Box box : boxes) {
			int[] uv = layout.get(box);
			used = Math.max(used, uv[1] + box.footprintHeight() + PADDING);
		}
		return used;
	}

	private static Map<Shape.Box, int[]> pack(List<Shape.Box> boxes, int width, int top, int maxHeight) {
		List<Shape.Box> order = new ArrayList<>(boxes);
		order.sort((a, b) -> Integer.compare(b.footprintHeight(), a.footprintHeight()));
		Map<Shape.Box, int[]> layout = new IdentityHashMap<>();
		int x = 0;
		int y = top;
		int rowHeight = 0;
		for (Shape.Box box : order) {
			int boxWidth = box.footprintWidth() + PADDING;
			int boxHeight = box.footprintHeight() + PADDING;
			if (boxWidth > width) {
				return null;
			}
			if (x + boxWidth > width) {
				x = 0;
				y += rowHeight;
				rowHeight = 0;
			}
			if (y + boxHeight > maxHeight) {
				return null;
			}
			layout.put(box, new int[] {x, y});
			x += boxWidth;
			rowHeight = Math.max(rowHeight, boxHeight);
		}
		return layout;
	}

	private static void paint(NativeImage image, int u, int v, Shape.Box box) {
		int w = Shape.Box.ceil(box.width());
		int h = Shape.Box.ceil(box.height());
		int d = Shape.Box.ceil(box.depth());
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
				if (px >= image.getWidth() || py >= image.getHeight()) {
					continue;
				}
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

package net.pretupid.client.cosmetic.model;

import java.util.ArrayList;
import java.util.List;

public final class Shape {
	public enum Anchor {
		BODY,
		ROOT
	}

	private final Anchor anchor;
	private final List<Node> nodes = new ArrayList<>();

	private Shape(Anchor anchor) {
		this.anchor = anchor;
	}

	public static Shape on(Anchor anchor) {
		return new Shape(anchor);
	}

	public Anchor anchor() {
		return anchor;
	}

	public List<Node> nodes() {
		return nodes;
	}

	public Node node(String name, float x, float y, float z) {
		Node node = new Node(name, x, y, z);
		nodes.add(node);
		return node;
	}

	public record Box(float x, float y, float z, int width, int height, int depth, int color, int accent, Pattern pattern) {
	}

	public static final class Node {
		final String name;
		final float x;
		final float y;
		final float z;
		float xRot;
		float yRot;
		float zRot;
		final List<Box> boxes = new ArrayList<>();
		final List<Node> children = new ArrayList<>();

		private Node(String name, float x, float y, float z) {
			this.name = name;
			this.x = x;
			this.y = y;
			this.z = z;
		}

		public Node rotated(float xRot, float yRot, float zRot) {
			this.xRot = xRot;
			this.yRot = yRot;
			this.zRot = zRot;
			return this;
		}

		public Node box(float x, float y, float z, int width, int height, int depth, int color) {
			return box(x, y, z, width, height, depth, color, color, Pattern.SOLID);
		}

		public Node box(float x, float y, float z, int width, int height, int depth, int color, int accent, Pattern pattern) {
			boxes.add(new Box(x, y, z, width, height, depth, color, accent, pattern));
			return this;
		}

		public Node mirroredBox(float x, float y, float z, int width, int height, int depth, int color, int accent, Pattern pattern) {
			return box(-x - width, y, z, width, height, depth, color, accent, pattern);
		}

		public Node child(String name, float x, float y, float z) {
			Node child = new Node(name, x, y, z);
			children.add(child);
			return child;
		}
	}
}

package net.pretupid.client.cosmetic.model;

import java.util.ArrayList;
import java.util.List;

public final class Shape {
	public enum Anchor {
		HEAD,
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

	public record Box(float x, float y, float z, float width, float height, float depth, int color, int accent, Pattern pattern, int u, int v, boolean mirror) {
		public boolean textured() {
			return u >= 0 && v >= 0;
		}

		int footprintWidth() {
			return 2 * (ceil(width) + ceil(depth));
		}

		int footprintHeight() {
			return ceil(depth) + ceil(height);
		}

		static int ceil(float value) {
			return Math.max(0, (int) Math.ceil(value - 1.0E-4f));
		}
	}

	public enum Property {
		ROT_X,
		ROT_Y,
		ROT_Z,
		X,
		Y,
		Z,
		SCALE,
		SCALE_X,
		SCALE_Y,
		SCALE_Z
	}

	public enum Wave {
		NONE,
		SINE,
		ABS_SINE,
		HALF_SINE,
		CUBIC_SINE,
		SPIN;

		float sample(float x) {
			return switch (this) {
				case NONE -> 0f;
				case SINE -> (float) Math.sin(x);
				case ABS_SINE -> Math.abs((float) Math.sin(x));
				case HALF_SINE -> Math.max(0f, (float) Math.sin(x));
				case CUBIC_SINE -> {
					float sine = (float) Math.sin(x);
					yield sine * sine * sine;
				}
				case SPIN -> x;
			};
		}
	}

	public record Channel(Property property, Wave wave, float amplitude, float speed, float phase, float offset, float walk, float walkOffset, float crouch) {
		public float value(float time, float motion, boolean crouching) {
			float value = offset + walkOffset * motion + (crouching ? crouch : 0f);
			if (wave != Wave.NONE && amplitude != 0f) {
				value += amplitude * (1f + walk * motion) * wave.sample(time * speed + phase);
			}
			return value;
		}
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
		final List<Channel> channels = new ArrayList<>();

		private Node(String name, float x, float y, float z) {
			this.name = name;
			this.x = x;
			this.y = y;
			this.z = z;
		}

		public String name() {
			return name;
		}

		public List<Node> children() {
			return children;
		}

		public List<Channel> channels() {
			return channels;
		}

		public Node rotated(float xRot, float yRot, float zRot) {
			this.xRot = xRot;
			this.yRot = yRot;
			this.zRot = zRot;
			return this;
		}

		public Node box(Box box) {
			boxes.add(box);
			return this;
		}

		public Node animate(Channel channel) {
			channels.add(channel);
			return this;
		}

		public Node child(String name, float x, float y, float z) {
			Node child = new Node(name, x, y, z);
			children.add(child);
			return child;
		}
	}
}

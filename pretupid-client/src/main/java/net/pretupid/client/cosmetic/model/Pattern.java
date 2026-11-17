package net.pretupid.client.cosmetic.model;

public enum Pattern {
	SOLID,
	TIPPED,
	BANDED,
	RIM,
	SPOTTED,
	VEINED,
	QUARTERED,
	GLOW,
	TOP;

	boolean useAccent(Face face, int x, int y, int width, int height, int hash) {
		return switch (this) {
			case SOLID, GLOW -> false;
			case TIPPED -> face.vertical() && y >= height - 1;
			case BANDED -> face.vertical() && (y / 2) % 2 == 1;
			case RIM -> x == 0 || y == 0 || x == width - 1 || y == height - 1;
			case SPOTTED -> Math.floorMod(hash, 7) == 0;
			case VEINED -> face.broad() && x % 3 == 0;
			case QUARTERED -> face.broad() && (x < width / 2) != (y < height / 2);
			case TOP -> face == Face.TOP;
		};
	}

	boolean shaded() {
		return this != GLOW;
	}

	enum Face {
		TOP(1.12f),
		BOTTOM(0.72f),
		EAST(0.86f),
		NORTH(1f),
		WEST(0.86f),
		SOUTH(0.94f);

		final float light;

		Face(float light) {
			this.light = light;
		}

		boolean vertical() {
			return this != TOP && this != BOTTOM;
		}

		boolean broad() {
			return this == NORTH || this == SOUTH;
		}
	}
}

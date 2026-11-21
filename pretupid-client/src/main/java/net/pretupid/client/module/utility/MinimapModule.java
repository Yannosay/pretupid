package net.pretupid.client.module.utility;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.MapColor;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.hud.HudModule;
import net.pretupid.client.hud.HudPosition;
import net.pretupid.client.map.MinimapCache;
import net.pretupid.client.module.Category;
import net.pretupid.client.platform.Gfx;
import net.pretupid.client.setting.BooleanSetting;
import net.pretupid.client.setting.EnumSetting;
import net.pretupid.client.setting.NumberSetting;
import net.pretupid.client.ui.Draw;

public final class MinimapModule extends HudModule {
	public enum MinimapShape {
		SQUARE,
		CIRCLE
	}

	private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(PretupidClient.MOD_ID, "minimap");
	private static final long COMPOSE_INTERVAL_NANOS = 33_000_000L;
	private static final int BORDER = 0xFF2A303B;
	private static final int[] PALETTE = buildPalette();
	private static final int SPRITE_OUTLINE = 0xFF14161C;
	private static final String[][] PLAYER = {
		{"....O....", "...OFO...", "..OFFFO..", "..OFFFO..", ".OFFFFFO.", ".OFFFFFO.", "OFFFOFFFO", "OFFO.OFFO", "OOO...OOO"},
		{"......OOO", "....OOFFO", "..OOFFFFO", "OOFFFFFO.", "OFFFFFFO.", ".OOOFFO..", "...OFFO..", "...OFO...", "....OO..."},
		{"OOO......", "OFFOO....", "OFFFFOO..", ".OFFFFFO.", "..OFFFFFO", ".OFFFFFO.", "OFFFFOO..", "OFFOO....", "OOO......"},
		{"....OO...", "...OFO...", "...OFFO..", ".OOOFFO..", "OFFFFFFO.", "OOFFFFFO.", "..OOFFFFO", "....OOFFO", "......OOO"},
		{"OOO...OOO", "OFFO.OFFO", "OFFFOFFFO", ".OFFFFFO.", ".OFFFFFO.", "..OFFFO..", "..OFFFO..", "...OFO...", "....O...."},
		{"...OO....", "...OFO...", "..OFFO...", "..OFFOOO.", ".OFFFFFFO", ".OFFFFFOO", "OFFFFOO..", "OFFOO....", "OOO......"},
		{"......OOO", "....OOFFO", "..OOFFFFO", ".OFFFFFO.", "OFFFFFO..", ".OFFFFFO.", "..OOFFFFO", "....OOFFO", "......OOO"},
		{"OOO......", "OFFOO....", "OFFFFOO..", ".OFFFFFOO", ".OFFFFFFO", "..OFFOOO.", "..OFFO...", "...OFO...", "...OO...."},
	};
	private static final String[] WAYPOINT_PIN = {"..OOO..", ".OFFFO.", "OFFWFFO", "OFWWWFO", "OFFWFFO", ".OFFFO.", "..OFO..", "...O..."};
	private static final String[] EDGE_MARKER = {"..O..", ".OFO.", "OFFFO", ".OFO.", "..O.."};
	private static final String[] NORTH = {"OOOOOOO", "OWOOOWO", "OWWOOWO", "OWOWOWO", "OWOOWWO", "OWOOOWO", "OOOOOOO"};

	private final NumberSetting size = add(new NumberSetting("size", 96, 64, 192, 8, " px"));
	private final NumberSetting zoom = add(new NumberSetting("zoom", 1.0, 0.5, 4.0, 0.25, "x"));
	private final EnumSetting<MinimapShape> shape = add(new EnumSetting<>("shape", MinimapShape.CIRCLE));
	private final BooleanSetting rotate = add(new BooleanSetting("rotate", true));
	private final BooleanSetting showWaypoints = add(new BooleanSetting("show_waypoints", true));
	private final BooleanSetting showNorth = add(new BooleanSetting("show_north", true));

	private final MinimapCache cache = new MinimapCache();
	private DynamicTexture texture;
	private NativeImage pixels;
	private int textureSize;
	private boolean dirty = true;
	private long composedAt;
	private double composedX = Double.NaN;
	private double composedZ = Double.NaN;
	private float composedYaw = Float.NaN;
	private int composedSignature;

	public MinimapModule() {
		super("minimap", Category.UTILITY, "map", false, new HudPosition(HudPosition.Anchor.END, 4, HudPosition.Anchor.END, 4));
	}

	private static int[] buildPalette() {
		int[] colors = new int[256];
		for (int packed = 4; packed < 256; packed++) {
			int color = MapColor.getColorFromPackedId(packed);
			colors[packed] = packed >> 2 == 0 || (color & 0xFFFFFF) == 0 ? 0 : color | 0xFF000000;
		}
		return colors;
	}

	private int chunkRadius() {
		return (int) Math.ceil(size.intValue() * zoom.get() * 0.75 / 16.0) + 1;
	}

	@Override
	public void onTick(Minecraft minecraft) {
		if (minecraft.player == null || minecraft.level == null) {
			cache.clear();
			return;
		}
		dirty |= cache.tick(minecraft, chunkRadius());
	}

	@Override
	protected void onDisable() {
		cache.clear();
		if (texture != null) {
			Minecraft.getInstance().getTextureManager().release(TEXTURE);
			texture = null;
			pixels = null;
			textureSize = 0;
		}
	}

	@Override
	public int width(Font font, Minecraft minecraft, boolean preview) {
		return size.intValue();
	}

	@Override
	public int height(Font font, Minecraft minecraft, boolean preview) {
		return size.intValue();
	}

	private void ensureTexture(Minecraft minecraft, int side) {
		if (texture != null && textureSize == side) {
			return;
		}
		NativeImage image = new NativeImage(side, side, true);
		texture = new DynamicTexture(() -> "Pretupid minimap", image);
		minecraft.getTextureManager().register(TEXTURE, texture);
		pixels = image;
		textureSize = side;
		dirty = true;
	}

	private void refresh(Minecraft minecraft, LocalPlayer player, int side) {
		ensureTexture(minecraft, side);
		long now = System.nanoTime();
		if (now - composedAt < COMPOSE_INTERVAL_NANOS) {
			return;
		}
		double x = player.getX();
		double z = player.getZ();
		float yaw = rotate.enabled() ? player.getYRot() : 0f;
		int signature = 31 * (31 * Double.hashCode(zoom.get()) + shape.get().ordinal()) + backgroundColor.resolve();
		boolean moved = x != composedX || z != composedZ || yaw != composedYaw || signature != composedSignature;
		if (!dirty && !moved) {
			return;
		}
		compose(player, side, x, z, yaw);
		texture.upload();
		composedAt = now;
		composedX = x;
		composedZ = z;
		composedYaw = yaw;
		composedSignature = signature;
		dirty = false;
	}

	private void compose(LocalPlayer player, int side, double px, double pz, float yaw) {
		double scale = zoom.get();
		double radians = Math.toRadians(yaw);
		double forwardX = -Math.sin(radians);
		double forwardZ = Math.cos(radians);
		double rightX = -forwardZ;
		double rightZ = forwardX;
		boolean turning = rotate.enabled();
		boolean circle = shape.get() == MinimapShape.CIRCLE;
		int radius = chunkRadius();
		int originX = (((int) Math.floor(px)) >> 4) - radius;
		int originZ = (((int) Math.floor(pz)) >> 4) - radius;
		int span = radius * 2 + 1;
		byte[][] grid = new byte[span * span][];
		for (int row = 0; row < span; row++) {
			for (int column = 0; column < span; column++) {
				grid[row * span + column] = cache.colors(originX + column, originZ + row);
			}
		}
		int base = backgroundColor.resolve() | 0xFF000000;
		double half = side / 2.0;
		double outer = half * half;
		double inner = (half - 1.5) * (half - 1.5);
		for (int y = 0; y < side; y++) {
			double sy = y + 0.5 - half;
			for (int x = 0; x < side; x++) {
				double sx = x + 0.5 - half;
				if (circle) {
					double distance = sx * sx + sy * sy;
					if (distance > outer) {
						pixels.setPixel(x, y, 0);
						continue;
					}
					if (distance > inner) {
						pixels.setPixel(x, y, BORDER);
						continue;
					}
				}
				double worldX;
				double worldZ;
				if (turning) {
					worldX = px + (rightX * sx - forwardX * sy) * scale;
					worldZ = pz + (rightZ * sx - forwardZ * sy) * scale;
				} else {
					worldX = px + sx * scale;
					worldZ = pz + sy * scale;
				}
				int blockX = (int) Math.floor(worldX);
				int blockZ = (int) Math.floor(worldZ);
				int column = (blockX >> 4) - originX;
				int row = (blockZ >> 4) - originZ;
				int color = base;
				if (column >= 0 && row >= 0 && column < span && row < span) {
					byte[] tile = grid[row * span + column];
					if (tile != null) {
						int mapped = PALETTE[tile[(blockZ & 15) << 4 | blockX & 15] & 0xFF];
						if (mapped != 0) {
							color = mapped;
						}
					}
				}
				pixels.setPixel(x, y, color);
			}
		}
	}

	@Override
	public void render(Gfx graphics, Font font, Minecraft minecraft, boolean preview) {
		int side = size.intValue();
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null) {
			Draw.rect(graphics, 0, 0, side, side, backgroundColor.resolve());
			return;
		}
		refresh(minecraft, player, side);
		graphics.blit(TEXTURE, 0, 0, 0f, 0f, side, side, side, side);
		boolean circle = shape.get() == MinimapShape.CIRCLE;
		if (!circle) {
			Draw.border(graphics, 0, 0, side, side, BORDER);
		}
		boolean turning = rotate.enabled();
		double radians = Math.toRadians(turning ? player.getYRot() : 0f);
		double forwardX = -Math.sin(radians);
		double forwardZ = Math.cos(radians);
		double rightX = -forwardZ;
		double rightZ = forwardX;
		float hud = scale();
		int pixel = Math.max(1, Math.round(hud));
		float half = side * hud / 2f;
		graphics.pose().pushMatrix();
		graphics.pose().scale(1f / hud, 1f / hud);
		if (showNorth.enabled()) {
			float nx = turning ? (float) -rightZ : 0f;
			float ny = turning ? (float) forwardZ : -1f;
			float reach = half - 6f * pixel;
			drawSprite(graphics, NORTH, Math.round(half + nx * reach) - 3 * pixel, Math.round(half + ny * reach) - 3 * pixel, pixel, 0xFFFFFFFF);
		}
		if (showWaypoints.enabled()) {
			WaypointsModule waypoints = PretupidClient.get().modules().get(WaypointsModule.class);
			if (waypoints.isEnabled()) {
				for (WaypointsModule.Located entry : waypoints.locatedHere(minecraft)) {
					double dx = entry.position().x - player.getX();
					double dz = entry.position().z - player.getZ();
					double sx = turning ? dx * rightX + dz * rightZ : dx;
					double sy = turning ? -(dx * forwardX + dz * forwardZ) : dz;
					drawWaypoint(graphics, (float) (sx / zoom.get() * hud), (float) (sy / zoom.get() * hud), half, circle, pixel, entry.waypoint().color());
				}
			}
		}
		int direction = turning ? 0 : Math.floorMod(Math.round(Mth.wrapDegrees(player.getYRot()) / 45f) + 4, 8);
		drawSprite(graphics, PLAYER[direction], Math.round(half) - 4 * pixel, Math.round(half) - 4 * pixel, pixel, 0xFFFFFFFF);
		graphics.pose().popMatrix();
	}

	private static void drawWaypoint(Gfx graphics, float sx, float sy, float half, boolean circle, int pixel, int color) {
		float limit = half - 5f * pixel;
		boolean clamped;
		if (circle) {
			float length = (float) Math.hypot(sx, sy);
			clamped = length > limit;
			if (clamped) {
				sx *= limit / length;
				sy *= limit / length;
			}
		} else {
			clamped = Math.abs(sx) > limit || Math.abs(sy) > limit;
			sx = Math.clamp(sx, -limit, limit);
			sy = Math.clamp(sy, -limit, limit);
		}
		int x = Math.round(half + sx);
		int y = Math.round(half + sy);
		int fill = color | 0xFF000000;
		if (clamped) {
			drawSprite(graphics, EDGE_MARKER, x - 2 * pixel, y - 2 * pixel, pixel, fill);
		} else {
			drawSprite(graphics, WAYPOINT_PIN, x - 3 * pixel, y - 7 * pixel, pixel, fill);
		}
	}

	private static void drawSprite(Gfx graphics, String[] rows, int x, int y, int pixel, int fill) {
		for (int row = 0; row < rows.length; row++) {
			String line = rows[row];
			int column = 0;
			while (column < line.length()) {
				char cell = line.charAt(column);
				int start = column;
				while (column < line.length() && line.charAt(column) == cell) {
					column++;
				}
				if (cell == '.') {
					continue;
				}
				int color = cell == 'O' ? SPRITE_OUTLINE : cell == 'W' ? 0xFFFFFFFF : fill;
				graphics.fill(x + start * pixel, y + row * pixel, x + column * pixel, y + (row + 1) * pixel, color);
			}
		}
	}
}

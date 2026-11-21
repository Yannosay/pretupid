package net.pretupid.client.module.utility;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
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
import net.pretupid.client.util.Colors;

public final class MinimapModule extends HudModule {
	public enum MinimapShape {
		SQUARE,
		CIRCLE
	}

	private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(PretupidClient.MOD_ID, "minimap");
	private static final long COMPOSE_INTERVAL_NANOS = 33_000_000L;
	private static final int BORDER = 0xFF2A303B;
	private static final int[] PALETTE = buildPalette();

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
		float yaw = rotate.enabled() ? player.getYRot() : 0f;
		double radians = Math.toRadians(yaw);
		double forwardX = -Math.sin(radians);
		double forwardZ = Math.cos(radians);
		double rightX = -forwardZ;
		double rightZ = forwardX;
		float half = side / 2f;
		if (showNorth.enabled()) {
			float nx = rotate.enabled() ? (float) (rightX * 0 + rightZ * -1) : 0f;
			float ny = rotate.enabled() ? (float) (forwardZ) : -1f;
			float reach = half - 7f;
			int labelX = Math.round(half + nx * reach);
			int labelY = Math.round(half + ny * reach);
			graphics.text(font, "N", labelX - font.width("N") / 2, labelY - 4, 0xFFFFFFFF, true);
		}
		if (showWaypoints.enabled()) {
			WaypointsModule waypoints = PretupidClient.get().modules().get(WaypointsModule.class);
			if (waypoints.isEnabled()) {
				List<WaypointsModule.Located> located = waypoints.locatedHere(minecraft);
				for (WaypointsModule.Located entry : located) {
					double dx = entry.position().x - player.getX();
					double dz = entry.position().z - player.getZ();
					double sx = rotate.enabled() ? dx * rightX + dz * rightZ : dx;
					double sy = rotate.enabled() ? -(dx * forwardX + dz * forwardZ) : dz;
					drawWaypoint(graphics, (float) (sx / zoom.get()), (float) (sy / zoom.get()), half, circle, entry.waypoint().color());
				}
			}
		}
		drawPlayer(graphics, half, rotate.enabled(), player.getYRot());
	}

	private static void drawWaypoint(Gfx graphics, float sx, float sy, float half, boolean circle, int color) {
		float limit = half - 5f;
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
		int radius = clamped ? 1 : 2;
		graphics.fill(x - radius - 1, y - radius - 1, x + radius + 2, y + radius + 2, Colors.withAlpha(0x000000, 190));
		graphics.fill(x - radius, y - radius, x + radius + 1, y + radius + 1, color | 0xFF000000);
	}

	private static void drawPlayer(Gfx graphics, float half, boolean turning, float yaw) {
		double radians = Math.toRadians(yaw);
		float fx = turning ? 0f : (float) -Math.sin(radians);
		float fy = turning ? -1f : (float) Math.cos(radians);
		float nx = -fy;
		float ny = fx;
		WaypointsModule.triangle(graphics, half + fx * 7, half + fy * 7, half - fx * 5 + nx * 5.5f, half - fy * 5 + ny * 5.5f, half - fx * 5 - nx * 5.5f, half - fy * 5 - ny * 5.5f, 0xC0000000);
		WaypointsModule.triangle(graphics, half + fx * 5, half + fy * 5, half - fx * 3.5f + nx * 4f, half - fy * 3.5f + ny * 4f, half - fx * 3.5f - nx * 4f, half - fy * 3.5f - ny * 4f, 0xFFFFFFFF);
	}
}

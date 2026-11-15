package net.pretupid.client.module.performance;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

public final class OcclusionCuller {
	private static final long FRAME_BUDGET_NANOS = 1_500_000L;
	private static final long VISIBLE_RECHECK_NANOS = 200_000_000L;
	private static final long EXPIRE_NANOS = 3_000_000_000L;
	private static final long SWEEP_INTERVAL_NANOS = 2_000_000_000L;
	private static final double ALWAYS_VISIBLE_DISTANCE_SQR = 9.0;
	private static final double INSET = 0.05;
	private static final int MAX_RAY_STEPS = 768;

	private final Long2ObjectMap<Entry> entities = new Long2ObjectOpenHashMap<>();
	private final Long2ObjectMap<Entry> blockEntities = new Long2ObjectOpenHashMap<>();
	private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
	private final double[] points = new double[27];
	private long frameStart;
	private long spent;
	private long lastSweep;
	private int tests;
	private int hidden;
	private int lastTests;
	private int lastHidden;

	public void beginFrame() {
		long now = System.nanoTime();
		frameStart = now;
		spent = 0;
		lastTests = tests;
		lastHidden = hidden;
		tests = 0;
		hidden = 0;
		if (now - lastSweep > SWEEP_INTERVAL_NANOS) {
			lastSweep = now;
			entities.values().removeIf(entry -> now - entry.seen > EXPIRE_NANOS);
			blockEntities.values().removeIf(entry -> now - entry.seen > EXPIRE_NANOS);
		}
	}

	public void clear() {
		entities.clear();
		blockEntities.clear();
	}

	public int lastFrameTests() {
		return lastTests;
	}

	public int lastFrameHidden() {
		return lastHidden;
	}

	public boolean entityVisible(Level level, int id, AABB box, double cameraX, double cameraY, double cameraZ) {
		return visible(entities, id, level, box, cameraX, cameraY, cameraZ);
	}

	public boolean blockEntityVisible(Level level, BlockPos pos, double cameraX, double cameraY, double cameraZ) {
		AABB box = new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
		return visible(blockEntities, pos.asLong(), level, box, cameraX, cameraY, cameraZ);
	}

	private boolean visible(Long2ObjectMap<Entry> cache, long key, Level level, AABB box, double cameraX, double cameraY, double cameraZ) {
		long now = System.nanoTime();
		Entry entry = cache.get(key);
		if (entry == null) {
			entry = new Entry();
			cache.put(key, entry);
		}
		entry.seen = now;
		if (entry.checked != 0 && entry.visible && now - entry.checked < VISIBLE_RECHECK_NANOS) {
			return true;
		}
		if (spent >= FRAME_BUDGET_NANOS || now - frameStart > 1_000_000_000L) {
			return entry.checked == 0 || entry.visible;
		}
		boolean result = trace(level, box, cameraX, cameraY, cameraZ);
		spent += System.nanoTime() - now;
		tests++;
		if (!result) {
			hidden++;
		}
		entry.visible = result;
		entry.checked = now;
		return result;
	}

	private boolean trace(Level level, AABB box, double cameraX, double cameraY, double cameraZ) {
		double minX = box.minX + INSET;
		double minY = box.minY + INSET;
		double minZ = box.minZ + INSET;
		double maxX = Math.max(minX, box.maxX - INSET);
		double maxY = Math.max(minY, box.maxY - INSET);
		double maxZ = Math.max(minZ, box.maxZ - INSET);
		if (cameraX >= minX && cameraX <= maxX && cameraY >= minY && cameraY <= maxY && cameraZ >= minZ && cameraZ <= maxZ) {
			return true;
		}
		double centerX = (minX + maxX) * 0.5;
		double centerY = (minY + maxY) * 0.5;
		double centerZ = (minZ + maxZ) * 0.5;
		double dx = centerX - cameraX;
		double dy = centerY - cameraY;
		double dz = centerZ - cameraZ;
		if (dx * dx + dy * dy + dz * dz < ALWAYS_VISIBLE_DISTANCE_SQR) {
			return true;
		}
		int count = 0;
		points[count++] = centerX;
		points[count++] = centerY;
		points[count++] = centerZ;
		for (int corner = 0; corner < 8; corner++) {
			points[count++] = (corner & 1) == 0 ? minX : maxX;
			points[count++] = (corner & 2) == 0 ? minY : maxY;
			points[count++] = (corner & 4) == 0 ? minZ : maxZ;
		}
		for (int i = 0; i < count; i += 3) {
			if (rayClear(level, cameraX, cameraY, cameraZ, points[i], points[i + 1], points[i + 2])) {
				return true;
			}
		}
		return false;
	}

	private boolean rayClear(Level level, double startX, double startY, double startZ, double endX, double endY, double endZ) {
		int x = Mth.floor(startX);
		int y = Mth.floor(startY);
		int z = Mth.floor(startZ);
		int targetX = Mth.floor(endX);
		int targetY = Mth.floor(endY);
		int targetZ = Mth.floor(endZ);
		double dx = endX - startX;
		double dy = endY - startY;
		double dz = endZ - startZ;
		int stepX = dx > 0 ? 1 : -1;
		int stepY = dy > 0 ? 1 : -1;
		int stepZ = dz > 0 ? 1 : -1;
		double deltaX = dx == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dx);
		double deltaY = dy == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dy);
		double deltaZ = dz == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dz);
		double maxX = dx == 0 ? Double.POSITIVE_INFINITY : (dx > 0 ? x + 1 - startX : startX - x) * deltaX;
		double maxY = dy == 0 ? Double.POSITIVE_INFINITY : (dy > 0 ? y + 1 - startY : startY - y) * deltaY;
		double maxZ = dz == 0 ? Double.POSITIVE_INFINITY : (dz > 0 ? z + 1 - startZ : startZ - z) * deltaZ;
		int steps = Math.min(MAX_RAY_STEPS, Math.abs(targetX - x) + Math.abs(targetY - y) + Math.abs(targetZ - z));
		for (int i = 0; i < steps; i++) {
			if (maxX < maxY && maxX < maxZ) {
				x += stepX;
				maxX += deltaX;
			} else if (maxY < maxZ) {
				y += stepY;
				maxY += deltaY;
			} else {
				z += stepZ;
				maxZ += deltaZ;
			}
			if (x == targetX && y == targetY && z == targetZ) {
				return true;
			}
			if (level.getBlockState(cursor.set(x, y, z)).isSolidRender()) {
				return false;
			}
		}
		return true;
	}

	private static final class Entry {
		private long checked;
		private long seen;
		private boolean visible = true;
	}
}

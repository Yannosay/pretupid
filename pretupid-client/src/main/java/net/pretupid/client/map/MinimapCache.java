package net.pretupid.client.map;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.pretupid.client.module.utility.WaypointsModule;

public final class MinimapCache {
	private static final int MAX_CHUNKS = 1024;
	private static final long SCAN_BUDGET_NANOS = 1_000_000L;
	private static final int BASE_REFRESH_TICKS = 40;
	private static final int RING_REFRESH_TICKS = 20;
	private static final int MAX_COLUMN_STEPS = 96;
	private static final int MAX_WATER_DEPTH = 9;
	private static final String NETHER = "minecraft:the_nether";

	private static final class Tile {
		final byte[] colors = new byte[256];
		int scannedAt;
	}

	private final Map<Long, Tile> tiles = new LinkedHashMap<>(256, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Long, Tile> eldest) {
			return size() > MAX_CHUNKS;
		}
	};
	private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
	private final int[] northHeights = new int[16];
	private String scope = "";
	private int clock;
	private int sampledHeight;
	private MapColor sampledColor = MapColor.NONE;
	private int sampledDepth;

	public void clear() {
		tiles.clear();
		scope = "";
	}

	public byte[] colors(int chunkX, int chunkZ) {
		Tile tile = tiles.get(chunkKey(chunkX, chunkZ));
		return tile == null ? null : tile.colors;
	}

	public boolean tick(Minecraft minecraft, int radius) {
		ClientLevel level = minecraft.level;
		LocalPlayer player = minecraft.player;
		if (level == null || player == null) {
			clear();
			return false;
		}
		String dimension = WaypointsModule.dimension(minecraft);
		String nextScope = WaypointsModule.worldKey(minecraft) + "|" + dimension;
		if (!nextScope.equals(scope)) {
			tiles.clear();
			scope = nextScope;
		}
		clock++;
		boolean ceiling = dimension.equals(NETHER);
		int centerX = player.getBlockX() >> 4;
		int centerZ = player.getBlockZ() >> 4;
		int playerY = player.getBlockY();
		long deadline = System.nanoTime() + SCAN_BUDGET_NANOS;
		boolean changed = false;
		for (int ring = 0; ring <= radius; ring++) {
			int age = BASE_REFRESH_TICKS + ring * RING_REFRESH_TICKS;
			for (int dz = -ring; dz <= ring; dz++) {
				int step = Math.abs(dz) == ring || ring == 0 ? 1 : ring * 2;
				for (int dx = -ring; dx <= ring; dx += step) {
					int chunkX = centerX + dx;
					int chunkZ = centerZ + dz;
					long key = chunkKey(chunkX, chunkZ);
					Tile tile = tiles.get(key);
					if (tile != null && clock - tile.scannedAt < age) {
						continue;
					}
					LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
					if (chunk == null) {
						continue;
					}
					if (tile == null) {
						tile = new Tile();
						tiles.put(key, tile);
					}
					scan(level, chunk, chunkX, chunkZ, tile, ceiling, playerY);
					tile.scannedAt = clock;
					changed = true;
					if (System.nanoTime() >= deadline) {
						return true;
					}
				}
			}
		}
		return changed;
	}

	private void scan(ClientLevel level, LevelChunk chunk, int chunkX, int chunkZ, Tile tile, boolean ceiling, int playerY) {
		int originX = chunkX << 4;
		int originZ = chunkZ << 4;
		LevelChunk north = level.getChunkSource().getChunk(chunkX, chunkZ - 1, ChunkStatus.FULL, false);
		for (int x = 0; x < 16; x++) {
			if (north == null) {
				northHeights[x] = Integer.MIN_VALUE;
			} else {
				sample(north, originX + x, originZ - 1, ceiling, playerY);
				northHeights[x] = sampledHeight;
			}
		}
		for (int z = 0; z < 16; z++) {
			for (int x = 0; x < 16; x++) {
				sample(chunk, originX + x, originZ + z, ceiling, playerY);
				MapColor.Brightness brightness;
				if (sampledColor == MapColor.WATER) {
					brightness = sampledDepth <= 2 ? MapColor.Brightness.HIGH : sampledDepth <= 5 ? MapColor.Brightness.NORMAL : MapColor.Brightness.LOW;
				} else if (northHeights[x] == Integer.MIN_VALUE || sampledHeight == northHeights[x]) {
					brightness = MapColor.Brightness.NORMAL;
				} else {
					brightness = sampledHeight > northHeights[x] ? MapColor.Brightness.HIGH : MapColor.Brightness.LOW;
				}
				tile.colors[z << 4 | x] = sampledColor == MapColor.NONE ? 0 : sampledColor.getPackedId(brightness);
				northHeights[x] = sampledHeight;
			}
		}
	}

	private void sample(LevelChunk chunk, int worldX, int worldZ, boolean ceiling, int playerY) {
		int min = chunk.getMinY();
		int y;
		cursor.set(worldX, playerY, worldZ);
		if (ceiling) {
			y = playerY + 2;
			cursor.setY(y);
			for (int guard = 0; guard < 16 && y > min && !chunk.getBlockState(cursor).isAir(); guard++) {
				cursor.setY(--y);
			}
		} else {
			y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, worldX & 15, worldZ & 15);
		}
		sampledColor = MapColor.NONE;
		sampledDepth = 0;
		BlockState state = null;
		for (int steps = 0; y >= min && steps < MAX_COLUMN_STEPS; steps++, y--) {
			cursor.setY(y);
			state = chunk.getBlockState(cursor);
			sampledColor = state.getMapColor(chunk, cursor);
			if (sampledColor != MapColor.NONE) {
				break;
			}
		}
		sampledHeight = y;
		if (sampledColor == MapColor.WATER && state != null) {
			int depthY = y;
			while (sampledDepth < MAX_WATER_DEPTH && depthY > min && !chunk.getBlockState(cursor.setY(depthY)).getFluidState().isEmpty()) {
				sampledDepth++;
				depthY--;
			}
		}
	}

	private static long chunkKey(int chunkX, int chunkZ) {
		return (long) chunkX & 0xFFFFFFFFL | ((long) chunkZ & 0xFFFFFFFFL) << 32;
	}
}

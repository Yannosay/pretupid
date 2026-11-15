package net.pretupid.client.cosmetic;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.network.RemoteProfile;

public final class TrailRenderer {
	private static final Set<String> ALLOWED = Set.of(
		"dust", "end_rod", "flame", "soul_fire_flame", "heart", "note", "happy_villager", "cherry_leaves", "electric_spark",
		"enchant", "glow", "snowflake", "totem_of_undying", "witch", "bubble_pop", "small_flame", "wax_on", "scrape"
	);
	private static final double RANGE_SQUARED = 48 * 48;
	private static final int MAX_PLAYERS = 24;

	private final RandomSource random = RandomSource.create();
	private final Map<UUID, Vec3> lastPositions = new HashMap<>();
	private int ticks;

	public void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		PretupidClient client = PretupidClient.get();
		if (level == null || minecraft.player == null || minecraft.isPaused()) {
			lastPositions.clear();
			return;
		}
		ParticleStatus status = minecraft.options.particles().get();
		if (status == ParticleStatus.MINIMAL || !client.settings().showTrails.enabled()) {
			return;
		}
		ticks++;
		Catalog catalog = client.cosmetics().catalog();
		Map<UUID, Vec3> current = new HashMap<>();
		int rendered = 0;
		for (Player player : level.players()) {
			if (rendered >= MAX_PLAYERS || player.isInvisible() || player.isSpectator() || player.distanceToSqr(minecraft.player) > RANGE_SQUARED) {
				continue;
			}
			String trailId = trailOf(player, client);
			if (trailId == null) {
				continue;
			}
			Optional<Catalog.TrailEntry> trail = catalog.trail(trailId);
			if (trail.isEmpty()) {
				continue;
			}
			Vec3 position = player.position();
			Vec3 previous = lastPositions.get(player.getUUID());
			current.put(player.getUUID(), position);
			boolean moving = previous != null && previous.distanceToSqr(position) > 0.0025;
			spawn(level, trail.get().definition(), player, moving, status == ParticleStatus.DECREASED);
			rendered++;
		}
		lastPositions.clear();
		lastPositions.putAll(current);
	}

	private static String trailOf(Player player, PretupidClient client) {
		if (client.nametags().isSelf(player.getUUID())) {
			String own = client.settings().trail.get();
			return own.isEmpty() ? null : own;
		}
		if (!client.settings().showCosmetics.enabled()) {
			return null;
		}
		return client.presence().profileOf(player.getUUID()).map(RemoteProfile::trail).orElse(null);
	}

	private void spawn(ClientLevel level, TrailDefinition definition, Player player, boolean moving, boolean reduced) {
		int density = reduced ? Math.max(1, definition.density() / 2) : definition.density();
		double x = player.getX();
		double y = player.getY();
		double z = player.getZ();
		switch (definition.pattern()) {
			case STREAM -> {
				if (!moving) {
					return;
				}
				for (int i = 0; i < density; i++) {
					add(level, definition, x + random.nextGaussian() * 0.15, y + 0.1 + random.nextDouble() * 0.3, z + random.nextGaussian() * 0.15, 0, 0.01, 0);
				}
			}
			case RING -> {
				if (ticks % Math.max(2, 8 - density) != 0) {
					return;
				}
				int points = 8 + density * 2;
				for (int i = 0; i < points; i++) {
					double angle = Math.PI * 2 * i / points;
					add(level, definition, x + Math.cos(angle) * 0.6, y + 0.1, z + Math.sin(angle) * 0.6, 0, 0.02, 0);
				}
			}
			case SPIRAL -> {
				for (int i = 0; i < density; i++) {
					double angle = (ticks * 0.35) + i * (Math.PI * 2 / density);
					double height = (ticks % 20) / 20.0 * 1.8;
					add(level, definition, x + Math.cos(angle) * 0.55, y + height, z + Math.sin(angle) * 0.55, 0, 0, 0);
				}
			}
			case HELIX -> {
				for (int strand = 0; strand < 2; strand++) {
					double angle = ticks * 0.4 + strand * Math.PI;
					double height = (ticks % 24) / 24.0 * 2.0;
					add(level, definition, x + Math.cos(angle) * 0.5, y + height, z + Math.sin(angle) * 0.5, 0, 0, 0);
				}
			}
		}
	}

	private void add(ClientLevel level, TrailDefinition definition, double x, double y, double z, double vx, double vy, double vz) {
		ParticleOptions options = resolve(definition);
		if (options != null) {
			level.addParticle(options, x, y, z, vx, vy, vz);
		}
	}

	private ParticleOptions resolve(TrailDefinition definition) {
		if (!ALLOWED.contains(definition.particle())) {
			return null;
		}
		if ("dust".equals(definition.particle())) {
			int[] colors = definition.colors();
			return new DustParticleOptions(colors[random.nextInt(colors.length)] & 0xFFFFFF, definition.size());
		}
		ParticleType<?> type = BuiltInRegistries.PARTICLE_TYPE.getValue(Identifier.withDefaultNamespace(definition.particle()));
		return type instanceof SimpleParticleType simple ? simple : null;
	}
}

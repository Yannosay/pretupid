package net.pretupid.client.cosmetic;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;

public final class PetFollower {
	private static final float STIFFNESS = 16f;
	private static final float DAMPING = 2f * Mth.sqrt(STIFFNESS) * 0.9f;
	private static final float TURN_RATE = 7f;
	private static final float MAX_STEP = 1f / 60f;
	private static final double SNAP_DISTANCE_SQR = 10.0 * 10.0;
	private static final long STALE_NANOS = 500_000_000L;
	private static final long EXPIRY_NANOS = 10_000_000_000L;
	private static final int MAX_TRACKED = 256;

	private static final Int2ObjectOpenHashMap<Follower> FOLLOWERS = new Int2ObjectOpenHashMap<>();
	private static long lastSweep;

	private PetFollower() {
	}

	public record Placement(float x, float y, float z, float yaw) {
	}

	private static final class Follower {
		double x;
		double y;
		double z;
		double vx;
		double vy;
		double vz;
		float yaw;
		long updatedAt;
		float ageInTicks = Float.NaN;
		float bodyYaw;
		Placement placement;
	}

	public static Placement update(AvatarRenderState state) {
		long now = System.nanoTime();
		sweep(now);
		float bodyYaw = state.bodyRot;
		float headYaw = bodyYaw + state.yRot;
		float yawRadians = bodyYaw * Mth.DEG_TO_RAD;
		double forwardX = -Mth.sin(yawRadians);
		double forwardZ = Mth.cos(yawRadians);
		double rightX = -forwardZ;
		double rightZ = forwardX;
		float scale = state.scale;
		double height = (state.isCrouching ? 1.25 : 1.55) * scale;
		double targetX = state.x + (rightX * 0.85 - forwardX * 0.3) * scale;
		double targetY = state.y + height;
		double targetZ = state.z + (rightZ * 0.85 - forwardZ * 0.3) * scale;
		Follower follower = FOLLOWERS.get(state.id);
		boolean snap = follower == null || now - follower.updatedAt > STALE_NANOS
			|| Mth.lengthSquared(follower.x - targetX, follower.y - targetY, follower.z - targetZ) > SNAP_DISTANCE_SQR;
		if (follower == null) {
			if (FOLLOWERS.size() >= MAX_TRACKED) {
				return null;
			}
			follower = new Follower();
			FOLLOWERS.put(state.id, follower);
		}
		if (!snap && state.ageInTicks == follower.ageInTicks && Math.abs(Mth.wrapDegrees(bodyYaw - follower.bodyYaw)) > 0.01f) {
			return new Placement((float) (targetX - state.x), (float) (targetY - state.y), (float) (targetZ - state.z), headYaw);
		}
		if (snap) {
			follower.x = targetX;
			follower.y = targetY;
			follower.z = targetZ;
			follower.vx = follower.vy = follower.vz = 0.0;
			follower.yaw = headYaw;
		} else if (state.ageInTicks != follower.ageInTicks) {
			float elapsed = Math.min(0.1f, (now - follower.updatedAt) / 1_000_000_000f);
			int steps = Math.max(1, Mth.ceil(elapsed / MAX_STEP));
			float step = elapsed / steps;
			for (int i = 0; i < steps; i++) {
				follower.vx += (STIFFNESS * (targetX - follower.x) - DAMPING * follower.vx) * step;
				follower.vy += (STIFFNESS * (targetY - follower.y) - DAMPING * follower.vy) * step;
				follower.vz += (STIFFNESS * (targetZ - follower.z) - DAMPING * follower.vz) * step;
				follower.x += follower.vx * step;
				follower.y += follower.vy * step;
				follower.z += follower.vz * step;
			}
			double speed = Math.sqrt(follower.vx * follower.vx + follower.vz * follower.vz);
			float desired = speed > 1.2 ? (float) (Mth.atan2(-follower.vx, follower.vz) * Mth.RAD_TO_DEG) : headYaw;
			float turn = 1f - (float) Math.exp(-TURN_RATE * elapsed);
			follower.yaw += Mth.wrapDegrees(desired - follower.yaw) * turn;
		}
		if (follower.placement == null || state.ageInTicks != follower.ageInTicks || snap) {
			follower.placement = new Placement((float) (follower.x - state.x), (float) (follower.y - state.y), (float) (follower.z - state.z), follower.yaw);
		}
		follower.updatedAt = now;
		follower.ageInTicks = state.ageInTicks;
		follower.bodyYaw = bodyYaw;
		return follower.placement;
	}

	private static void sweep(long now) {
		if (now - lastSweep < 2_000_000_000L) {
			return;
		}
		lastSweep = now;
		FOLLOWERS.values().removeIf(follower -> now - follower.updatedAt > EXPIRY_NANOS);
	}

	public static void clear() {
		FOLLOWERS.clear();
	}
}

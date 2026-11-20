package net.pretupid.client.screenie;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.pretupid.client.cinematic.Shot;

public enum MemeShot {
	NORMAL,
	FACE,
	WORM,
	BIRD,
	DUTCH,
	FISHEYE,
	SIDE,
	SHOULDER;

	public Shot.View view(Player player) {
		Vec3 eye = player.getEyePosition();
		Vec3 feet = player.position();
		double yaw = Math.toRadians(player.getYRot());
		Vec3 forward = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
		Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
		return switch (this) {
			case NORMAL -> null;
			case FACE -> look(eye.add(forward.scale(1.15)).add(0, 0.05, 0), eye, 0f, 50f);
			case WORM -> look(feet.add(forward.scale(1.4)).add(0, 0.12, 0), eye, 0f, 88f);
			case BIRD -> new Shot.View(eye.add(0, 6, 0).add(forward.scale(0.01)), player.getYRot(), 89.9f, 0f, 70f);
			case DUTCH -> look(eye.add(forward.scale(2.6)).add(right.scale(0.8)).add(0, 0.3, 0), eye, 24f, 62f);
			case FISHEYE -> look(eye.add(forward.scale(0.95)).add(0, 0.1, 0), eye, 0f, 140f);
			case SIDE -> look(eye.add(right.scale(2.2)), eye, 0f, 55f);
			case SHOULDER -> new Shot.View(eye.add(forward.scale(-1.1)).add(right.scale(-0.55)).add(0, 0.25, 0), player.getYRot(), player.getXRot(), 0f, 70f);
		};
	}

	private static Shot.View look(Vec3 from, Vec3 target, float roll, float fov) {
		Vec3 delta = target.subtract(from);
		double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
		float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
		float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, horizontal));
		return new Shot.View(from, yaw, pitch, roll, fov);
	}
}

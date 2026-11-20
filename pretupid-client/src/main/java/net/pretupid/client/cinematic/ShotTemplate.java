package net.pretupid.client.cinematic;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public enum ShotTemplate {
	ORBIT,
	FLY_BY,
	CRANE,
	DOLLY_ZOOM,
	PUSH_IN;

	public Shot create(Player player, String name) {
		Shot shot = new Shot(name);
		Vec3 eye = player.getEyePosition();
		double yaw = Math.toRadians(player.getYRot());
		Vec3 forward = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
		Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
		switch (this) {
			case ORBIT -> {
				shot.setLookAtPlayer(true);
				shot.setLoop(true);
				int points = 8;
				for (int i = 0; i <= points; i++) {
					double angle = yaw + Math.PI * 2 * i / points;
					Vec3 position = eye.add(-Math.sin(angle) * 4.5, 1.2, Math.cos(angle) * 4.5);
					shot.add(keyframe(i * 1.5, position, eye, 70f, CameraKeyframe.Ease.LINEAR));
				}
			}
			case FLY_BY -> {
				shot.setLookAtPlayer(true);
				shot.add(keyframe(0, eye.add(forward.scale(9)).add(right.scale(-5)).add(0, 1.5, 0), eye, 60f, CameraKeyframe.Ease.SMOOTH));
				shot.add(keyframe(3, eye.add(right.scale(2.2)).add(0, 0.4, 0), eye, 70f, CameraKeyframe.Ease.SMOOTH));
				shot.add(keyframe(6, eye.add(forward.scale(-9)).add(right.scale(5)).add(0, 2.5, 0), eye, 60f, CameraKeyframe.Ease.SMOOTH));
			}
			case CRANE -> {
				shot.setLookAtPlayer(true);
				shot.add(keyframe(0, eye.add(forward.scale(3)).add(0, -0.6, 0), eye, 70f, CameraKeyframe.Ease.EASE_OUT));
				shot.add(keyframe(8, eye.add(forward.scale(5)).add(0, 11, 0), eye, 75f, CameraKeyframe.Ease.SMOOTH));
			}
			case DOLLY_ZOOM -> {
				shot.setLookAtPlayer(true);
				double near = 3.0;
				double far = 12.0;
				float wide = 90f;
				float narrow = (float) Math.toDegrees(2 * Math.atan(Math.tan(Math.toRadians(wide / 2)) * near / far));
				shot.add(keyframe(0, eye.add(forward.scale(near)), eye, wide, CameraKeyframe.Ease.LINEAR));
				shot.add(keyframe(5, eye.add(forward.scale(far)), eye, narrow, CameraKeyframe.Ease.LINEAR));
			}
			case PUSH_IN -> {
				shot.setLookAtPlayer(true);
				shot.add(keyframe(0, eye.add(forward.scale(8)).add(0, 0.8, 0), eye, 70f, CameraKeyframe.Ease.EASE_IN));
				shot.add(keyframe(4, eye.add(forward.scale(1.4)).add(0, 0.1, 0), eye, 55f, CameraKeyframe.Ease.SMOOTH));
			}
		}
		return shot;
	}

	private static CameraKeyframe keyframe(double time, Vec3 position, Vec3 target, float fov, CameraKeyframe.Ease ease) {
		Vec3 delta = target.subtract(position);
		double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
		float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
		float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, horizontal));
		return new CameraKeyframe(time, position, yaw, pitch, 0f, fov, ease);
	}
}

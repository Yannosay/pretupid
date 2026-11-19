package net.pretupid.client.cinematic;

import com.google.gson.JsonObject;
import net.minecraft.world.phys.Vec3;
import net.pretupid.client.network.Json;

public record CameraKeyframe(double time, Vec3 position, float yaw, float pitch, float roll, float fov, boolean linear) {
	public static final float MIN_FOV = 10f;
	public static final float MAX_FOV = 140f;

	public CameraKeyframe withTime(double value) {
		return new CameraKeyframe(Math.max(0.0, value), position, yaw, pitch, roll, fov, linear);
	}

	public CameraKeyframe withFov(float value) {
		return new CameraKeyframe(time, position, yaw, pitch, roll, Math.clamp(value, MIN_FOV, MAX_FOV), linear);
	}

	public CameraKeyframe withRoll(float value) {
		return new CameraKeyframe(time, position, yaw, pitch, Math.clamp(value, -180f, 180f), fov, linear);
	}

	public CameraKeyframe withLinear(boolean value) {
		return new CameraKeyframe(time, position, yaw, pitch, roll, fov, value);
	}

	public CameraKeyframe withView(Vec3 newPosition, float newYaw, float newPitch) {
		return new CameraKeyframe(time, newPosition, newYaw, newPitch, roll, fov, linear);
	}

	public JsonObject toJson() {
		JsonObject object = new JsonObject();
		object.addProperty("t", Math.round(time * 1000.0) / 1000.0);
		object.addProperty("x", position.x);
		object.addProperty("y", position.y);
		object.addProperty("z", position.z);
		object.addProperty("yaw", yaw);
		object.addProperty("pitch", pitch);
		object.addProperty("roll", roll);
		object.addProperty("fov", fov);
		object.addProperty("linear", linear);
		return object;
	}

	public static CameraKeyframe fromJson(JsonObject object) {
		double x = Json.decimal(object, "x", 0);
		double y = Json.decimal(object, "y", 0);
		double z = Json.decimal(object, "z", 0);
		if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
			return null;
		}
		return new CameraKeyframe(
			Math.clamp(Json.decimal(object, "t", 0), 0, 3600),
			new Vec3(x, y, z),
			(float) Json.decimal(object, "yaw", 0),
			(float) Math.clamp(Json.decimal(object, "pitch", 0), -90, 90),
			(float) Math.clamp(Json.decimal(object, "roll", 0), -180, 180),
			(float) Math.clamp(Json.decimal(object, "fov", 70), MIN_FOV, MAX_FOV),
			Json.bool(object, "linear", false)
		);
	}
}

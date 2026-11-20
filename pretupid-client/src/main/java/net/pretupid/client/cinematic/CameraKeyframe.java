package net.pretupid.client.cinematic;

import com.google.gson.JsonObject;
import java.util.Locale;
import net.minecraft.world.phys.Vec3;
import net.pretupid.client.network.Json;

public record CameraKeyframe(double time, Vec3 position, float yaw, float pitch, float roll, float fov, Ease ease) {
	public static final float MIN_FOV = 10f;
	public static final float MAX_FOV = 140f;

	public enum Ease {
		SMOOTH,
		LINEAR,
		EASE_IN,
		EASE_OUT,
		HOLD;

		public double apply(double t) {
			return switch (this) {
				case SMOOTH -> t * t * (3 - 2 * t);
				case LINEAR -> t;
				case EASE_IN -> t * t * t;
				case EASE_OUT -> 1 - Math.pow(1 - t, 3);
				case HOLD -> 0;
			};
		}

		public Ease next() {
			Ease[] values = values();
			return values[(ordinal() + 1) % values.length];
		}

		static Ease parse(String value, boolean linear) {
			if (value != null) {
				for (Ease ease : values()) {
					if (ease.name().equalsIgnoreCase(value)) {
						return ease;
					}
				}
			}
			return linear ? LINEAR : SMOOTH;
		}
	}

	public CameraKeyframe withTime(double value) {
		return new CameraKeyframe(Math.clamp(value, 0.0, 3600.0), position, yaw, pitch, roll, fov, ease);
	}

	public CameraKeyframe withFov(float value) {
		return new CameraKeyframe(time, position, yaw, pitch, roll, Math.clamp(value, MIN_FOV, MAX_FOV), ease);
	}

	public CameraKeyframe withRoll(float value) {
		return new CameraKeyframe(time, position, yaw, pitch, Math.clamp(value, -180f, 180f), fov, ease);
	}

	public CameraKeyframe withEase(Ease value) {
		return new CameraKeyframe(time, position, yaw, pitch, roll, fov, value);
	}

	public CameraKeyframe withView(Vec3 newPosition, float newYaw, float newPitch) {
		return new CameraKeyframe(time, newPosition, newYaw, newPitch, roll, fov, ease);
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
		object.addProperty("ease", ease.name().toLowerCase(Locale.ROOT));
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
			Ease.parse(Json.string(object, "ease", 16), Json.bool(object, "linear", false))
		);
	}
}

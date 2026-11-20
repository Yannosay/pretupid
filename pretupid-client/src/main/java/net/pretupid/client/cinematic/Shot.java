package net.pretupid.client.cinematic;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.pretupid.client.network.Json;

public final class Shot {
	public static final int MAX_KEYFRAMES = 128;

	public record View(Vec3 position, float yaw, float pitch, float roll, float fov) {
	}

	private String name;
	private final List<CameraKeyframe> keyframes = new ArrayList<>();
	private boolean smooth = true;
	private boolean loop;
	private boolean lookAtPlayer;
	private double speed = 1.0;

	public Shot(String name) {
		this.name = name;
	}

	public String name() {
		return name;
	}

	public void rename(String value) {
		if (value != null && !value.isBlank()) {
			name = value.strip().length() > 32 ? value.strip().substring(0, 32) : value.strip();
		}
	}

	public List<CameraKeyframe> keyframes() {
		return keyframes;
	}

	public boolean smooth() {
		return smooth;
	}

	public void setSmooth(boolean value) {
		smooth = value;
	}

	public boolean loop() {
		return loop;
	}

	public void setLoop(boolean value) {
		loop = value;
	}

	public boolean lookAtPlayer() {
		return lookAtPlayer;
	}

	public void setLookAtPlayer(boolean value) {
		lookAtPlayer = value;
	}

	public double speed() {
		return speed;
	}

	public void setSpeed(double value) {
		speed = Math.clamp(value, 0.25, 4.0);
	}

	public double duration() {
		return keyframes.isEmpty() ? 0.0 : keyframes.getLast().time();
	}

	public int add(CameraKeyframe keyframe) {
		if (keyframes.size() >= MAX_KEYFRAMES) {
			return -1;
		}
		keyframes.add(keyframe);
		sort();
		return keyframes.indexOf(keyframe);
	}

	public int replace(int index, CameraKeyframe keyframe) {
		keyframes.set(index, keyframe);
		sort();
		return keyframes.indexOf(keyframe);
	}

	public void remove(int index) {
		if (index >= 0 && index < keyframes.size()) {
			keyframes.remove(index);
		}
	}

	private void sort() {
		keyframes.sort(Comparator.comparingDouble(CameraKeyframe::time));
	}

	public View sample(double time) {
		int count = keyframes.size();
		if (count == 0) {
			return null;
		}
		CameraKeyframe first = keyframes.getFirst();
		if (count == 1 || time <= first.time()) {
			return view(first);
		}
		CameraKeyframe last = keyframes.getLast();
		if (time >= last.time()) {
			return view(last);
		}
		int index = 0;
		while (index < count - 2 && keyframes.get(index + 1).time() <= time) {
			index++;
		}
		CameraKeyframe a = keyframes.get(index);
		CameraKeyframe b = keyframes.get(index + 1);
		double span = Math.max(1e-4, b.time() - a.time());
		double t = Math.clamp((time - a.time()) / span, 0.0, 1.0);
		float[] yaws = unwrappedYaws();
		if (a.ease() == CameraKeyframe.Ease.HOLD) {
			return view(a);
		}
		if (!smooth) {
			double eased = a.ease().apply(t);
			return new View(a.position().lerp(b.position(), eased), (float) Mth.lerp(eased, yaws[index], yaws[index + 1]),
				(float) Mth.lerp(eased, a.pitch(), b.pitch()), (float) Mth.lerp(eased, a.roll(), b.roll()), (float) Mth.lerp(eased, a.fov(), b.fov()));
		}
		if (a.ease() != CameraKeyframe.Ease.SMOOTH) {
			t = a.ease().apply(t);
		}
		double x = hermite(index, t, span, k -> keyframes.get(k).position().x);
		double y = hermite(index, t, span, k -> keyframes.get(k).position().y);
		double z = hermite(index, t, span, k -> keyframes.get(k).position().z);
		float yaw = (float) hermite(index, t, span, k -> yaws[k]);
		float pitch = (float) Math.clamp(hermite(index, t, span, k -> keyframes.get(k).pitch()), -90.0, 90.0);
		float roll = (float) hermite(index, t, span, k -> keyframes.get(k).roll());
		float fov = (float) Math.clamp(hermite(index, t, span, k -> keyframes.get(k).fov()), CameraKeyframe.MIN_FOV, CameraKeyframe.MAX_FOV);
		return new View(new Vec3(x, y, z), yaw, pitch, roll, fov);
	}

	private float[] unwrappedYaws() {
		float[] yaws = new float[keyframes.size()];
		for (int i = 0; i < yaws.length; i++) {
			float yaw = keyframes.get(i).yaw();
			yaws[i] = i == 0 ? yaw : yaws[i - 1] + Mth.wrapDegrees(yaw - yaws[i - 1]);
		}
		return yaws;
	}

	private interface Channel {
		double at(int index);
	}

	private double hermite(int index, double t, double span, Channel channel) {
		double p0 = channel.at(index);
		double p1 = channel.at(index + 1);
		double m0 = tangent(index, channel) * span;
		double m1 = tangent(index + 1, channel) * span;
		double t2 = t * t;
		double t3 = t2 * t;
		return (2 * t3 - 3 * t2 + 1) * p0 + (t3 - 2 * t2 + t) * m0 + (-2 * t3 + 3 * t2) * p1 + (t3 - t2) * m1;
	}

	private double tangent(int index, Channel channel) {
		int last = keyframes.size() - 1;
		if (index <= 0 || index >= last) {
			return 0.0;
		}
		double before = keyframes.get(index).time() - keyframes.get(index - 1).time();
		double after = keyframes.get(index + 1).time() - keyframes.get(index).time();
		if (before <= 1e-4 || after <= 1e-4) {
			return 0.0;
		}
		double incoming = (channel.at(index) - channel.at(index - 1)) / before;
		double outgoing = (channel.at(index + 1) - channel.at(index)) / after;
		return (incoming * after + outgoing * before) / (before + after);
	}

	private static View view(CameraKeyframe keyframe) {
		return new View(keyframe.position(), keyframe.yaw(), keyframe.pitch(), keyframe.roll(), keyframe.fov());
	}

	public JsonObject toJson() {
		JsonObject object = new JsonObject();
		object.addProperty("name", name);
		object.addProperty("smooth", smooth);
		object.addProperty("loop", loop);
		object.addProperty("lookAtPlayer", lookAtPlayer);
		object.addProperty("speed", speed);
		JsonArray array = new JsonArray();
		for (CameraKeyframe keyframe : keyframes) {
			array.add(keyframe.toJson());
		}
		object.add("keyframes", array);
		return object;
	}

	public static Shot fromJson(JsonObject object) {
		String name = Json.string(object, "name", 32);
		Shot shot = new Shot(name == null || name.isBlank() ? "Shot" : name);
		shot.smooth = Json.bool(object, "smooth", true);
		shot.loop = Json.bool(object, "loop", false);
		shot.lookAtPlayer = Json.bool(object, "lookAtPlayer", false);
		shot.setSpeed(Json.decimal(object, "speed", 1.0));
		for (JsonElement element : Json.array(object, "keyframes")) {
			if (element.isJsonObject() && shot.keyframes.size() < MAX_KEYFRAMES) {
				CameraKeyframe keyframe = CameraKeyframe.fromJson(element.getAsJsonObject());
				if (keyframe != null) {
					shot.keyframes.add(keyframe);
				}
			}
		}
		shot.sort();
		return shot;
	}
}

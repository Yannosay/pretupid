package net.pretupid.client.cinematic;

import net.pretupid.client.platform.Platform;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.Vec3;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.config.JsonFiles;
import net.pretupid.client.module.visual.CinematicModule;
import net.pretupid.client.module.visual.FreecamModule;
import net.pretupid.client.util.SmoothClock;

public final class CinematicDirector {
	public enum State {
		IDLE,
		PREVIEW,
		PLAYING
	}

	private static final int MAX_SHOTS = 32;

	private final List<Shot> shots = new ArrayList<>();
	private String loadedKey;
	private int selected;
	private State state = State.IDLE;
	private double playhead;
	private long playStartedAt;
	private double playStartedFrom;
	private boolean dirty;
	private long nextSave;

	public State state() {
		return state;
	}

	public boolean playing() {
		return state == State.PLAYING;
	}

	public boolean controlsCamera() {
		return state != State.IDLE && current() != null && !current().keyframes().isEmpty();
	}

	public double playhead() {
		return playhead;
	}

	public List<Shot> shots() {
		ensureLoaded();
		return shots;
	}

	public int selectedIndex() {
		return selected;
	}

	public Shot current() {
		ensureLoaded();
		if (shots.isEmpty()) {
			return null;
		}
		selected = Math.clamp(selected, 0, shots.size() - 1);
		return shots.get(selected);
	}

	public Shot currentOrCreate() {
		Shot shot = current();
		if (shot == null) {
			shot = new Shot("Shot 1");
			shots.add(shot);
			selected = 0;
			markDirty();
		}
		return shot;
	}

	public void select(int index) {
		ensureLoaded();
		if (!shots.isEmpty()) {
			selected = Math.floorMod(index, shots.size());
			playhead = 0;
			stop();
		}
	}

	public void newShot() {
		ensureLoaded();
		if (shots.size() >= MAX_SHOTS) {
			return;
		}
		shots.add(new Shot("Shot " + (shots.size() + 1)));
		selected = shots.size() - 1;
		playhead = 0;
		stop();
		markDirty();
	}

	public void deleteShot() {
		ensureLoaded();
		if (!shots.isEmpty()) {
			shots.remove(Math.clamp(selected, 0, shots.size() - 1));
			selected = Math.max(0, selected - 1);
			playhead = 0;
			stop();
			markDirty();
		}
	}

	public int addKeyframeAtCamera(Minecraft minecraft, double spacing) {
		Camera camera = Platform.camera();
		if (minecraft.player == null || camera == null) {
			return -1;
		}
		Shot shot = currentOrCreate();
		double time = shot.keyframes().isEmpty() ? 0.0 : shot.duration() + spacing;
		FreecamModule freecam = PretupidClient.get().modules().get(FreecamModule.class);
		Vec3 position = freecam.active() ? freecam.position(1f) : camera.position();
		float yaw = freecam.active() ? freecam.yaw() : camera.yRot();
		float pitch = freecam.active() ? freecam.pitch() : camera.xRot();
		float fov = minecraft.options.fov().get().floatValue();
		int index = shot.add(new CameraKeyframe(time, position, yaw, pitch, 0f, fov, false));
		if (index >= 0) {
			playhead = time;
			markDirty();
		}
		return index;
	}

	public int updateKeyframe(int index, CameraKeyframe keyframe) {
		Shot shot = current();
		if (shot == null || index < 0 || index >= shot.keyframes().size()) {
			return index;
		}
		int next = shot.replace(index, keyframe);
		markDirty();
		return next;
	}

	public void removeKeyframe(int index) {
		Shot shot = current();
		if (shot != null) {
			shot.remove(index);
			markDirty();
		}
	}

	public void markDirty() {
		dirty = true;
		nextSave = System.currentTimeMillis() + 1500L;
	}

	public void play() {
		Shot shot = current();
		if (shot == null || shot.keyframes().size() < 2) {
			return;
		}
		if (playhead >= shot.duration() - 1e-3) {
			playhead = 0;
		}
		playStartedFrom = playhead;
		playStartedAt = SmoothClock.millisLong();
		state = State.PLAYING;
		FreecamModule freecam = PretupidClient.get().modules().get(FreecamModule.class);
		if (freecam.active()) {
			freecam.setHeld(Minecraft.getInstance(), false);
		}
	}

	public void pause() {
		if (state == State.PLAYING) {
			state = State.PREVIEW;
		}
	}

	public void stop() {
		state = State.IDLE;
	}

	public void preview(double time) {
		Shot shot = current();
		if (shot == null || shot.keyframes().isEmpty()) {
			return;
		}
		playhead = Math.clamp(time, 0.0, Math.max(0.0, shot.duration()));
		state = State.PREVIEW;
	}

	public void endPreview() {
		if (state == State.PREVIEW) {
			state = State.IDLE;
		}
	}

	public Shot.View view() {
		Shot shot = current();
		if (shot == null || state == State.IDLE) {
			return null;
		}
		if (state == State.PLAYING) {
			double elapsed = (SmoothClock.millisLong() - playStartedAt) / 1000.0;
			double duration = shot.duration();
			double time = playStartedFrom + elapsed;
			if (time >= duration) {
				if (shot.loop() && duration > 0) {
					time = time % duration;
				} else {
					time = duration;
				}
			}
			playhead = time;
		}
		return shot.sample(playhead);
	}

	public void tick(Minecraft minecraft) {
		if (minecraft.level == null || minecraft.player == null) {
			if (state != State.IDLE) {
				state = State.IDLE;
			}
			saveIfDirty(true);
			loadedKey = null;
			shots.clear();
			return;
		}
		String key = worldKey(minecraft);
		if (!key.equals(loadedKey)) {
			saveIfDirty(true);
			load(key);
		}
		CinematicModule module = PretupidClient.get().modules().get(CinematicModule.class);
		if (!module.isEnabled() && state != State.IDLE) {
			state = State.IDLE;
		}
		Shot shot = current();
		if (state == State.PLAYING && shot != null && !shot.loop() && playhead >= shot.duration() - 1e-4
			&& (SmoothClock.millisLong() - playStartedAt) / 1000.0 + playStartedFrom >= shot.duration() + 0.6) {
			state = State.PREVIEW;
		}
		saveIfDirty(false);
	}

	private void ensureLoaded() {
		if (loadedKey == null) {
			Minecraft minecraft = Minecraft.getInstance();
			if (minecraft.level != null) {
				load(worldKey(minecraft));
			}
		}
	}

	private static String worldKey(Minecraft minecraft) {
		MinecraftServer local = minecraft.getSingleplayerServer();
		String world;
		if (local != null) {
			world = "local:" + local.getWorldData().getLevelName().toLowerCase(Locale.ROOT);
		} else {
			ServerData server = minecraft.getCurrentServer();
			world = server == null ? "unknown" : "server:" + server.ip.toLowerCase(Locale.ROOT);
		}
		return world + "|" + minecraft.level.dimension().identifier();
	}

	private static Path file(String key) {
		String name = HexFormat.of().formatHex(key.getBytes(StandardCharsets.UTF_8));
		if (name.length() > 160) {
			name = name.substring(0, 120) + Integer.toHexString(key.hashCode());
		}
		return FabricLoader.getInstance().getConfigDir().resolve(PretupidClient.MOD_ID).resolve("cinematic").resolve(name + ".json");
	}

	private void load(String key) {
		shots.clear();
		selected = 0;
		playhead = 0;
		state = State.IDLE;
		loadedKey = key;
		dirty = false;
		JsonFiles.read(file(key)).ifPresent(object -> {
			for (JsonElement element : object.getAsJsonArray("shots") == null ? new JsonArray() : object.getAsJsonArray("shots")) {
				if (element.isJsonObject() && shots.size() < MAX_SHOTS) {
					shots.add(Shot.fromJson(element.getAsJsonObject()));
				}
			}
		});
	}

	private void saveIfDirty(boolean now) {
		if (!dirty || loadedKey == null || (!now && System.currentTimeMillis() < nextSave)) {
			return;
		}
		dirty = false;
		JsonObject object = new JsonObject();
		object.addProperty("world", loadedKey);
		JsonArray array = new JsonArray();
		for (Shot shot : shots) {
			array.add(shot.toJson());
		}
		object.add("shots", array);
		try {
			JsonFiles.write(file(loadedKey), object);
		} catch (IOException exception) {
			PretupidClient.LOGGER.warn("Could not save cinematic shots", exception);
		}
	}
}

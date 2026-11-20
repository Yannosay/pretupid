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
		PLAYING,
		RENDERING
	}

	public record RenderResult(Path folder, int frames, boolean failed) {
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
	private int renderFrame;
	private int renderFrames;
	private int renderFps;
	private int renderScale;
	private double renderBudget;
	private Path renderFolder;
	private final java.util.concurrent.atomic.AtomicInteger pendingWrites = new java.util.concurrent.atomic.AtomicInteger();
	private final java.util.concurrent.atomic.AtomicBoolean renderFailed = new java.util.concurrent.atomic.AtomicBoolean();
	private java.util.function.Consumer<RenderResult> renderDone;
	private static final java.util.concurrent.ExecutorService WRITER = java.util.concurrent.Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "Pretupid Cinematic Render");
		thread.setDaemon(true);
		return thread;
	});

	public State state() {
		return state;
	}

	public boolean playing() {
		return state == State.PLAYING;
	}

	public boolean controlsCamera() {
		return state != State.IDLE && current() != null && !current().keyframes().isEmpty();
	}

	public boolean rendering() {
		return state == State.RENDERING;
	}

	public float renderProgress() {
		return renderFrames <= 0 ? 0f : Math.min(1f, renderFrame / (float) renderFrames);
	}

	public void addTemplate(ShotTemplate template, Minecraft minecraft) {
		ensureLoaded();
		if (minecraft.player == null || shots.size() >= MAX_SHOTS) {
			return;
		}
		String label = template.name().charAt(0) + template.name().substring(1).toLowerCase(Locale.ROOT).replace('_', ' ');
		shots.add(template.create(minecraft.player, label + " " + (shots.size() + 1)));
		selected = shots.size() - 1;
		playhead = 0;
		stop();
		markDirty();
	}

	public int duplicateKeyframe(int index) {
		Shot shot = current();
		if (shot == null || index < 0 || index >= shot.keyframes().size()) {
			return -1;
		}
		CameraKeyframe source = shot.keyframes().get(index);
		double nextTime = index + 1 < shot.keyframes().size() ? (source.time() + shot.keyframes().get(index + 1).time()) / 2.0 : source.time() + 1.0;
		int added = shot.add(source.withTime(nextTime));
		if (added >= 0) {
			markDirty();
		}
		return added;
	}

	public boolean startRender(int fps, int scale, java.util.function.Consumer<RenderResult> done) {
		Shot shot = current();
		if (shot == null || shot.keyframes().size() < 2 || state == State.RENDERING) {
			return false;
		}
		String stamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss"));
		String safe = shot.name().replaceAll("[^0-9A-Za-z_-]+", "_");
		renderFolder = Minecraft.getInstance().gameDirectory.toPath().resolve("screenshots").resolve("pretupid-cinematic").resolve(safe + "_" + stamp);
		try {
			java.nio.file.Files.createDirectories(renderFolder);
		} catch (IOException exception) {
			PretupidClient.LOGGER.warn("Could not create the render folder", exception);
			return false;
		}
		renderFps = Math.clamp(fps, 10, 120);
		renderScale = Math.clamp(scale, 1, 4);
		renderFrame = 0;
		renderFrames = Math.max(1, (int) Math.ceil(shot.duration() * renderFps)) + 1;
		renderBudget = 0;
		renderFailed.set(false);
		renderDone = done;
		playhead = 0;
		state = State.RENDERING;
		FreecamModule freecam = PretupidClient.get().modules().get(FreecamModule.class);
		if (freecam.active()) {
			freecam.setHeld(Minecraft.getInstance(), false);
		}
		return true;
	}

	public void cancelRender() {
		if (state == State.RENDERING) {
			finishRender();
		}
	}

	private void renderTick() {
		renderBudget += renderFps / 20.0;
		while (renderBudget >= 1 && renderFrame < renderFrames && state == State.RENDERING) {
			renderBudget -= 1;
			if (pendingWrites.get() > 12) {
				return;
			}
			int frame = renderFrame++;
			playhead = frame / (double) renderFps;
			Path target = renderFolder.resolve(String.format(Locale.ROOT, "frame_%05d.png", frame + 1));
			pendingWrites.incrementAndGet();
			try {
				Platform.captureScreenie(renderScale, image -> WRITER.execute(() -> {
					try {
						image.writeToFile(target);
					} catch (IOException | RuntimeException exception) {
						renderFailed.set(true);
						PretupidClient.LOGGER.warn("Could not write render frame {}", target, exception);
					} finally {
						image.close();
						pendingWrites.decrementAndGet();
					}
				}));
			} catch (RuntimeException exception) {
				pendingWrites.decrementAndGet();
				renderFailed.set(true);
				PretupidClient.LOGGER.warn("Could not render cinematic frame", exception);
				finishRender();
				return;
			}
		}
		if (renderFrame >= renderFrames) {
			finishRender();
		}
	}

	private void finishRender() {
		state = State.PREVIEW;
		java.util.function.Consumer<RenderResult> done = renderDone;
		renderDone = null;
		if (done != null) {
			done.accept(new RenderResult(renderFolder, renderFrame, renderFailed.get()));
		}
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
		float yaw = freecam.active() ? freecam.yaw() : Platform.cameraYaw(camera);
		float pitch = freecam.active() ? freecam.pitch() : Platform.cameraPitch(camera);
		float fov = minecraft.options.fov().get().floatValue();
		int index = shot.add(new CameraKeyframe(time, position, yaw, pitch, 0f, fov, CameraKeyframe.Ease.SMOOTH));
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
		if (state == State.RENDERING) {
			finishRender();
		}
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
			double elapsed = (SmoothClock.millisLong() - playStartedAt) / 1000.0 * shot.speed();
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
		Shot.View view = shot.sample(playhead);
		return view != null && shot.lookAtPlayer() ? aimAtPlayer(view) : view;
	}

	private static Shot.View aimAtPlayer(Shot.View view) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) {
			return view;
		}
		Vec3 target = minecraft.player.getEyePosition(minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(true));
		Vec3 delta = target.subtract(view.position());
		double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
		if (horizontal < 1e-3 && Math.abs(delta.y) < 1e-3) {
			return view;
		}
		float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
		float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, horizontal));
		return new Shot.View(view.position(), yaw, pitch, view.roll(), view.fov());
	}

	public void tick(Minecraft minecraft) {
		if (minecraft.level == null || minecraft.player == null) {
			if (state == State.RENDERING) {
				finishRender();
			}
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
			if (state == State.RENDERING) {
				finishRender();
			}
			state = State.IDLE;
		}
		Shot shot = current();
		if (state == State.RENDERING) {
			renderTick();
		}
		if (state == State.PLAYING && shot != null && !shot.loop() && playhead >= shot.duration() - 1e-4
			&& (SmoothClock.millisLong() - playStartedAt) / 1000.0 * shot.speed() + playStartedFrom >= shot.duration() + 0.6) {
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

package net.pretupid.client.module.hud;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.WinScreen;
import net.minecraft.network.chat.Component;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.config.JsonFiles;
import net.pretupid.client.hud.HudModule;
import net.pretupid.client.hud.HudPosition;
import net.pretupid.client.module.Category;
import net.pretupid.client.module.utility.WaypointsModule;
import net.pretupid.client.platform.Gfx;
import net.pretupid.client.platform.Platform;
import net.pretupid.client.setting.BooleanSetting;
import net.pretupid.client.setting.ColorSetting;
import net.pretupid.client.setting.KeySetting;
import net.pretupid.client.ui.Draw;
import net.pretupid.client.ui.Icon;
import net.pretupid.client.ui.Theme;

public final class SpeedrunTimerModule extends HudModule {
	private static final int ROW_HEIGHT = 10;
	private static final int TIME_HEIGHT = 13;
	private static final float TIME_SCALE = 1.5f;
	private static final int MAX_WORLDS = 24;
	private static final long SAVE_INTERVAL_NANOS = 60_000_000_000L;
	private static final long FRESH_WORLD_TICKS = 600L;
	private static final long NANOS_PER_MILLI = 1_000_000L;
	private static final String NETHER = "minecraft:the_nether";
	private static final String END = "minecraft:the_end";

	private static final class Run {
		long nanos;
		long nether = -1L;
		long end = -1L;
		boolean finished;
		boolean running;
		long touched;

		long millis() {
			return nanos / NANOS_PER_MILLI;
		}
	}

	private final BooleanSetting autoStart = add(new BooleanSetting("auto_start", true));
	private final BooleanSetting pauseWithGame = add(new BooleanSetting("pause_with_game", true));
	private final BooleanSetting showSplits = add(new BooleanSetting("show_splits", true));
	private final BooleanSetting showMilliseconds = add(new BooleanSetting("show_milliseconds", true));
	private final KeySetting startKey = add(new KeySetting("start_key", KeySetting.UNBOUND));
	private final KeySetting resetKey = add(new KeySetting("reset_key", KeySetting.UNBOUND));
	private final ColorSetting timeColor = add(new ColorSetting("time_color", 0xFFFFFFFF).followingModuleText());
	private final ColorSetting labelColor = add(new ColorSetting("label_color", 0xFFA3ABB9));

	private final Path file = FabricLoader.getInstance().getConfigDir().resolve(PretupidClient.MOD_ID).resolve("speedrun.json");
	private final Map<String, Run> runs = new LinkedHashMap<>();
	private long bestTotal = -1L;
	private long bestNether = -1L;
	private long bestEnd = -1L;
	private boolean loaded;
	private String worldKey;
	private Run run;
	private boolean armed;
	private float armedYaw;
	private float armedPitch;
	private long lastNano = System.nanoTime();
	private long lastSave = System.nanoTime();
	private boolean startHeld;
	private boolean resetHeld;

	public SpeedrunTimerModule() {
		super("speedrun", Category.HUD, "clock", false, new HudPosition(HudPosition.Anchor.END, 4, HudPosition.Anchor.START, 84));
	}

	@Override
	public void onTick(Minecraft minecraft) {
		if (minecraft.player == null || minecraft.level == null) {
			leaveWorld();
			startHeld = false;
			resetHeld = false;
			return;
		}
		ensureLoaded();
		String key = WaypointsModule.worldKey(minecraft);
		if (!key.equals(worldKey)) {
			leaveWorld();
			enterWorld(minecraft, key);
		}
		advance(minecraft);
		handleKeys(minecraft);
		if (armed && hasInput(minecraft)) {
			armed = false;
			begin();
		}
		if (run != null && run.running) {
			updateSplits(minecraft);
			long now = System.nanoTime();
			if (now - lastSave >= SAVE_INTERVAL_NANOS) {
				persist();
			}
		}
	}

	@Override
	protected void onDisable() {
		leaveWorld();
	}

	private void enterWorld(Minecraft minecraft, String key) {
		worldKey = key;
		run = runs.get(key);
		lastNano = System.nanoTime();
		armed = run == null && autoStart.enabled() && minecraft.hasSingleplayerServer() && minecraft.level.getGameTime() < FRESH_WORLD_TICKS;
		if (armed) {
			captureLook(minecraft);
		}
	}

	private void leaveWorld() {
		if (worldKey == null) {
			return;
		}
		persist();
		worldKey = null;
		run = null;
		armed = false;
	}

	private void captureLook(Minecraft minecraft) {
		armedYaw = minecraft.player.getYRot();
		armedPitch = minecraft.player.getXRot();
	}

	private boolean clockHeld(Minecraft minecraft) {
		return pauseWithGame.enabled() && minecraft.isPaused();
	}

	private void advance(Minecraft minecraft) {
		long now = System.nanoTime();
		if (run != null && run.running && !clockHeld(minecraft)) {
			run.nanos += now - lastNano;
		}
		lastNano = now;
	}

	private void handleKeys(Minecraft minecraft) {
		boolean free = Platform.screen() == null;
		boolean startDown = free && startKey.isBound() && Platform.isKeyDown(startKey.get());
		if (startDown && !startHeld) {
			if (run != null && run.running) {
				pause();
			} else {
				begin();
			}
		}
		startHeld = startDown;
		boolean resetDown = free && resetKey.isBound() && Platform.isKeyDown(resetKey.get());
		if (resetDown && !resetHeld) {
			reset(minecraft);
		}
		resetHeld = resetDown;
	}

	private boolean hasInput(Minecraft minecraft) {
		if (Platform.screen() != null) {
			return false;
		}
		var options = minecraft.options;
		return options.keyUp.isDown() || options.keyDown.isDown() || options.keyLeft.isDown() || options.keyRight.isDown() || options.keyJump.isDown()
			|| minecraft.player.getYRot() != armedYaw || minecraft.player.getXRot() != armedPitch;
	}

	private void begin() {
		if (worldKey == null) {
			return;
		}
		armed = false;
		if (run == null || run.finished) {
			run = new Run();
			runs.put(worldKey, run);
		}
		run.running = true;
		lastNano = System.nanoTime();
		persist();
	}

	private void pause() {
		if (run != null && run.running) {
			run.running = false;
			persist();
		}
	}

	private void reset(Minecraft minecraft) {
		if (worldKey == null) {
			return;
		}
		runs.remove(worldKey);
		run = null;
		armed = autoStart.enabled();
		if (armed) {
			captureLook(minecraft);
		}
		persist();
	}

	private void updateSplits(Minecraft minecraft) {
		String dimension = WaypointsModule.dimension(minecraft);
		boolean changed = false;
		if (run.nether < 0L && dimension.equals(NETHER)) {
			run.nether = run.millis();
			changed = true;
		}
		if (run.end < 0L && dimension.equals(END)) {
			run.end = run.millis();
			changed = true;
		}
		if (run.end >= 0L && Platform.screen() instanceof WinScreen) {
			finish();
			return;
		}
		if (changed) {
			persist();
		}
	}

	private void finish() {
		run.running = false;
		run.finished = true;
		long total = run.millis();
		boolean record = bestTotal < 0L || total < bestTotal;
		if (record) {
			bestTotal = total;
			bestNether = run.nether;
			bestEnd = run.end;
		}
		persist();
		PretupidClient.get().notifications().push(name(),
			Component.translatable(record ? "pretupid.speedrun.new_best" : "pretupid.speedrun.finished", format(total, true)),
			Icon.item("clock"), null, record ? Theme.SUCCESS : Theme.accent(), 6000L);
	}

	public Component commandStart(Minecraft minecraft) {
		if (!prepareCommand(minecraft)) {
			return noWorld();
		}
		begin();
		return Component.translatable("pretupid.speedrun.started").withStyle(ChatFormatting.GRAY);
	}

	public Component commandPause(Minecraft minecraft) {
		if (!prepareCommand(minecraft)) {
			return noWorld();
		}
		pause();
		return Component.translatable("pretupid.speedrun.paused").withStyle(ChatFormatting.GRAY);
	}

	public Component commandReset(Minecraft minecraft) {
		if (!prepareCommand(minecraft)) {
			return noWorld();
		}
		reset(minecraft);
		return Component.translatable("pretupid.speedrun.reset").withStyle(ChatFormatting.GRAY);
	}

	private static Component noWorld() {
		return Component.translatable("pretupid.waypoints.no_world").withStyle(ChatFormatting.RED);
	}

	private boolean prepareCommand(Minecraft minecraft) {
		if (minecraft.player == null || minecraft.level == null) {
			return false;
		}
		if (!isEnabled()) {
			setEnabled(true);
		}
		ensureLoaded();
		String key = WaypointsModule.worldKey(minecraft);
		if (!key.equals(worldKey)) {
			leaveWorld();
			enterWorld(minecraft, key);
		}
		advance(minecraft);
		return true;
	}

	private void ensureLoaded() {
		if (loaded) {
			return;
		}
		loaded = true;
		JsonFiles.read(file).ifPresent(root -> {
			try {
				if (root.has("best") && root.get("best").isJsonObject()) {
					JsonObject best = root.getAsJsonObject("best");
					bestTotal = readLong(best, "total");
					bestNether = readLong(best, "nether");
					bestEnd = readLong(best, "end");
				}
				if (root.has("worlds") && root.get("worlds").isJsonObject()) {
					for (var entry : root.getAsJsonObject("worlds").entrySet()) {
						if (!entry.getValue().isJsonObject()) {
							continue;
						}
						JsonObject object = entry.getValue().getAsJsonObject();
						Run stored = new Run();
						stored.nanos = Math.max(0L, readLong(object, "time")) * NANOS_PER_MILLI;
						stored.nether = readLong(object, "nether");
						stored.end = readLong(object, "end");
						stored.finished = object.has("finished") && object.get("finished").getAsBoolean();
						stored.running = !stored.finished && object.has("running") && object.get("running").getAsBoolean();
						stored.touched = Math.max(0L, readLong(object, "touched"));
						runs.put(entry.getKey(), stored);
					}
				}
			} catch (RuntimeException exception) {
				PretupidClient.LOGGER.warn("Ignoring malformed speedrun data: {}", exception.getMessage());
			}
		});
	}

	private static long readLong(JsonObject object, String key) {
		return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsLong() : -1L;
	}

	private void persist() {
		lastSave = System.nanoTime();
		if (run != null) {
			run.touched = System.currentTimeMillis();
		}
		while (runs.size() > MAX_WORLDS) {
			runs.entrySet().stream()
				.filter(entry -> entry.getValue() != run)
				.min(Comparator.comparingLong(entry -> entry.getValue().touched))
				.map(Map.Entry::getKey)
				.ifPresent(runs::remove);
		}
		JsonObject best = new JsonObject();
		best.addProperty("total", bestTotal);
		best.addProperty("nether", bestNether);
		best.addProperty("end", bestEnd);
		JsonObject worlds = new JsonObject();
		for (Map.Entry<String, Run> entry : runs.entrySet()) {
			Run value = entry.getValue();
			JsonObject object = new JsonObject();
			object.addProperty("time", value.millis());
			object.addProperty("nether", value.nether);
			object.addProperty("end", value.end);
			object.addProperty("finished", value.finished);
			object.addProperty("running", value.running);
			object.addProperty("touched", value.touched);
			worlds.add(entry.getKey(), object);
		}
		JsonObject root = new JsonObject();
		root.add("best", best);
		root.add("worlds", worlds);
		try {
			JsonFiles.write(file, root);
		} catch (IOException exception) {
			PretupidClient.LOGGER.warn("Could not save speedrun data: {}", exception.getMessage());
		}
	}

	private String format(long millis, boolean withMillis) {
		long safe = Math.max(0L, millis);
		long hours = safe / 3_600_000L;
		long minutes = (safe / 60_000L) % 60L;
		long seconds = (safe / 1000L) % 60L;
		long rest = safe % 1000L;
		String base = hours > 0L
			? String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
			: String.format(Locale.ROOT, "%d:%02d", minutes, seconds);
		return withMillis ? base + String.format(Locale.ROOT, ".%03d", rest) : base;
	}

	private String delta(long value, long best) {
		if (value < 0L || best < 0L) {
			return "";
		}
		long difference = value - best;
		return (difference <= 0L ? "-" : "+") + format(Math.abs(difference), false);
	}

	private long displayMillis(Minecraft minecraft, boolean preview) {
		if (preview && run == null) {
			return 754_318L;
		}
		if (run == null) {
			return 0L;
		}
		long nanos = run.nanos;
		if (run.running && !clockHeld(minecraft)) {
			nanos += System.nanoTime() - lastNano;
		}
		return nanos / NANOS_PER_MILLI;
	}

	private long splitValue(boolean nether, boolean preview) {
		if (preview && run == null) {
			return nether ? 241_500L : 612_040L;
		}
		if (run == null) {
			return -1L;
		}
		return nether ? run.nether : run.end;
	}

	private String splitText(long value) {
		return value < 0L ? "--:--" : format(value, false);
	}

	@Override
	public int width(Font font, Minecraft minecraft, boolean preview) {
		String time = format(displayMillis(minecraft, preview), showMilliseconds.enabled());
		String sample = time.replaceAll("\\d", "0");
		int width = Math.round(font.width(sample) * TIME_SCALE);
		if (showSplits.enabled()) {
			int labels = Math.max(font.width(Component.translatable("pretupid.speedrun.nether")), font.width(Component.translatable("pretupid.speedrun.end")));
			width = Math.max(width, labels + 6 + font.width("0:00:00") + 4 + font.width("+0:00:00"));
		}
		return width + padding() * 2;
	}

	@Override
	public int height(Font font, Minecraft minecraft, boolean preview) {
		return TIME_HEIGHT + (showSplits.enabled() ? ROW_HEIGHT * 2 + 1 : 0) + padding() * 2;
	}

	@Override
	public void render(Gfx graphics, Font font, Minecraft minecraft, boolean preview) {
		int width = width(font, minecraft, preview);
		drawBackground(graphics, width, height(font, minecraft, preview));
		int x = padding();
		int y = padding();
		boolean shadow = textShadow.enabled();
		int color = timeColor.resolve();
		if (run != null && run.finished) {
			color = Theme.SUCCESS;
		} else if (!preview && (run == null || !run.running || clockHeld(minecraft))) {
			color = labelColor.resolve();
		}
		Component time = Component.literal(format(displayMillis(minecraft, preview), showMilliseconds.enabled()));
		Draw.scaled(graphics, font, time, x, y, TIME_SCALE, color, shadow);
		if (!showSplits.enabled()) {
			return;
		}
		int rowY = y + TIME_HEIGHT + 1;
		drawSplit(graphics, font, Component.translatable("pretupid.speedrun.nether"), splitValue(true, preview), bestNether, x, rowY, width - padding() * 2, shadow);
		drawSplit(graphics, font, Component.translatable("pretupid.speedrun.end"), splitValue(false, preview), bestEnd, x, rowY + ROW_HEIGHT, width - padding() * 2, shadow);
	}

	private void drawSplit(Gfx graphics, Font font, Component label, long value, long best, int x, int y, int width, boolean shadow) {
		int muted = labelColor.resolve();
		graphics.text(font, label, x, y, muted, shadow);
		String deltaText = delta(value, best);
		int right = x + width;
		if (!deltaText.isEmpty()) {
			graphics.text(font, deltaText, right - font.width(deltaText), y, deltaText.startsWith("-") ? Theme.SUCCESS : Theme.DANGER, shadow);
			right -= font.width("+0:00:00") + 4;
		}
		String text = splitText(value);
		graphics.text(font, text, right - font.width(text), y, timeColor.resolve(), shadow);
	}
}

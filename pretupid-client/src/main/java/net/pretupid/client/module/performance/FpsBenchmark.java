package net.pretupid.client.module.performance;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.network.chat.Component;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.module.Module;
import net.pretupid.client.ui.Icon;

public final class FpsBenchmark {
	private static final long WARMUP_NANOS = 5_000_000_000L;
	private static final long MEASURE_NANOS = 10_000_000_000L;
	private static final Icon ICON = Icon.item("clock_00");

	private enum Phase {
		IDLE,
		WARMUP_OFF,
		MEASURE_OFF,
		WARMUP_ON,
		MEASURE_ON
	}

	public record Result(double averageFps, double onePercentLow, int frames) {
	}

	private final List<Class<? extends Module>> optimizations = List.of(FpsBoostModule.class, EntityCullingModule.class, ParticleLimiterModule.class);
	private final Map<Module, Boolean> originalState = new LinkedHashMap<>();
	private final LongArrayList frameTimes = new LongArrayList();
	private Phase phase = Phase.IDLE;
	private long phaseStart;
	private long lastFrame;
	private Result vanilla;
	private int savedLimit;
	private boolean savedVsync;

	public boolean running() {
		return phase != Phase.IDLE;
	}

	public float progress() {
		if (phase == Phase.IDLE) {
			return 0;
		}
		long total = 2 * (WARMUP_NANOS + MEASURE_NANOS);
		long elapsed = System.nanoTime() - phaseStart + switch (phase) {
			case WARMUP_OFF, IDLE -> 0;
			case MEASURE_OFF -> WARMUP_NANOS;
			case WARMUP_ON -> WARMUP_NANOS + MEASURE_NANOS;
			case MEASURE_ON -> 2 * WARMUP_NANOS + MEASURE_NANOS;
		};
		return Math.clamp((float) elapsed / total, 0F, 1F);
	}

	public boolean measuringOptimized() {
		return phase == Phase.WARMUP_ON || phase == Phase.MEASURE_ON;
	}

	public boolean start(Minecraft minecraft) {
		if (running() || minecraft.level == null || minecraft.player == null) {
			return false;
		}
		PretupidClient client = PretupidClient.get();
		Options options = minecraft.options;
		savedLimit = options.framerateLimit().get();
		savedVsync = options.enableVsync().get();
		options.framerateLimit().set(Options.UNLIMITED_FRAMERATE_CUTOFF);
		options.enableVsync().set(false);
		originalState.clear();
		Component reason = Component.translatable("pretupid.benchmark.paused");
		for (Class<? extends Module> type : optimizations) {
			Module module = client.modules().get(type);
			if (module.isPaused()) {
				continue;
			}
			originalState.put(module, module.enabledSetting().get());
			module.pause(reason);
		}
		vanilla = null;
		enter(Phase.WARMUP_OFF);
		message(minecraft, Component.translatable("pretupid.benchmark.started").withStyle(ChatFormatting.GRAY));
		client.notifications().info(Component.translatable("pretupid.benchmark.title"), Component.translatable("pretupid.benchmark.hold_still"), ICON);
		return true;
	}

	public void cancel(Minecraft minecraft, Component reason) {
		if (!running()) {
			return;
		}
		restore(minecraft);
		message(minecraft, Component.translatable("pretupid.benchmark.cancelled", reason).withStyle(ChatFormatting.RED));
	}

	public void onFrame(Minecraft minecraft) {
		if (phase == Phase.IDLE) {
			return;
		}
		long now = System.nanoTime();
		if (minecraft.level == null || minecraft.player == null) {
			cancel(minecraft, Component.translatable("pretupid.benchmark.reason.left"));
			return;
		}
		if (!minecraft.isWindowActive()) {
			cancel(minecraft, Component.translatable("pretupid.benchmark.reason.focus"));
			return;
		}
		if (minecraft.gui.screen() != null && now - phaseStart > 500_000_000L) {
			cancel(minecraft, Component.translatable("pretupid.benchmark.reason.screen"));
			return;
		}
		if ((phase == Phase.MEASURE_OFF || phase == Phase.MEASURE_ON) && lastFrame != 0) {
			frameTimes.add(now - lastFrame);
		}
		lastFrame = now;
		long elapsed = now - phaseStart;
		switch (phase) {
			case WARMUP_OFF -> {
				if (elapsed >= WARMUP_NANOS) {
					enter(Phase.MEASURE_OFF);
				}
			}
			case MEASURE_OFF -> {
				if (elapsed >= MEASURE_NANOS) {
					vanilla = summarize();
					enableOptimizations();
					enter(Phase.WARMUP_ON);
				}
			}
			case WARMUP_ON -> {
				if (elapsed >= WARMUP_NANOS) {
					enter(Phase.MEASURE_ON);
				}
			}
			case MEASURE_ON -> {
				if (elapsed >= MEASURE_NANOS) {
					Result optimized = summarize();
					Result baseline = vanilla;
					restore(minecraft);
					report(minecraft, baseline, optimized);
				}
			}
			case IDLE -> {
			}
		}
	}

	private void enter(Phase next) {
		phase = next;
		phaseStart = System.nanoTime();
		lastFrame = 0;
		frameTimes.clear();
	}

	private void enableOptimizations() {
		for (Map.Entry<Module, Boolean> entry : originalState.entrySet()) {
			Module module = entry.getKey();
			module.resume();
			if (!module.enabledSetting().get()) {
				module.setEnabled(true);
			}
		}
	}

	private void restore(Minecraft minecraft) {
		phase = Phase.IDLE;
		List<Map.Entry<Module, Boolean>> entries = new ArrayList<>(originalState.entrySet());
		originalState.clear();
		for (Map.Entry<Module, Boolean> entry : entries) {
			Module module = entry.getKey();
			module.resume();
			if (module.enabledSetting().get() != entry.getValue()) {
				module.setEnabled(entry.getValue());
			}
		}
		Options options = minecraft.options;
		options.framerateLimit().set(savedLimit);
		options.enableVsync().set(savedVsync);
		options.save();
		frameTimes.clear();
	}

	private Result summarize() {
		int count = frameTimes.size();
		if (count == 0) {
			return new Result(0, 0, 0);
		}
		long[] times = frameTimes.toLongArray();
		long total = 0;
		for (long time : times) {
			total += time;
		}
		Arrays.sort(times);
		int slowest = Math.max(1, count / 100);
		long slowTotal = 0;
		for (int i = count - slowest; i < count; i++) {
			slowTotal += times[i];
		}
		double average = count * 1_000_000_000.0 / total;
		double low = slowest * 1_000_000_000.0 / slowTotal;
		return new Result(average, low, count);
	}

	private void report(Minecraft minecraft, Result baseline, Result optimized) {
		if (baseline == null || baseline.frames() == 0 || optimized.frames() == 0) {
			message(minecraft, Component.translatable("pretupid.benchmark.no_data").withStyle(ChatFormatting.RED));
			return;
		}
		double gain = (optimized.averageFps() / baseline.averageFps() - 1) * 100;
		double lowGain = (optimized.onePercentLow() / baseline.onePercentLow() - 1) * 100;
		message(minecraft, Component.translatable("pretupid.benchmark.result.title").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
		message(minecraft, Component.translatable("pretupid.benchmark.result.vanilla", round(baseline.averageFps()), round(baseline.onePercentLow())).withStyle(ChatFormatting.GRAY));
		message(minecraft, Component.translatable("pretupid.benchmark.result.optimized", round(optimized.averageFps()), round(optimized.onePercentLow())).withStyle(ChatFormatting.WHITE));
		message(minecraft, Component.translatable("pretupid.benchmark.result.gain", signed(gain), signed(lowGain)).withStyle(gain >= 0 ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
		PretupidClient.get().notifications().info(Component.translatable("pretupid.benchmark.title"),
			Component.translatable("pretupid.benchmark.result.short", round(baseline.averageFps()), round(optimized.averageFps()), signed(gain)), ICON);
	}

	private static String round(double value) {
		return Long.toString(Math.round(value));
	}

	private static String signed(double value) {
		long rounded = Math.round(value);
		return (rounded > 0 ? "+" : "") + rounded + "%";
	}

	private static void message(Minecraft minecraft, Component text) {
		if (minecraft.player != null) {
			minecraft.player.sendSystemMessage(text);
		}
	}
}

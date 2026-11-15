package net.pretupid.client.update;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.PretupidInfo;
import net.pretupid.client.cosmetic.CapeTextures;
import net.pretupid.client.network.ApiClient;
import net.pretupid.client.network.Json;
import net.pretupid.client.ui.Icon;

public final class UpdateService {
	public enum State {
		IDLE,
		CHECKING,
		UP_TO_DATE,
		AVAILABLE,
		DOWNLOADING,
		READY,
		FAILED
	}

	private static final long FIRST_CHECK_DELAY_MS = 8_000L;
	private static final long CHECK_INTERVAL_MS = 6 * 60 * 60_000L;
	private static final int MAX_JAR_BYTES = 64 * 1024 * 1024;

	private final ApiClient api;
	private final long startedAt = System.currentTimeMillis();
	private volatile State state = State.IDLE;
	private volatile Release release;
	private volatile String error;
	private long nextCheck;
	private boolean notified;
	private Path stagedJar;
	private Path currentJar;
	private Path targetJar;

	public record Release(String version, URI url, String sha256, long size, String notes, String page) {
	}

	public UpdateService(ApiClient api) {
		this.api = api;
		this.nextCheck = startedAt + FIRST_CHECK_DELAY_MS;
	}

	public State state() {
		return state;
	}

	public Release release() {
		return release;
	}

	public String error() {
		return error;
	}

	public boolean updateVisible() {
		Release current = release;
		return current != null && (state == State.AVAILABLE || state == State.DOWNLOADING || state == State.READY || state == State.FAILED)
			&& !current.version().equals(PretupidClient.get().settings().skippedUpdate.get());
	}

	public void tick(Minecraft minecraft) {
		if (!PretupidClient.get().settings().updateChecks.enabled() || state == State.DOWNLOADING || state == State.READY) {
			return;
		}
		if (System.currentTimeMillis() >= nextCheck && state != State.CHECKING) {
			check();
		}
	}

	public void check() {
		nextCheck = System.currentTimeMillis() + CHECK_INTERVAL_MS;
		state = State.CHECKING;
		String minecraftVersion = SharedConstants.getCurrentVersion().name();
		api.json("GET", "/updates?mc=" + java.net.URLEncoder.encode(minecraftVersion, java.nio.charset.StandardCharsets.UTF_8), null, null)
			.whenComplete((response, failure) -> Minecraft.getInstance().execute(() -> {
				if (failure != null) {
					state = State.IDLE;
					nextCheck = System.currentTimeMillis() + 30 * 60_000L;
					return;
				}
				if (!Json.bool(response, "available", false)) {
					state = State.UP_TO_DATE;
					return;
				}
				String version = Json.string(response, "version", 40);
				String url = Json.string(response, "url", 1024);
				String sha256 = Json.string(response, "sha256");
				if (version == null || url == null || sha256 == null || !sha256.matches("[0-9a-f]{64}")
					|| Versions.compare(version, PretupidInfo.shortVersion()) <= 0) {
					state = State.UP_TO_DATE;
					return;
				}
				URI uri;
				try {
					uri = URI.create(url);
				} catch (IllegalArgumentException exception) {
					state = State.UP_TO_DATE;
					return;
				}
				if (!ApiClient.isTrustedContentHost(uri)) {
					state = State.UP_TO_DATE;
					return;
				}
				String notes = Json.string(response, "notes", 4000);
				String page = Json.string(response, "page", 512);
				release = new Release(version, uri, sha256, Json.number(response, "size", 0L), notes == null ? "" : notes, page);
				state = State.AVAILABLE;
				notifyAvailable();
			}));
	}

	private void notifyAvailable() {
		if (notified || !updateVisible()) {
			return;
		}
		notified = true;
		PretupidClient.get().notifications().push(
			Component.translatable("pretupid.update.toast_title"),
			Component.translatable("pretupid.update.toast_body", release.version()),
			Icon.item("firework_rocket"),
			null,
			0xFF4ADE80,
			8000L
		);
	}

	public Optional<String> unsupportedReason() {
		Optional<Path> jar = currentJar();
		if (jar.isEmpty()) {
			return Optional.of("pretupid.update.unsupported_dev");
		}
		return Optional.empty();
	}

	private static Optional<Path> currentJar() {
		Optional<ModContainer> container = FabricLoader.getInstance().getModContainer(PretupidClient.MOD_ID);
		if (container.isEmpty() || FabricLoader.getInstance().isDevelopmentEnvironment()) {
			return Optional.empty();
		}
		List<Path> paths = container.get().getOrigin().getPaths();
		if (paths.size() != 1) {
			return Optional.empty();
		}
		Path path = paths.getFirst().toAbsolutePath().normalize();
		return Files.isRegularFile(path) && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar") ? Optional.of(path) : Optional.empty();
	}

	public void install() {
		Release target = release;
		Optional<Path> jar = currentJar();
		if (target == null || state == State.DOWNLOADING || state == State.READY) {
			return;
		}
		if (jar.isEmpty()) {
			state = State.FAILED;
			error = Component.translatable("pretupid.update.unsupported_dev").getString();
			return;
		}
		state = State.DOWNLOADING;
		error = null;
		currentJar = jar.get();
		String fileName = "pretupid-client-" + target.version().replaceAll("[^0-9A-Za-z.+_-]", "") + "+mc" + SharedConstants.getCurrentVersion().name() + ".jar";
		targetJar = currentJar.resolveSibling(fileName);
		Path stagingDirectory = currentJar.getParent().resolve(".pretupid-update");
		api.download(target.url(), MAX_JAR_BYTES).thenApply(bytes -> {
			if (!CapeTextures.sha256(bytes).equals(target.sha256())) {
				throw new IllegalStateException("Downloaded update failed verification");
			}
			if (target.size() > 0 && bytes.length != target.size()) {
				throw new IllegalStateException("Downloaded update has the wrong size");
			}
			try {
				Files.createDirectories(stagingDirectory);
				Path staged = stagingDirectory.resolve(fileName);
				Files.write(staged, bytes);
				return staged;
			} catch (IOException exception) {
				throw new IllegalStateException("Could not save the update: " + exception.getMessage());
			}
		}).whenComplete((staged, failure) -> Minecraft.getInstance().execute(() -> {
			if (failure != null) {
				state = State.FAILED;
				error = ApiClient.describe(failure);
				return;
			}
			stagedJar = staged;
			finishStaging();
		}));
	}

	private void finishStaging() {
		boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
		if (!windows) {
			try {
				Files.move(stagedJar, targetJar, StandardCopyOption.REPLACE_EXISTING);
				if (!currentJar.equals(targetJar)) {
					Files.deleteIfExists(currentJar);
				}
				Files.deleteIfExists(stagedJar.getParent());
				state = State.READY;
			} catch (IOException exception) {
				state = State.FAILED;
				error = exception.getMessage();
			}
			return;
		}
		try {
			Path helperDirectory = Files.createTempDirectory("pretupid-update");
			Path helperJar = helperDirectory.resolve("helper.jar");
			Files.copy(currentJar, helperJar, StandardCopyOption.REPLACE_EXISTING);
			String java = ProcessHandle.current().info().command().orElse(null);
			if (java == null) {
				throw new IOException("Java runtime path is unknown");
			}
			long pid = ProcessHandle.current().pid();
			List<String> command = List.of(java, "-cp", helperJar.toString(), UpdateHelper.class.getName(), Long.toString(pid),
				currentJar.toString(), stagedJar.toString(), targetJar.toString());
			Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				try {
					new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
				} catch (IOException exception) {
					PretupidClient.LOGGER.error("Could not start the Pretupid updater", exception);
				}
			}, "Pretupid Update"));
			state = State.READY;
		} catch (IOException exception) {
			state = State.FAILED;
			error = exception.getMessage();
		}
	}

	public void skip() {
		Release current = release;
		if (current != null) {
			PretupidClient.get().settings().skippedUpdate.set(current.version());
		}
	}

	public static final class Versions {
		private Versions() {
		}

		public static int compare(String left, String right) {
			String[] a = core(left);
			String[] b = core(right);
			String[] aNumbers = a[0].split("\\.");
			String[] bNumbers = b[0].split("\\.");
			for (int i = 0; i < Math.max(aNumbers.length, bNumbers.length); i++) {
				int difference = Long.compare(number(aNumbers, i), number(bNumbers, i));
				if (difference != 0) {
					return difference;
				}
			}
			if (a[1].equals(b[1])) {
				return 0;
			}
			if (a[1].isEmpty()) {
				return 1;
			}
			if (b[1].isEmpty()) {
				return -1;
			}
			return a[1].compareTo(b[1]);
		}

		private static String[] core(String value) {
			String base = value.startsWith("v") ? value.substring(1) : value;
			int plus = base.indexOf('+');
			if (plus >= 0) {
				base = base.substring(0, plus);
			}
			int dash = base.indexOf('-');
			return dash < 0 ? new String[] {base, ""} : new String[] {base.substring(0, dash), base.substring(dash + 1)};
		}

		private static long number(String[] parts, int index) {
			if (index >= parts.length) {
				return 0;
			}
			try {
				return Long.parseLong(parts[index]);
			} catch (NumberFormatException exception) {
				return 0;
			}
		}
	}
}

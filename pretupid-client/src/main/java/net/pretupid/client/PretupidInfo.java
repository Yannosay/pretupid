package net.pretupid.client;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import net.fabricmc.loader.api.FabricLoader;

public final class PretupidInfo {
	public static final String DEFAULT_API = "https://pretupid.yannosay.com/api/client/v1";
	public static final String DEFAULT_REALTIME = "wss://pretupid-realtime.yannosay.com/connect";
	private static final String API_PROPERTY = "pretupid.api";
	private static final String API_ENV = "PRETUPID_CLIENT_API";
	private static final String LAUNCHER_PROPERTY = "pretupid.launcher";
	private static final String LAUNCHER_VERSION_PROPERTY = "pretupid.launcher.version";

	private static String version;
	private static URI apiBase;

	private PretupidInfo() {
	}

	public static String version() {
		if (version == null) {
			version = FabricLoader.getInstance().getModContainer(PretupidClient.MOD_ID)
				.map(container -> container.getMetadata().getVersion().getFriendlyString())
				.orElse("dev");
		}
		return version;
	}

	private static String minecraftVersion;

	public static String minecraftVersion() {
		if (minecraftVersion == null) {
			minecraftVersion = FabricLoader.getInstance().getModContainer("minecraft")
				.map(container -> container.getMetadata().getVersion().getFriendlyString())
				.orElse("unknown");
		}
		return minecraftVersion;
	}

	public static String shortVersion() {
		String full = version();
		int plus = full.indexOf('+');
		return plus > 0 ? full.substring(0, plus) : full;
	}

	public static String displayVersion() {
		String version = shortVersion();
		int dash = version.indexOf('-');
		if (dash < 0) {
			return version;
		}
		String stage = version.substring(dash + 1);
		String label = stage.isEmpty() ? "" : Character.toUpperCase(stage.charAt(0)) + stage.substring(1);
		return version.substring(0, dash) + " " + label;
	}

	public static boolean launchedByPretupidLauncher() {
		return Boolean.parseBoolean(System.getProperty(LAUNCHER_PROPERTY, "false"));
	}

	public static Optional<String> launcherVersion() {
		return Optional.ofNullable(System.getProperty(LAUNCHER_VERSION_PROPERTY)).filter(value -> !value.isBlank());
	}

	public static URI apiBase() {
		if (apiBase == null) {
			String configured = System.getProperty(API_PROPERTY);
			if (configured == null || configured.isBlank()) {
				configured = System.getenv(API_ENV);
			}
			apiBase = parseApi(configured).orElseGet(() -> URI.create(DEFAULT_API));
		}
		return apiBase;
	}

	private static Optional<URI> parseApi(String value) {
		if (value == null || value.isBlank()) {
			return Optional.empty();
		}
		try {
			URI uri = URI.create(value.trim().replaceAll("/+$", ""));
			String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
			boolean local = "localhost".equalsIgnoreCase(uri.getHost()) || "127.0.0.1".equals(uri.getHost());
			if ("https".equals(scheme) || ("http".equals(scheme) && local)) {
				return Optional.of(uri);
			}
			PretupidClient.LOGGER.warn("Ignoring insecure Pretupid API override {}", value);
		} catch (IllegalArgumentException exception) {
			PretupidClient.LOGGER.warn("Ignoring malformed Pretupid API override {}", value);
		}
		return Optional.empty();
	}

	public static URI realtimeUri() {
		String configured = System.getProperty("pretupid.realtime");
		if (configured == null || configured.isBlank()) {
			configured = System.getenv("PRETUPID_CLIENT_REALTIME");
		}
		if (configured != null && !configured.isBlank()) {
			try {
				URI uri = URI.create(configured.trim());
				String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
				boolean local = "localhost".equalsIgnoreCase(uri.getHost()) || "127.0.0.1".equals(uri.getHost());
				if ("wss".equals(scheme) || "ws".equals(scheme) && local) {
					return uri;
				}
			} catch (IllegalArgumentException exception) {
				PretupidClient.LOGGER.warn("Ignoring malformed realtime override {}", configured);
			}
		}
		return URI.create(DEFAULT_REALTIME);
	}

	public static String userAgent() {
		return "PretupidClient/" + shortVersion() + " (Minecraft; Fabric)";
	}
}

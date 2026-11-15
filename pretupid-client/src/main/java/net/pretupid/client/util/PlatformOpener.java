package net.pretupid.client.util;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import net.pretupid.client.PretupidClient;

public final class PlatformOpener {
	private PlatformOpener() {
	}

	public static void openFolder(Path path) {
		try {
			Files.createDirectories(path);
		} catch (IOException exception) {
			PretupidClient.LOGGER.warn("Could not create {}", path, exception);
		}
		launch(path.toAbsolutePath().toString());
	}

	public static void openUri(URI uri) {
		String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
		if (!scheme.equals("https") && !scheme.equals("http")) {
			return;
		}
		launch(uri.toString());
	}

	private static void launch(String target) {
		String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
		List<String> command;
		if (os.contains("win")) {
			command = List.of("explorer.exe", target);
		} else if (os.contains("mac")) {
			command = List.of("open", target);
		} else {
			command = List.of("xdg-open", target);
		}
		Thread thread = new Thread(() -> {
			try {
				new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
			} catch (IOException exception) {
				PretupidClient.LOGGER.warn("Could not open {}", target, exception);
			}
		}, "Pretupid Opener");
		thread.setDaemon(true);
		thread.start();
	}
}

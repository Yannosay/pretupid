package net.pretupid.client.update;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public final class UpdateHelper {
	private UpdateHelper() {
	}

	public static void main(String[] args) throws Exception {
		if (args.length != 4) {
			return;
		}
		long parentPid = Long.parseLong(args[0]);
		Path oldJar = Path.of(args[1]).toAbsolutePath().normalize();
		Path staged = Path.of(args[2]).toAbsolutePath().normalize();
		Path target = Path.of(args[3]).toAbsolutePath().normalize();
		if (!oldJar.getFileName().toString().endsWith(".jar") || !target.getFileName().toString().endsWith(".jar")
			|| !target.getParent().equals(oldJar.getParent()) || !Files.isRegularFile(staged)) {
			return;
		}
		Optional<ProcessHandle> parent = ProcessHandle.of(parentPid);
		if (parent.isPresent()) {
			parent.get().onExit().get(10, TimeUnit.MINUTES);
		}
		for (int attempt = 0; attempt < 60; attempt++) {
			try {
				if (!oldJar.equals(target)) {
					Files.deleteIfExists(oldJar);
				}
				Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING);
				Files.deleteIfExists(staged.getParent());
				return;
			} catch (IOException exception) {
				Thread.sleep(500L);
			}
		}
	}
}

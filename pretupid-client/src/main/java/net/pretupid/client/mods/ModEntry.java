package net.pretupid.client.mods;

import java.net.URI;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

public record ModEntry(
	String id,
	String name,
	String version,
	String description,
	String authors,
	Path file,
	boolean enabledOnDisk,
	boolean loaded,
	LockReason lock,
	int dependents,
	Supplier<Optional<byte[]>> icon,
	Optional<URI> homepage
) {
	public enum LockReason {
		NONE,
		CORE,
		REQUIRED,
		NOT_A_FILE
	}

	public boolean toggleable() {
		return lock == LockReason.NONE && file != null;
	}

	public boolean restartRequired() {
		return enabledOnDisk != loaded;
	}

	public boolean matches(String query) {
		if (query.isEmpty()) {
			return true;
		}
		String needle = query.toLowerCase(Locale.ROOT);
		return id.toLowerCase(Locale.ROOT).contains(needle)
			|| name.toLowerCase(Locale.ROOT).contains(needle)
			|| authors.toLowerCase(Locale.ROOT).contains(needle);
	}
}

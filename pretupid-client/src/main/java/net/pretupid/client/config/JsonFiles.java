package net.pretupid.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import net.pretupid.client.PretupidClient;

public final class JsonFiles {
	public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private JsonFiles() {
	}

	public static Optional<JsonObject> read(Path path) {
		if (!Files.isRegularFile(path)) {
			return Optional.empty();
		}
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			JsonElement element = JsonParser.parseReader(reader);
			return element.isJsonObject() ? Optional.of(element.getAsJsonObject()) : Optional.empty();
		} catch (IOException | JsonParseException exception) {
			PretupidClient.LOGGER.warn("Could not read {}, keeping a backup and using defaults", path, exception);
			backupCorrupt(path);
			return Optional.empty();
		}
	}

	public static void write(Path path, JsonObject object) throws IOException {
		Files.createDirectories(path.getParent());
		Path temp = path.resolveSibling(path.getFileName() + ".tmp");
		Files.writeString(temp, GSON.toJson(object), StandardCharsets.UTF_8);
		try {
			Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException exception) {
			Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private static void backupCorrupt(Path path) {
		try {
			Files.copy(path, path.resolveSibling(path.getFileName() + ".corrupt"), StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException ignored) {
			PretupidClient.LOGGER.debug("Backup of corrupt file {} failed", path);
		}
	}
}

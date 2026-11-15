package net.pretupid.client.mods;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.ModOrigin;
import net.fabricmc.loader.api.metadata.Person;
import net.pretupid.client.PretupidClient;

public final class ModCatalog {
	private static final String DISABLED_SUFFIX = ".disabled";
	private static final long MAX_ICON_BYTES = 1L << 20;
	private static final Set<String> CORE = Set.of(PretupidClient.MOD_ID, "fabricloader", "fabric-api", "minecraft", "java", "mixinextras");

	private final Path modsDirectory;
	private final Set<String> loadedIds = new HashSet<>();
	private List<ModEntry> entries = List.of();

	public ModCatalog() {
		this.modsDirectory = FabricLoader.getInstance().getGameDir().resolve("mods");
		for (ModContainer container : FabricLoader.getInstance().getAllMods()) {
			loadedIds.add(container.getMetadata().getId());
		}
	}

	public Path modsDirectory() {
		return modsDirectory;
	}

	public List<ModEntry> entries() {
		return entries;
	}

	public boolean restartRequired() {
		return entries.stream().anyMatch(ModEntry::restartRequired);
	}

	public void refresh() {
		Map<String, Integer> dependents = new HashMap<>();
		for (ModContainer container : FabricLoader.getInstance().getAllMods()) {
			for (ModDependency dependency : container.getMetadata().getDependencies()) {
				if (dependency.getKind() == ModDependency.Kind.DEPENDS) {
					dependents.merge(dependency.getModId(), 1, Integer::sum);
				}
			}
		}
		List<ModEntry> result = new ArrayList<>();
		Set<Path> seenFiles = new HashSet<>();
		for (ModContainer container : FabricLoader.getInstance().getAllMods()) {
			if (container.getContainingMod().isPresent()) {
				continue;
			}
			ModMetadata metadata = container.getMetadata();
			if ("builtin".equals(metadata.getType())) {
				continue;
			}
			Path file = modFile(container);
			boolean enabledOnDisk = true;
			if (file != null) {
				seenFiles.add(file);
				Path disabledVariant = file.resolveSibling(file.getFileName() + DISABLED_SUFFIX);
				if (!Files.exists(file) && Files.exists(disabledVariant)) {
					file = disabledVariant;
					enabledOnDisk = false;
				}
			}
			String id = metadata.getId();
			ModEntry.LockReason lock;
			if (CORE.contains(id)) {
				lock = ModEntry.LockReason.CORE;
			} else if (file == null) {
				lock = ModEntry.LockReason.NOT_A_FILE;
			} else if (dependents.getOrDefault(id, 0) > 0) {
				lock = ModEntry.LockReason.REQUIRED;
			} else {
				lock = ModEntry.LockReason.NONE;
			}
			result.add(new ModEntry(
				id,
				metadata.getName(),
				metadata.getVersion().getFriendlyString(),
				metadata.getDescription(),
				metadata.getAuthors().stream().map(Person::getName).collect(Collectors.joining(", ")),
				file,
				enabledOnDisk,
				true,
				lock,
				dependents.getOrDefault(id, 0),
				iconFromContainer(container),
				metadata.getContact().get("homepage").flatMap(ModCatalog::safeUri)
			));
		}
		if (Files.isDirectory(modsDirectory)) {
			try (DirectoryStream<Path> stream = Files.newDirectoryStream(modsDirectory)) {
				for (Path path : stream) {
					String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
					if (!fileName.endsWith(".jar" + DISABLED_SUFFIX) || seenFiles.contains(enabledPath(path).toAbsolutePath().normalize())) {
						continue;
					}
					readDisabled(path).ifPresent(result::add);
				}
			} catch (IOException exception) {
				PretupidClient.LOGGER.warn("Could not scan mods directory", exception);
			}
		}
		result.sort(Comparator.comparing((ModEntry entry) -> entry.lock() == ModEntry.LockReason.CORE)
			.thenComparing(entry -> entry.name().toLowerCase(Locale.ROOT)));
		entries = List.copyOf(result);
	}

	private Path modFile(ModContainer container) {
		ModOrigin origin = container.getOrigin();
		if (origin.getKind() != ModOrigin.Kind.PATH || origin.getPaths().size() != 1) {
			return null;
		}
		Path path = origin.getPaths().getFirst().toAbsolutePath().normalize();
		Path parent = path.getParent();
		if (parent == null || !parent.equals(modsDirectory.toAbsolutePath().normalize()) || !path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")) {
			return null;
		}
		return path;
	}

	private static Supplier<Optional<byte[]>> iconFromContainer(ModContainer container) {
		return () -> container.getMetadata().getIconPath(64).flatMap(container::findPath).flatMap(path -> {
			try {
				if (Files.size(path) > MAX_ICON_BYTES) {
					return Optional.empty();
				}
				return Optional.of(Files.readAllBytes(path));
			} catch (IOException exception) {
				return Optional.empty();
			}
		});
	}

	private Optional<ModEntry> readDisabled(Path path) {
		try (ZipFile zip = new ZipFile(path.toFile())) {
			ZipEntry manifest = zip.getEntry("fabric.mod.json");
			if (manifest == null || manifest.getSize() > MAX_ICON_BYTES) {
				return Optional.empty();
			}
			JsonObject json;
			try (InputStream input = zip.getInputStream(manifest)) {
				JsonElement element = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8));
				if (!element.isJsonObject()) {
					return Optional.empty();
				}
				json = element.getAsJsonObject();
			}
			String id = string(json, "id", path.getFileName().toString());
			String iconPath = iconPath(json);
			Supplier<Optional<byte[]>> icon = () -> readZipEntry(path, iconPath);
			Optional<URI> homepage = json.has("contact") && json.get("contact").isJsonObject()
				? Optional.ofNullable(string(json.getAsJsonObject("contact"), "homepage", null)).flatMap(ModCatalog::safeUri)
				: Optional.empty();
			return Optional.of(new ModEntry(
				id,
				string(json, "name", id),
				string(json, "version", "?"),
				string(json, "description", ""),
				authors(json),
				path,
				false,
				loadedIds.contains(id),
				CORE.contains(id) ? ModEntry.LockReason.CORE : ModEntry.LockReason.NONE,
				0,
				icon,
				homepage
			));
		} catch (IOException | RuntimeException exception) {
			PretupidClient.LOGGER.debug("Skipping unreadable disabled mod {}", path, exception);
			return Optional.empty();
		}
	}

	private static Optional<byte[]> readZipEntry(Path zipPath, String entryName) {
		if (entryName == null || entryName.isBlank()) {
			return Optional.empty();
		}
		try (ZipFile zip = new ZipFile(zipPath.toFile())) {
			ZipEntry entry = zip.getEntry(entryName.startsWith("/") ? entryName.substring(1) : entryName);
			if (entry == null || entry.getSize() > MAX_ICON_BYTES) {
				return Optional.empty();
			}
			try (InputStream input = zip.getInputStream(entry)) {
				return Optional.of(input.readNBytes((int) MAX_ICON_BYTES));
			}
		} catch (IOException exception) {
			return Optional.empty();
		}
	}

	private static String iconPath(JsonObject json) {
		JsonElement icon = json.get("icon");
		if (icon == null) {
			return null;
		}
		if (icon.isJsonPrimitive()) {
			return icon.getAsString();
		}
		if (icon.isJsonObject()) {
			String best = null;
			int bestSize = Integer.MAX_VALUE;
			for (Map.Entry<String, JsonElement> entry : icon.getAsJsonObject().entrySet()) {
				try {
					int size = Integer.parseInt(entry.getKey());
					if (Math.abs(size - 64) < bestSize && entry.getValue().isJsonPrimitive()) {
						bestSize = Math.abs(size - 64);
						best = entry.getValue().getAsString();
					}
				} catch (NumberFormatException ignored) {
					continue;
				}
			}
			return best;
		}
		return null;
	}

	private static String authors(JsonObject json) {
		JsonElement authors = json.get("authors");
		if (authors == null || !authors.isJsonArray()) {
			return "";
		}
		List<String> names = new ArrayList<>();
		for (JsonElement author : authors.getAsJsonArray()) {
			if (author.isJsonPrimitive()) {
				names.add(author.getAsString());
			} else if (author.isJsonObject()) {
				String name = string(author.getAsJsonObject(), "name", null);
				if (name != null) {
					names.add(name);
				}
			}
		}
		return String.join(", ", names);
	}

	private static String string(JsonObject json, String key, String fallback) {
		JsonElement element = json.get(key);
		return element != null && element.isJsonPrimitive() ? element.getAsString() : fallback;
	}

	private static Optional<URI> safeUri(String value) {
		try {
			URI uri = URI.create(value);
			String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
			return scheme.equals("https") || scheme.equals("http") ? Optional.of(uri) : Optional.empty();
		} catch (IllegalArgumentException exception) {
			return Optional.empty();
		}
	}

	private static Path enabledPath(Path disabled) {
		String name = disabled.getFileName().toString();
		return disabled.resolveSibling(name.substring(0, name.length() - DISABLED_SUFFIX.length()));
	}

	public boolean setEnabled(ModEntry entry, boolean enabled) {
		if (!entry.toggleable() || entry.enabledOnDisk() == enabled) {
			return false;
		}
		Path source = entry.file();
		if (!source.toAbsolutePath().normalize().getParent().equals(modsDirectory.toAbsolutePath().normalize())) {
			return false;
		}
		Path target = enabled ? enabledPath(source) : source.resolveSibling(source.getFileName() + DISABLED_SUFFIX);
		try {
			if (Files.exists(target)) {
				PretupidClient.LOGGER.warn("Refusing to overwrite {}", target);
				return false;
			}
			Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException exception) {
			try {
				Files.move(source, target);
			} catch (IOException fallback) {
				PretupidClient.LOGGER.error("Could not {} mod {}", enabled ? "enable" : "disable", entry.id(), fallback);
				return false;
			}
		}
		refreshEntry(entry, target, enabled);
		return true;
	}

	private void refreshEntry(ModEntry entry, Path newFile, boolean enabled) {
		List<ModEntry> updated = new ArrayList<>(entries.size());
		for (ModEntry existing : entries) {
			if (existing == entry) {
				updated.add(new ModEntry(existing.id(), existing.name(), existing.version(), existing.description(), existing.authors(), newFile, enabled, existing.loaded(), existing.lock(), existing.dependents(), existing.icon(), existing.homepage()));
			} else {
				updated.add(existing);
			}
		}
		entries = List.copyOf(updated);
	}
}

package net.pretupid.client.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.module.Module;
import net.pretupid.client.module.ModuleManager;
import net.pretupid.client.setting.SettingChangeBus;

public final class ConfigStore {
	public static final String DEFAULT_PROFILE = "default";
	private static final Pattern PROFILE_NAME = Pattern.compile("[a-z0-9_-]{1,32}");
	private static final long SAVE_DELAY_MS = 750L;
	private static final int FORMAT_VERSION = 1;

	private final Path clientFile;
	private final Path profilesDirectory;
	private final ClientSettings clientSettings;
	private final ModuleManager modules;
	private final Set<String> favorites = new LinkedHashSet<>();
	private final Map<String, List<String>> profileServers = new LinkedHashMap<>();
	private final ExecutorService io = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "Pretupid Config IO");
		thread.setDaemon(true);
		return thread;
	});
	private String activeProfile = DEFAULT_PROFILE;
	private String autoSwitchedFrom;
	private boolean loading;
	private boolean dirty;
	private long dirtySince;

	public ConfigStore(Path root, ClientSettings clientSettings, ModuleManager modules) {
		this.clientFile = root.resolve("client.json");
		this.profilesDirectory = root.resolve("profiles");
		this.clientSettings = clientSettings;
		this.modules = modules;
		SettingChangeBus.subscribe(setting -> markDirty());
	}

	public void load() {
		loading = true;
		try {
			JsonFiles.read(clientFile).ifPresent(this::readClient);
			if (!profileExists(activeProfile)) {
				activeProfile = DEFAULT_PROFILE;
			}
			applyProfile(activeProfile);
		} finally {
			loading = false;
		}
		dirty = false;
		if (!Files.isRegularFile(clientFile) || !profileExists(activeProfile)) {
			saveNow();
		}
	}

	private void readClient(JsonObject root) {
		if (root.has("settings") && root.get("settings").isJsonObject()) {
			clientSettings.load(root.getAsJsonObject("settings"));
		}
		favorites.clear();
		if (root.has("favorites") && root.get("favorites").isJsonArray()) {
			for (JsonElement element : root.getAsJsonArray("favorites")) {
				if (element.isJsonPrimitive()) {
					favorites.add(element.getAsString());
				}
			}
		}
		profileServers.clear();
		if (root.has("profileServers") && root.get("profileServers").isJsonObject()) {
			for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("profileServers").entrySet()) {
				if (!isValidProfileName(entry.getKey()) || !entry.getValue().isJsonArray()) {
					continue;
				}
				List<String> servers = new ArrayList<>();
				for (JsonElement server : entry.getValue().getAsJsonArray()) {
					if (server.isJsonPrimitive()) {
						servers.add(normalizeAddress(server.getAsString()));
					}
				}
				profileServers.put(entry.getKey(), servers);
			}
		}
		if (root.has("activeProfile") && root.get("activeProfile").isJsonPrimitive()) {
			String name = root.get("activeProfile").getAsString();
			if (isValidProfileName(name)) {
				activeProfile = name;
			}
		}
	}

	private void applyProfile(String name) {
		Optional<JsonObject> data = JsonFiles.read(profilePath(name));
		JsonObject moduleData = data.filter(root -> root.has("modules") && root.get("modules").isJsonObject())
			.map(root -> root.getAsJsonObject("modules"))
			.orElseGet(JsonObject::new);
		Module.withoutCallbacks(() -> {
			for (Module module : modules.all()) {
				module.loadDefaults();
				JsonElement element = moduleData.get(module.id());
				if (element != null && element.isJsonObject()) {
					try {
						module.load(element.getAsJsonObject());
					} catch (RuntimeException exception) {
						PretupidClient.LOGGER.warn("Ignoring invalid configuration for module {}", module.id(), exception);
					}
				}
			}
		});
	}

	public void markDirty() {
		if (loading) {
			return;
		}
		if (!dirty) {
			dirtySince = System.currentTimeMillis();
		}
		dirty = true;
	}

	public void tick() {
		if (dirty && System.currentTimeMillis() - dirtySince >= SAVE_DELAY_MS) {
			saveAsync();
		}
	}

	public void saveAsync() {
		Snapshot snapshot = snapshot();
		dirty = false;
		io.execute(() -> write(snapshot));
	}

	public void saveNow() {
		Snapshot snapshot = snapshot();
		dirty = false;
		write(snapshot);
	}

	public void shutdown() {
		saveNow();
		io.shutdown();
		try {
			if (!io.awaitTermination(2, TimeUnit.SECONDS)) {
				io.shutdownNow();
			}
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
		}
	}

	private Snapshot snapshot() {
		JsonObject client = new JsonObject();
		client.addProperty("formatVersion", FORMAT_VERSION);
		client.addProperty("activeProfile", activeProfile);
		client.add("settings", clientSettings.save());
		JsonArray favoriteArray = new JsonArray();
		favorites.forEach(favoriteArray::add);
		client.add("favorites", favoriteArray);
		JsonObject servers = new JsonObject();
		for (Map.Entry<String, List<String>> entry : profileServers.entrySet()) {
			JsonArray array = new JsonArray();
			entry.getValue().forEach(array::add);
			servers.add(entry.getKey(), array);
		}
		client.add("profileServers", servers);
		return new Snapshot(client, activeProfile, profileData());
	}

	private JsonObject profileData() {
		JsonObject profile = new JsonObject();
		profile.addProperty("formatVersion", FORMAT_VERSION);
		JsonObject moduleData = new JsonObject();
		for (Module module : modules.all()) {
			moduleData.add(module.id(), module.save());
		}
		profile.add("modules", moduleData);
		return profile;
	}

	private void write(Snapshot snapshot) {
		try {
			JsonFiles.write(clientFile, snapshot.client());
			JsonFiles.write(profilePath(snapshot.profile()), snapshot.profileData());
		} catch (IOException exception) {
			PretupidClient.LOGGER.error("Failed to save Pretupid Client configuration", exception);
		}
	}

	public String activeProfile() {
		return activeProfile;
	}

	public List<String> profiles() {
		List<String> names = new ArrayList<>();
		if (Files.isDirectory(profilesDirectory)) {
			try (DirectoryStream<Path> stream = Files.newDirectoryStream(profilesDirectory, "*.json")) {
				for (Path path : stream) {
					String fileName = path.getFileName().toString();
					String name = fileName.substring(0, fileName.length() - 5);
					if (isValidProfileName(name)) {
						names.add(name);
					}
				}
			} catch (IOException exception) {
				PretupidClient.LOGGER.warn("Could not list profiles", exception);
			}
		}
		if (!names.contains(activeProfile)) {
			names.add(activeProfile);
		}
		Collections.sort(names);
		names.remove(DEFAULT_PROFILE);
		names.addFirst(DEFAULT_PROFILE);
		return names;
	}

	public boolean switchProfile(String name) {
		if (!isValidProfileName(name) || name.equals(activeProfile)) {
			return false;
		}
		saveNow();
		loading = true;
		try {
			activeProfile = name;
			applyProfile(name);
		} finally {
			loading = false;
		}
		for (Module module : modules.all()) {
			try {
				module.onProfileApplied();
			} catch (RuntimeException exception) {
				PretupidClient.LOGGER.warn("Module {} failed to apply profile {}", module.id(), name, exception);
			}
		}
		saveNow();
		return true;
	}

	public Optional<String> createProfile(String requestedName) {
		String name = sanitizeProfileName(requestedName);
		if (name.isEmpty() || profileExists(name)) {
			return Optional.empty();
		}
		try {
			JsonFiles.write(profilePath(name), profileData());
		} catch (IOException exception) {
			PretupidClient.LOGGER.error("Could not create profile {}", name, exception);
			return Optional.empty();
		}
		switchProfile(name);
		return Optional.of(name);
	}

	public boolean deleteProfile(String name) {
		if (DEFAULT_PROFILE.equals(name) || !isValidProfileName(name)) {
			return false;
		}
		if (name.equals(activeProfile)) {
			switchProfile(DEFAULT_PROFILE);
		}
		profileServers.remove(name);
		try {
			Files.deleteIfExists(profilePath(name));
		} catch (IOException exception) {
			PretupidClient.LOGGER.error("Could not delete profile {}", name, exception);
			return false;
		}
		markDirty();
		return true;
	}

	public List<String> serversFor(String profile) {
		return Collections.unmodifiableList(profileServers.getOrDefault(profile, List.of()));
	}

	public void bindServer(String profile, String address) {
		String normalized = normalizeAddress(address);
		if (normalized.isEmpty()) {
			return;
		}
		for (List<String> servers : profileServers.values()) {
			servers.remove(normalized);
		}
		profileServers.computeIfAbsent(profile, key -> new ArrayList<>()).add(normalized);
		markDirty();
	}

	public void unbindServer(String profile, String address) {
		List<String> servers = profileServers.get(profile);
		if (servers != null && servers.remove(normalizeAddress(address))) {
			markDirty();
		}
	}

	public void onServerJoined(String address) {
		if (!clientSettings.autoProfileSwitch.enabled() || address == null) {
			return;
		}
		String normalized = normalizeAddress(address);
		for (Map.Entry<String, List<String>> entry : profileServers.entrySet()) {
			if (entry.getValue().contains(normalized) && profileExists(entry.getKey()) && !entry.getKey().equals(activeProfile)) {
				String previous = activeProfile;
				if (switchProfile(entry.getKey())) {
					autoSwitchedFrom = previous;
					PretupidClient.get().notifications().profileSwitched(entry.getKey());
				}
				return;
			}
		}
	}

	public void onServerLeft() {
		if (autoSwitchedFrom != null) {
			String target = autoSwitchedFrom;
			autoSwitchedFrom = null;
			if (profileExists(target)) {
				switchProfile(target);
			}
		}
	}

	public boolean isFavorite(Module module) {
		return favorites.contains(module.id());
	}

	public void toggleFavorite(Module module) {
		if (!favorites.remove(module.id())) {
			favorites.add(module.id());
		}
		markDirty();
	}

	public Path profilesDirectory() {
		return profilesDirectory;
	}

	private boolean profileExists(String name) {
		return DEFAULT_PROFILE.equals(name) || Files.isRegularFile(profilePath(name));
	}

	private Path profilePath(String name) {
		return profilesDirectory.resolve(name + ".json");
	}

	public static boolean isValidProfileName(String name) {
		return name != null && PROFILE_NAME.matcher(name).matches();
	}

	public static String sanitizeProfileName(String input) {
		if (input == null) {
			return "";
		}
		String lowered = input.trim().toLowerCase(Locale.ROOT).replace(' ', '-');
		StringBuilder builder = new StringBuilder();
		for (char c : lowered.toCharArray()) {
			if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '_') {
				builder.append(c);
			}
			if (builder.length() == 32) {
				break;
			}
		}
		return builder.toString();
	}

	public static String normalizeAddress(String address) {
		if (address == null) {
			return "";
		}
		String value = address.trim().toLowerCase(Locale.ROOT);
		if (value.endsWith(":25565")) {
			value = value.substring(0, value.length() - 6);
		}
		if (value.endsWith(".")) {
			value = value.substring(0, value.length() - 1);
		}
		return value;
	}

	private record Snapshot(JsonObject client, String profile, JsonObject profileData) {
	}
}

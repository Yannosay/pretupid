package net.pretupid.client.network;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.config.ConfigStore;
import net.pretupid.client.module.Module;
import net.pretupid.client.setting.TextSetting;
import net.pretupid.client.ui.Icon;
import net.pretupid.client.ui.Theme;

public final class ServerCompatibility {
	public record Rule(String pattern, Set<String> modules, String reason) {
		boolean matches(String address) {
			if (pattern.startsWith("*.")) {
				String suffix = pattern.substring(1);
				return address.endsWith(suffix) || address.equals(pattern.substring(2));
			}
			return address.equals(pattern) || address.endsWith("." + pattern);
		}
	}

	public static final Set<String> COMPETITIVE_MODULES = Set.of("zoom", "freelook", "freecam", "fullbright", "clear_weather", "low_fire", "no_hurt_cam", "reach", "combo");
	private static final Pattern KICK_PATTERN = Pattern.compile(
		"(disallowed|illegal|unfair|forbidden|blacklisted|prohibited|unallowed|not allowed)[^\\n]{0,40}(mod|client|modification|hack)|(hacked|modded) client",
		Pattern.CASE_INSENSITIVE);
	private static final long REFRESH_MS = 6L * 60L * 60L * 1000L;
	private static final int MAX_RESTRICTED = 64;
	private static final Icon ICON = Icon.item("barrier");

	private final ApiClient api;
	private final TextSetting restrictedServers;
	private List<Rule> remoteRules = List.of();
	private long nextRefresh;
	private boolean refreshing;
	private String currentAddress;
	private String lastAddress;
	private final List<Module> paused = new ArrayList<>();

	public ServerCompatibility(ApiClient api, TextSetting restrictedServers) {
		this.api = api;
		this.restrictedServers = restrictedServers;
	}

	public void tick() {
		if (!refreshing && System.currentTimeMillis() >= nextRefresh) {
			refresh();
		}
	}

	private void refresh() {
		refreshing = true;
		nextRefresh = System.currentTimeMillis() + REFRESH_MS;
		api.json("GET", "/server-rules", null, null).whenComplete((response, failure) -> Minecraft.getInstance().execute(() -> {
			refreshing = false;
			if (failure != null) {
				nextRefresh = System.currentTimeMillis() + 10L * 60L * 1000L;
				return;
			}
			List<Rule> parsed = new ArrayList<>();
			for (JsonElement element : Json.array(response, "rules")) {
				if (!element.isJsonObject() || parsed.size() >= 500) {
					continue;
				}
				JsonObject object = element.getAsJsonObject();
				String pattern = Json.string(object, "pattern", 253);
				if (pattern == null || pattern.isBlank()) {
					continue;
				}
				Set<String> modules = new LinkedHashSet<>();
				for (JsonElement module : Json.array(object, "modules")) {
					if (module.isJsonPrimitive() && modules.size() < 64) {
						modules.add(module.getAsString().toLowerCase(Locale.ROOT));
					}
				}
				String reason = Json.string(object, "reason", 160);
				parsed.add(new Rule(ConfigStore.normalizeAddress(pattern), Collections.unmodifiableSet(modules), reason == null ? "" : reason));
			}
			remoteRules = Collections.unmodifiableList(parsed);
		}));
	}

	public void onJoined(String address) {
		resumeAll();
		currentAddress = address == null ? null : ConfigStore.normalizeAddress(address);
		if (currentAddress == null || currentAddress.isEmpty()) {
			return;
		}
		Set<String> blocked = new LinkedHashSet<>();
		String reason = null;
		for (Rule rule : remoteRules) {
			if (rule.matches(currentAddress)) {
				blocked.addAll(rule.modules());
				if (reason == null && !rule.reason().isBlank()) {
					reason = rule.reason();
				}
			}
		}
		if (isRestricted(currentAddress)) {
			blocked.addAll(COMPETITIVE_MODULES);
		}
		apply(blocked, reason == null ? Component.translatable("pretupid.compat.reason_server") : Component.literal(reason));
	}

	public void onServerRules(Set<String> modules) {
		if (currentAddress == null) {
			return;
		}
		apply(modules, Component.translatable("pretupid.compat.reason_server"));
	}

	private void apply(Set<String> blocked, Component reason) {
		if (blocked.isEmpty()) {
			return;
		}
		PretupidClient client = PretupidClient.get();
		List<Component> names = new ArrayList<>();
		for (String id : blocked) {
			Module module = id.equals("*") ? null : client.modules().byId(id);
			if (module == null || module.isPaused()) {
				continue;
			}
			boolean wasActive = module.isEnabled();
			module.pause(reason);
			paused.add(module);
			if (wasActive) {
				names.add(module.name());
			}
		}
		if (!names.isEmpty()) {
			Component list = Component.literal(String.join(", ", names.stream().map(Component::getString).toList()));
			client.notifications().push(Component.translatable("pretupid.compat.title"), Component.translatable("pretupid.compat.paused", list), ICON, null, Theme.WARNING, 6000L);
		}
	}

	public void onDisconnected() {
		resumeAll();
		if (currentAddress != null) {
			lastAddress = currentAddress;
		}
		currentAddress = null;
	}

	public void onDisconnectMessage(String text) {
		String address = lastAddress;
		lastAddress = null;
		if (address == null || text == null || !KICK_PATTERN.matcher(text).find() || isRestricted(address)) {
			return;
		}
		markRestricted(address);
		PretupidClient.LOGGER.info("Server {} rejected modded clients; competitive modules will pause there next time", address);
		PretupidClient.get().notifications().push(Component.translatable("pretupid.compat.title"), Component.translatable("pretupid.compat.restricted", address), ICON, null, Theme.WARNING, 8000L);
	}

	private void resumeAll() {
		for (Module module : paused) {
			module.resume();
		}
		paused.clear();
	}

	public boolean isRestricted(String address) {
		String normalized = ConfigStore.normalizeAddress(address);
		for (String entry : restrictedServers.get().split(",")) {
			if (entry.strip().equals(normalized)) {
				return true;
			}
		}
		return false;
	}

	private void markRestricted(String address) {
		List<String> entries = new ArrayList<>();
		for (String entry : restrictedServers.get().split(",")) {
			if (!entry.isBlank()) {
				entries.add(entry.strip());
			}
		}
		entries.add(address);
		while (entries.size() > MAX_RESTRICTED || String.join(",", entries).length() > restrictedServers.maxLength()) {
			entries.removeFirst();
		}
		restrictedServers.set(String.join(",", entries));
	}

	public void clearRestricted() {
		restrictedServers.set("");
	}
}

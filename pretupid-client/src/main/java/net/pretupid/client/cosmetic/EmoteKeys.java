package net.pretupid.client.cosmetic;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.setting.KeySetting;
import net.pretupid.client.setting.SettingChangeBus;
import net.pretupid.client.setting.TextSetting;

public final class EmoteKeys {
	private static final int MAX_BINDINGS = 32;

	private final TextSetting storage;
	private final Map<String, KeySetting> bindings = new LinkedHashMap<>();
	private final Map<String, Boolean> held = new HashMap<>();
	private boolean loading;

	public EmoteKeys(TextSetting storage) {
		this.storage = storage;
		SettingChangeBus.subscribe(setting -> {
			if (loading) {
				return;
			}
			if (setting == storage) {
				load();
			} else if (setting instanceof KeySetting key && bindings.containsValue(key)) {
				save();
			}
		});
		load();
	}

	public KeySetting binding(String emoteId) {
		return bindings.computeIfAbsent(emoteId, id -> {
			KeySetting setting = new KeySetting("emote_key." + id, KeySetting.UNBOUND);
			setting.bindTranslation("pretupid.emote_key");
			return setting;
		});
	}

	private void load() {
		loading = true;
		try {
			for (KeySetting setting : bindings.values()) {
				setting.set(KeySetting.UNBOUND);
			}
			for (String entry : storage.get().split(";")) {
				int separator = entry.lastIndexOf('=');
				if (separator <= 0) {
					continue;
				}
				String id = entry.substring(0, separator).trim();
				try {
					int key = Integer.parseInt(entry.substring(separator + 1).trim());
					if (!id.isEmpty() && key != KeySetting.UNBOUND) {
						binding(id).set(key);
					}
				} catch (NumberFormatException ignored) {
					PretupidClient.LOGGER.debug("Ignoring malformed emote binding {}", entry);
				}
			}
		} finally {
			loading = false;
		}
	}

	private void save() {
		StringBuilder builder = new StringBuilder();
		int count = 0;
		for (Map.Entry<String, KeySetting> entry : bindings.entrySet()) {
			if (!entry.getValue().isBound() || count >= MAX_BINDINGS) {
				continue;
			}
			String candidate = entry.getKey() + "=" + entry.getValue().get();
			if (builder.length() + candidate.length() + 1 > storage.maxLength()) {
				break;
			}
			if (!builder.isEmpty()) {
				builder.append(';');
			}
			builder.append(candidate);
			count++;
		}
		loading = true;
		try {
			storage.set(builder.toString());
		} finally {
			loading = false;
		}
	}

	public void tick(Minecraft minecraft) {
		boolean inGame = minecraft.gui.screen() == null && minecraft.player != null;
		PretupidClient client = PretupidClient.get();
		for (Map.Entry<String, KeySetting> entry : bindings.entrySet()) {
			KeySetting key = entry.getValue();
			if (!key.isBound()) {
				held.remove(entry.getKey());
				continue;
			}
			boolean down = inGame && InputConstants.isKeyDown(key.get());
			boolean wasDown = held.getOrDefault(entry.getKey(), false);
			if (down && !wasDown) {
				client.cosmetics().catalog().emote(entry.getKey()).ifPresent(emote -> client.emotes().playLocal(emote));
			}
			held.put(entry.getKey(), down);
		}
	}
}

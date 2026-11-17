package net.pretupid.client.cosmetic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.pretupid.client.setting.TextSetting;

public final class EmoteWheel {
	public static final int SIZE = 6;
	private static final String SEPARATOR = ",";

	private final TextSetting storage;

	public EmoteWheel(TextSetting storage) {
		this.storage = storage;
	}

	public String[] slotIds() {
		String[] slots = new String[SIZE];
		Arrays.fill(slots, "");
		String raw = storage.get();
		if (raw.isBlank()) {
			return slots;
		}
		String[] parts = raw.split(SEPARATOR, -1);
		List<String> seen = new ArrayList<>();
		for (int i = 0; i < Math.min(SIZE, parts.length); i++) {
			String id = parts[i].trim();
			if (!id.isEmpty() && !seen.contains(id)) {
				slots[i] = id;
				seen.add(id);
			}
		}
		return slots;
	}

	public boolean ensureDefaults(Catalog catalog) {
		if (!storage.get().isBlank() || catalog.emotes().isEmpty()) {
			return false;
		}
		String[] slots = new String[SIZE];
		Arrays.fill(slots, "");
		List<Catalog.EmoteEntry> emotes = catalog.emotes();
		for (int i = 0; i < Math.min(SIZE, emotes.size()); i++) {
			slots[i] = emotes.get(i).id();
		}
		write(slots);
		return true;
	}

	public List<Optional<Catalog.EmoteEntry>> slots(Catalog catalog) {
		ensureDefaults(catalog);
		List<Optional<Catalog.EmoteEntry>> result = new ArrayList<>(SIZE);
		for (String id : slotIds()) {
			result.add(id.isEmpty() ? Optional.empty() : catalog.emote(id));
		}
		return result;
	}

	public List<Catalog.EmoteEntry> filled(Catalog catalog) {
		List<Catalog.EmoteEntry> result = new ArrayList<>();
		for (Optional<Catalog.EmoteEntry> slot : slots(catalog)) {
			slot.ifPresent(result::add);
		}
		return result;
	}

	public int slotOf(String emoteId) {
		String[] slots = slotIds();
		for (int i = 0; i < SIZE; i++) {
			if (slots[i].equals(emoteId)) {
				return i;
			}
		}
		return -1;
	}

	public void assign(int slot, String emoteId) {
		if (slot < 0 || slot >= SIZE || emoteId == null || emoteId.isBlank()) {
			return;
		}
		String[] slots = slotIds();
		int existing = slotOf(emoteId);
		if (existing == slot) {
			return;
		}
		if (existing >= 0) {
			slots[existing] = slots[slot];
		}
		slots[slot] = emoteId;
		write(slots);
	}

	public void swap(int first, int second) {
		if (first < 0 || second < 0 || first >= SIZE || second >= SIZE || first == second) {
			return;
		}
		String[] slots = slotIds();
		String held = slots[first];
		slots[first] = slots[second];
		slots[second] = held;
		write(slots);
	}

	public void clear(int slot) {
		if (slot < 0 || slot >= SIZE) {
			return;
		}
		String[] slots = slotIds();
		slots[slot] = "";
		write(slots);
	}

	public int firstFreeSlot() {
		String[] slots = slotIds();
		for (int i = 0; i < SIZE; i++) {
			if (slots[i].isEmpty()) {
				return i;
			}
		}
		return -1;
	}

	private void write(String[] slots) {
		storage.set(String.join(SEPARATOR, slots));
	}
}

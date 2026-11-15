package net.pretupid.client.module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.pretupid.client.PretupidClient;

public final class ModuleManager {
	private final List<Module> modules = new ArrayList<>();
	private final List<Module> view = Collections.unmodifiableList(modules);
	private final Map<String, Module> byId = new HashMap<>();
	private final Map<Class<? extends Module>, Module> byType = new HashMap<>();

	public <M extends Module> M register(M module) {
		if (byId.putIfAbsent(module.id(), module) != null) {
			throw new IllegalStateException("Duplicate module id " + module.id());
		}
		modules.add(module);
		byType.put(module.getClass(), module);
		return module;
	}

	public List<Module> all() {
		return view;
	}

	public Module byId(String id) {
		return byId.get(id);
	}

	public <M extends Module> M get(Class<M> type) {
		Module module = byType.get(type);
		if (module == null) {
			throw new IllegalArgumentException("Module not registered: " + type.getName());
		}
		return type.cast(module);
	}

	public List<Module> inCategory(Category category) {
		List<Module> result = new ArrayList<>();
		for (Module module : modules) {
			if (module.category() == category) {
				result.add(module);
			}
		}
		return result;
	}

	public long enabledCount() {
		return modules.stream().filter(Module::isEnabled).count();
	}

	public void tick(Minecraft minecraft) {
		for (Module module : modules) {
			if (!module.isEnabled()) {
				continue;
			}
			try {
				module.onTick(minecraft);
			} catch (RuntimeException exception) {
				PretupidClient.LOGGER.error("Module {} failed during tick and was disabled", module.id(), exception);
				module.setEnabled(false);
			}
		}
	}

	public boolean handleToggleKey(int key) {
		boolean handled = false;
		for (Module module : modules) {
			if (module.toggleKey().matches(key)) {
				module.toggle();
				PretupidClient.get().notifications().moduleToggled(module);
				handled = true;
			}
		}
		return handled;
	}
}

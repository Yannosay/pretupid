package net.pretupid.client.setting;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class SettingChangeBus {
	private static final List<Consumer<Setting<?>>> SUBSCRIBERS = new CopyOnWriteArrayList<>();

	private SettingChangeBus() {
	}

	public static void subscribe(Consumer<Setting<?>> subscriber) {
		SUBSCRIBERS.add(subscriber);
	}

	public static void fire(Setting<?> setting) {
		for (Consumer<Setting<?>> subscriber : SUBSCRIBERS) {
			subscriber.accept(setting);
		}
	}
}

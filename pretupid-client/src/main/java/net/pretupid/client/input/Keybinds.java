package net.pretupid.client.input;

import net.pretupid.client.platform.Platform;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.module.Module;
import net.pretupid.client.module.visual.CinematicModule;
import net.pretupid.client.module.visual.FreecamModule;
import net.pretupid.client.module.visual.FreelookModule;
import net.pretupid.client.setting.KeySetting;
import net.pretupid.client.setting.SettingChangeBus;
import net.pretupid.client.ui.screen.ClientMenuScreen;
import net.pretupid.client.ui.screen.EmoteWheelScreen;
import net.pretupid.client.ui.screen.HudEditorScreen;
import net.pretupid.client.ui.screen.QuickMenuScreen;
import net.pretupid.client.ui.screen.WaypointsScreen;

public final class Keybinds {
	private final PretupidClient client;
	private final Map<Module, Boolean> held = new HashMap<>();
	private KeyMapping openMenu;
	private KeyMapping openHudEditor;
	private boolean syncingHudEditorKey;
	private KeyMapping zoom;
	private KeyMapping emoteWheel;
	private KeyMapping freelook;
	private KeyMapping freecam;
	private KeyMapping waypoints;
	private KeyMapping music;
	private KeyMapping screenie;
	private KeyMapping meme;
	private KeyMapping cinematicEditor;
	private KeyMapping cinematicKeyframe;
	private KeyMapping quickMenu;

	public Keybinds(PretupidClient client) {
		this.client = client;
	}

	public void register() {
		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(PretupidClient.MOD_ID, "main"));
		openMenu = Platform.registerKey(new KeyMapping("key.pretupid.menu", Platform.keyboard(), InputConstants.KEY_RSHIFT, category));
		openHudEditor = Platform.registerKey(new KeyMapping("key.pretupid.hud_editor", Platform.keyboard(), InputConstants.KEY_H, category));
		SettingChangeBus.subscribe(setting -> {
			if (setting == client.settings().hudEditorKey && !syncingHudEditorKey) {
				int key = client.settings().hudEditorKey.get();
				openHudEditor.setKey(key == KeySetting.UNBOUND ? InputConstants.UNKNOWN : Platform.keyboard().getOrCreate(key));
				KeyMapping.resetMapping();
				Minecraft.getInstance().options.save();
			}
		});
		zoom = Platform.registerKey(new KeyMapping("key.pretupid.zoom", Platform.keyboard(), InputConstants.KEY_C, category));
		emoteWheel = Platform.registerKey(new KeyMapping("key.pretupid.emote_wheel", Platform.keyboard(), InputConstants.KEY_B, category));
		quickMenu = Platform.registerKey(new KeyMapping("key.pretupid.quick_menu", Platform.keyboard(), InputConstants.KEY_G, category));
		freelook = Platform.registerKey(new KeyMapping("key.pretupid.freelook", Platform.keyboard(), InputConstants.KEY_LALT, category));
		freecam = Platform.registerKey(new KeyMapping("key.pretupid.freecam", Platform.keyboard(), InputConstants.KEY_F6, category));
		screenie = Platform.registerKey(new KeyMapping("key.pretupid.screenie", Platform.keyboard(), InputConstants.KEY_F4, category));
		meme = Platform.registerKey(new KeyMapping("key.pretupid.meme", Platform.keyboard(), InputConstants.KEY_F7, category));
		waypoints = Platform.registerKey(new KeyMapping("key.pretupid.waypoints", Platform.keyboard(), InputConstants.KEY_N, category));
		music = Platform.registerKey(new KeyMapping("key.pretupid.music", Platform.keyboard(), InputConstants.KEY_M, category));
		cinematicEditor = Platform.registerKey(new KeyMapping("key.pretupid.cinematic_editor", Platform.keyboard(), InputConstants.KEY_J, category));
		cinematicKeyframe = Platform.registerKey(new KeyMapping("key.pretupid.cinematic_keyframe", Platform.keyboard(), InputConstants.KEY_K, category));
	}

	public KeyMapping emoteWheel() {
		return emoteWheel;
	}

	public KeyMapping freecam() {
		return freecam;
	}

	public KeyMapping cinematicKeyframe() {
		return cinematicKeyframe;
	}

	public KeyMapping quickMenu() {
		return quickMenu;
	}

	public KeyMapping zoom() {
		return zoom;
	}

	public KeyMapping openMenu() {
		return openMenu;
	}

	private void syncHudEditorKey() {
		InputConstants.Key bound = Platform.boundKey(openHudEditor);
		int key = bound.getType() == Platform.keyboard() && bound != InputConstants.UNKNOWN ? bound.getValue() : KeySetting.UNBOUND;
		KeySetting setting = client.settings().hudEditorKey;
		if (setting.get() != key) {
			syncingHudEditorKey = true;
			try {
				setting.set(key);
			} finally {
				syncingHudEditorKey = false;
			}
		}
	}

	public void tick(Minecraft minecraft) {
		while (openMenu.consumeClick()) {
			if (Platform.screen() == null) {
				Platform.setScreen(new ClientMenuScreen(null));
			}
		}
		while (emoteWheel.consumeClick()) {
			if (Platform.screen() == null && minecraft.player != null) {
				Platform.setScreen(new EmoteWheelScreen());
			}
		}
		while (quickMenu.consumeClick()) {
			if (Platform.screen() == null && minecraft.player != null) {
				Platform.setScreen(new QuickMenuScreen());
			}
		}
		FreelookModule freelookModule = client.modules().get(FreelookModule.class);
		if (freelookModule.holdMode()) {
			boolean pressed = freelook.isDown();
			while (freelook.consumeClick()) {
				pressed = true;
			}
			freelookModule.setHeld(minecraft, freelookModule.isEnabled() && pressed && Platform.screen() == null);
		} else {
			while (freelook.consumeClick()) {
				if (Platform.screen() == null) {
					freelookModule.toggleActive(minecraft);
				}
			}
		}
		FreecamModule freecamModule = client.modules().get(FreecamModule.class);
		if (freecamModule.holdMode()) {
			boolean pressed = freecam.isDown();
			while (freecam.consumeClick()) {
				pressed = true;
			}
			freecamModule.setHeld(minecraft, freecamModule.isEnabled() && pressed && Platform.screen() == null);
		} else {
			while (freecam.consumeClick()) {
				if (Platform.screen() == null) {
					if (!freecamModule.isEnabled() && !freecamModule.active()) {
						freecamModule.explainUnavailable(minecraft);
					} else {
						freecamModule.toggleActive(minecraft);
					}
				}
			}
		}
		while (screenie.consumeClick()) {
			var module = client.modules().get(net.pretupid.client.module.utility.ScreeniesModule.class);
			if (module.isEnabled() && minecraft.level != null && !(Platform.screen() instanceof net.pretupid.client.ui.screen.ScreenieScreen)) {
				client.screenies().request();
			}
		}
		while (meme.consumeClick()) {
			var module = client.modules().get(net.pretupid.client.module.utility.ScreeniesModule.class);
			if (module.isEnabled() && minecraft.level != null && !(Platform.screen() instanceof net.pretupid.client.ui.screen.ScreenieScreen)) {
				client.screenies().requestMeme();
			}
		}
		while (waypoints.consumeClick()) {
			if (Platform.screen() == null && minecraft.level != null) {
				Platform.setScreen(new WaypointsScreen(null));
			}
		}
		while (music.consumeClick()) {
			if (Platform.screen() == null) {
				Platform.setScreen(new net.pretupid.client.ui.screen.MusicScreen(null));
			}
		}
		CinematicModule cinematicModule = client.modules().get(CinematicModule.class);
		while (cinematicEditor.consumeClick()) {
			if (Platform.screen() == null) {
				cinematicModule.openEditor(minecraft);
			}
		}
		while (cinematicKeyframe.consumeClick()) {
			if (Platform.screen() == null) {
				cinematicModule.addKeyframe(minecraft);
			}
		}
		syncHudEditorKey();
		while (openHudEditor.consumeClick()) {
			if (Platform.screen() == null && minecraft.level != null) {
				Platform.setScreen(new HudEditorScreen(null));
			}
		}
		boolean inGame = Platform.screen() == null && minecraft.level != null;
		for (Module module : client.modules().all()) {
			if (!module.toggleKey().isBound()) {
				held.remove(module);
				continue;
			}
			boolean down = inGame && Platform.isKeyDown(module.toggleKey().get());
			boolean wasDown = held.getOrDefault(module, false);
			if (down && !wasDown) {
				module.toggle();
				client.notifications().moduleToggled(module);
			}
			held.put(module, down);
		}
	}
}

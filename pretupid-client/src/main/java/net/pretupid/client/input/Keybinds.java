package net.pretupid.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.module.Module;
import net.pretupid.client.module.visual.FreelookModule;
import net.pretupid.client.setting.KeySetting;
import net.pretupid.client.setting.SettingChangeBus;
import net.pretupid.client.ui.screen.ClientMenuScreen;
import net.pretupid.client.ui.screen.EmoteWheelScreen;
import net.pretupid.client.ui.screen.HudEditorScreen;
import net.pretupid.client.ui.screen.QuickMenuScreen;

public final class Keybinds {
	private final PretupidClient client;
	private final Map<Module, Boolean> held = new HashMap<>();
	private KeyMapping openMenu;
	private KeyMapping openHudEditor;
	private boolean syncingHudEditorKey;
	private KeyMapping zoom;
	private KeyMapping emoteWheel;
	private KeyMapping freelook;
	private KeyMapping quickMenu;

	public Keybinds(PretupidClient client) {
		this.client = client;
	}

	public void register() {
		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(PretupidClient.MOD_ID, "main"));
		openMenu = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.pretupid.menu", InputConstants.Type.KEYBOARD, InputConstants.KEY_RSHIFT, category));
		openHudEditor = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.pretupid.hud_editor", InputConstants.Type.KEYBOARD, InputConstants.KEY_H, category));
		SettingChangeBus.subscribe(setting -> {
			if (setting == client.settings().hudEditorKey && !syncingHudEditorKey) {
				int key = client.settings().hudEditorKey.get();
				openHudEditor.setKey(key == KeySetting.UNBOUND ? InputConstants.UNKNOWN : InputConstants.Type.KEYBOARD.getOrCreate(key));
				KeyMapping.resetMapping();
				Minecraft.getInstance().options.save();
			}
		});
		zoom = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.pretupid.zoom", InputConstants.Type.KEYBOARD, InputConstants.KEY_C, category));
		emoteWheel = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.pretupid.emote_wheel", InputConstants.Type.KEYBOARD, InputConstants.KEY_B, category));
		quickMenu = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.pretupid.quick_menu", InputConstants.Type.KEYBOARD, InputConstants.KEY_G, category));
		freelook = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.pretupid.freelook", InputConstants.Type.KEYBOARD, InputConstants.KEY_LALT, category));
	}

	public KeyMapping emoteWheel() {
		return emoteWheel;
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
		InputConstants.Key bound = KeyMappingHelper.getBoundKeyOf(openHudEditor);
		int key = bound.getType() == InputConstants.Type.KEYBOARD && bound != InputConstants.UNKNOWN ? bound.getValue() : KeySetting.UNBOUND;
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
			if (minecraft.gui.screen() == null) {
				minecraft.gui.setScreen(new ClientMenuScreen(null));
			}
		}
		while (emoteWheel.consumeClick()) {
			if (minecraft.gui.screen() == null && minecraft.player != null) {
				minecraft.gui.setScreen(new EmoteWheelScreen());
			}
		}
		while (quickMenu.consumeClick()) {
			if (minecraft.gui.screen() == null && minecraft.player != null) {
				minecraft.gui.setScreen(new QuickMenuScreen());
			}
		}
		FreelookModule freelookModule = client.modules().get(FreelookModule.class);
		if (freelookModule.holdMode()) {
			boolean pressed = freelook.isDown();
			while (freelook.consumeClick()) {
				pressed = true;
			}
			freelookModule.setHeld(minecraft, freelookModule.isEnabled() && pressed && minecraft.gui.screen() == null);
		} else {
			while (freelook.consumeClick()) {
				if (minecraft.gui.screen() == null) {
					freelookModule.toggleActive(minecraft);
				}
			}
		}
		syncHudEditorKey();
		while (openHudEditor.consumeClick()) {
			if (minecraft.gui.screen() == null && minecraft.level != null) {
				minecraft.gui.setScreen(new HudEditorScreen(null));
			}
		}
		boolean inGame = minecraft.gui.screen() == null && minecraft.level != null;
		for (Module module : client.modules().all()) {
			if (!module.toggleKey().isBound()) {
				held.remove(module);
				continue;
			}
			boolean down = inGame && InputConstants.isKeyDown(module.toggleKey().get());
			boolean wasDown = held.getOrDefault(module, false);
			if (down && !wasDown) {
				module.toggle();
				client.notifications().moduleToggled(module);
			}
			held.put(module, down);
		}
	}
}

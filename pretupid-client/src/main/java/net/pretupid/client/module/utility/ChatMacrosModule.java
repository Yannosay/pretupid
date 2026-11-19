package net.pretupid.client.module.utility;

import net.pretupid.client.platform.Platform;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.pretupid.client.module.Category;
import net.pretupid.client.module.Module;
import net.pretupid.client.setting.KeySetting;
import net.pretupid.client.setting.TextSetting;

public final class ChatMacrosModule extends Module {
	private static final int SLOTS = 4;
	private static final long COOLDOWN_MS = 1000L;

	private final KeySetting[] keys = new KeySetting[SLOTS];
	private final TextSetting[] messages = new TextSetting[SLOTS];
	private final boolean[] held = new boolean[SLOTS];
	private final long[] lastSent = new long[SLOTS];

	public ChatMacrosModule() {
		super("chat_macros", Category.UTILITY, "oak_sign", false);
		for (int i = 0; i < SLOTS; i++) {
			keys[i] = add(new KeySetting("key_" + (i + 1), KeySetting.UNBOUND));
			messages[i] = add(new TextSetting("message_" + (i + 1), i == 0 ? "gg" : "", 256));
		}
	}

	@Override
	public void onTick(Minecraft minecraft) {
		boolean canSend = Platform.screen() == null && minecraft.player != null;
		long now = System.currentTimeMillis();
		for (int i = 0; i < SLOTS; i++) {
			boolean down = keys[i].isBound() && Platform.isKeyDown(keys[i].get());
			if (down && !held[i] && canSend && now - lastSent[i] >= COOLDOWN_MS) {
				send(minecraft, messages[i].get().trim());
				lastSent[i] = now;
			}
			held[i] = down;
		}
	}

	private static void send(Minecraft minecraft, String text) {
		ClientPacketListener connection = minecraft.getConnection();
		if (connection == null || text.isEmpty()) {
			return;
		}
		if (text.startsWith("/")) {
			String command = text.substring(1).trim();
			if (!command.isEmpty()) {
				connection.sendCommand(command);
			}
		} else {
			connection.sendChat(text);
		}
	}
}

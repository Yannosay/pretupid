package net.pretupid.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.emoji.ChatEmojis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
	private static final java.util.Set<String> MESSAGE_COMMANDS = java.util.Set.of("msg", "tell", "w", "whisper", "m", "r", "reply", "me", "say", "teammsg", "tm",
		"party", "p", "pc", "gc", "ac", "mail");

	@ModifyVariable(method = "sendChat", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private String pretupid$chatEmojis(String message) {
		PretupidClient client = PretupidClient.getOrNull();
		if (client == null || message == null || !client.settings().chatEmojis.enabled() || message.indexOf(':') < 0) {
			return message;
		}
		try {
			ChatEmojis.Outgoing outgoing = client.chatEmojis().prepare(message, Minecraft.getInstance().getUser().getProfileId());
			if (outgoing.overlay() != null) {
				client.chatEmojis().sent(outgoing.overlay());
			}
			return outgoing.message();
		} catch (RuntimeException exception) {
			PretupidClient.LOGGER.debug("Could not prepare emoji for a chat message", exception);
			return message;
		}
	}

	@ModifyVariable(method = "sendCommand", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private String pretupid$commandEmojis(String command) {
		PretupidClient client = PretupidClient.getOrNull();
		if (client == null || command == null || !client.settings().chatEmojis.enabled() || command.indexOf(':') < 0) {
			return command;
		}
		int space = command.indexOf(' ');
		String name = (space < 0 ? command : command.substring(0, space)).toLowerCase(java.util.Locale.ROOT);
		if (!MESSAGE_COMMANDS.contains(name.substring(name.indexOf(':') + 1))) {
			return command;
		}
		String converted = ChatEmojis.shortcodesToUnicode(command);
		return converted.length() <= 256 ? converted : command;
	}
}

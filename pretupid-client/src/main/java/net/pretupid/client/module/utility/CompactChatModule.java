package net.pretupid.client.module.utility;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import net.pretupid.client.module.Category;
import net.pretupid.client.module.Module;

public final class CompactChatModule extends Module {
	private String lastText;
	private GuiMessage lastMessage;
	private int repeats;

	public CompactChatModule() {
		super("compact_chat", Category.UTILITY, "writable_book", false);
	}

	public Component compact(Component message, List<GuiMessage> messages, Runnable refresh) {
		String text = message.getString();
		boolean repeated = text.equals(lastText) && lastMessage != null && !messages.isEmpty() && messages.getFirst() == lastMessage;
		if (!repeated) {
			lastText = text;
			repeats = 1;
			return message;
		}
		messages.removeFirst();
		refresh.run();
		repeats++;
		return message.copy().append(Component.literal(" (" + repeats + "×)").withStyle(ChatFormatting.GRAY));
	}

	public void remember(List<GuiMessage> messages) {
		lastMessage = messages.isEmpty() ? null : messages.getFirst();
	}

	@Override
	protected void onDisable() {
		lastText = null;
		lastMessage = null;
		repeats = 0;
	}
}

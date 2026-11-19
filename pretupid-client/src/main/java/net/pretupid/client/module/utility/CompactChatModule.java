package net.pretupid.client.module.utility;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.pretupid.client.module.Category;
import net.pretupid.client.module.Module;
import net.pretupid.client.ui.ChatHistory;

public final class CompactChatModule extends Module {
	private String lastText;
	private Component lastMessage;
	private int repeats;

	public CompactChatModule() {
		super("compact_chat", Category.UTILITY, "writable_book", false);
	}

	public Component compact(Component message, ChatHistory chat) {
		String text = message.getString();
		boolean repeated = text.equals(lastText) && lastMessage != null && chat.size() > 0 && chat.content(0) == lastMessage;
		if (!repeated) {
			lastText = text;
			repeats = 1;
			return message;
		}
		chat.removeNewest();
		chat.refresh();
		repeats++;
		return message.copy().append(Component.literal(" (" + repeats + "×)").withStyle(ChatFormatting.GRAY));
	}

	public void remember(ChatHistory chat) {
		lastMessage = chat.size() == 0 ? null : chat.content(0);
	}

	@Override
	protected void onDisable() {
		lastText = null;
		lastMessage = null;
		repeats = 0;
	}
}

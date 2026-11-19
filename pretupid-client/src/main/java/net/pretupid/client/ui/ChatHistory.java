package net.pretupid.client.ui;

import net.minecraft.network.chat.Component;

public interface ChatHistory {
	int size();

	Component content(int index);

	void replace(int index, Component content);

	void removeNewest();

	void refresh();
}

package net.pretupid.client.module;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public interface ManagedModule {
	Component manageLabel();

	boolean canManage(Minecraft minecraft);

	Screen createManager(Screen parent);
}

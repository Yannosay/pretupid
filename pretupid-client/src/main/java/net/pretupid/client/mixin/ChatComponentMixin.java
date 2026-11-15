package net.pretupid.client.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.module.utility.ChatTimestampsModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
	@ModifyVariable(method = "addMessage", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private Component pretupid$timestamp(Component message) {
		PretupidClient client = PretupidClient.getOrNull();
		if (client == null) {
			return message;
		}
		ChatTimestampsModule module = client.modules().get(ChatTimestampsModule.class);
		return module.isEnabled() ? module.decorate(message) : message;
	}
}

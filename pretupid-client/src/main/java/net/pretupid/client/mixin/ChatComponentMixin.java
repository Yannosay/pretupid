package net.pretupid.client.mixin;

import java.util.List;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.module.utility.ChatTimestampsModule;
import net.pretupid.client.module.utility.CompactChatModule;
import net.pretupid.client.module.utility.NickHiderModule;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
	@Shadow
	@Final
	private List<GuiMessage> allMessages;

	@Shadow
	protected abstract void refreshTrimmedMessages();

	@ModifyVariable(method = "addMessage", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private Component pretupid$decorate(Component message) {
		PretupidClient client = PretupidClient.getOrNull();
		if (client == null) {
			return message;
		}
		Component result = message;
		NickHiderModule nickHider = client.modules().get(NickHiderModule.class);
		if (nickHider.isEnabled()) {
			result = nickHider.hide(result);
		}
		CompactChatModule compactChat = client.modules().get(CompactChatModule.class);
		if (compactChat.isEnabled()) {
			result = compactChat.compact(result, allMessages, this::refreshTrimmedMessages);
		}
		ChatTimestampsModule timestamps = client.modules().get(ChatTimestampsModule.class);
		return timestamps.isEnabled() ? timestamps.decorate(result) : result;
	}

	@Inject(method = "addMessage", at = @At("TAIL"))
	private void pretupid$rememberLatest(Component message, MessageSignature signature, GuiMessageSource source, GuiMessageTag tag, CallbackInfo ci) {
		PretupidClient client = PretupidClient.getOrNull();
		if (client == null) {
			return;
		}
		CompactChatModule compactChat = client.modules().get(CompactChatModule.class);
		if (compactChat.isEnabled()) {
			compactChat.remember(allMessages);
		}
	}
}

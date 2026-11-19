package net.pretupid.client.mixin.platform;

import java.util.List;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.GuiMessage;
import net.minecraft.network.chat.Component;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.ui.ChatHistory;
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
public abstract class ChatComponentMixin implements ChatHistory {
	@Shadow
	@Final
	private List<GuiMessage> allMessages;

	@Shadow
	protected abstract void refreshTrimmedMessages();

	@ModifyVariable(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
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
			result = compactChat.compact(result, this);
		}
		ChatTimestampsModule timestamps = client.modules().get(ChatTimestampsModule.class);
		if (timestamps.isEnabled()) {
			result = timestamps.decorate(result);
		}
		if (client.settings().chatEmojis.enabled()) {
			try {
				result = client.chatEmojis().decorate(result);
			} catch (RuntimeException exception) {
				PretupidClient.LOGGER.debug("Could not add emoji to a chat message", exception);
			}
		}
		return result;
	}

	@Override
	public int size() {
		return allMessages.size();
	}

	@Override
	public Component content(int index) {
		return allMessages.get(index).content();
	}

	@Override
	public void replace(int index, Component content) {
		GuiMessage old = allMessages.get(index);
		allMessages.set(index, new GuiMessage(old.addedTime(), content, old.signature(), old.tag()));
	}

	@Override
	public void removeNewest() {
		if (!allMessages.isEmpty()) {
			allMessages.removeFirst();
		}
	}

	@Override
	public void refresh() {
		refreshTrimmedMessages();
	}

	@Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V", at = @At("TAIL"))
	private void pretupid$rememberLatest(CallbackInfo ci) {
		PretupidClient client = PretupidClient.getOrNull();
		if (client == null) {
			return;
		}
		CompactChatModule compactChat = client.modules().get(CompactChatModule.class);
		if (compactChat.isEnabled()) {
			compactChat.remember(this);
		}
	}
}

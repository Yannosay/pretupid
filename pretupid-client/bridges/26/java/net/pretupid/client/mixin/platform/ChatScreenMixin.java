package net.pretupid.client.mixin.platform;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.pretupid.client.emoji.EmojiPicker;
import net.pretupid.client.platform.Gfx;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen {
	@Shadow
	protected EditBox input;

	@Unique
	private EmojiPicker pretupid$picker;

	protected ChatScreenMixin(Component title) {
		super(title);
	}

	@Inject(method = "init", at = @At("TAIL"))
	private void pretupid$initEmoji(CallbackInfo ci) {
		if (!EmojiPicker.enabled() || input == null) {
			pretupid$picker = null;
			return;
		}
		input.setWidth(Math.max(40, input.getWidth() - EmojiPicker.BUTTON_SIZE - 2));
		pretupid$picker = new EmojiPicker(input);
	}

	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void pretupid$renderEmoji(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		if (pretupid$picker != null) {
			pretupid$picker.render(Gfx.of(graphics), font, mouseX, mouseY, width);
		}
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void pretupid$clickEmoji(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
		if (pretupid$picker != null && pretupid$picker.mouseClicked(event.x(), event.y(), event.button(), width)) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
	private void pretupid$scrollEmoji(double mouseX, double mouseY, double scrollX, double scrollY, CallbackInfoReturnable<Boolean> cir) {
		if (pretupid$picker != null && pretupid$picker.mouseScrolled(mouseX, mouseY, scrollY, width)) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void pretupid$keyEmoji(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
		if (pretupid$picker != null && pretupid$picker.keyPressed(event.key())) {
			cir.setReturnValue(true);
		}
	}
}

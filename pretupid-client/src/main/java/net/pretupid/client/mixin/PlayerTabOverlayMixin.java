package net.pretupid.client.mixin;

import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.module.utility.TabPingModule;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayMixin {
	@Inject(method = "extractPingIcon", at = @At("HEAD"), cancellable = true)
	private void pretupid$numericPing(GuiGraphicsExtractor graphics, int width, int x, int y, PlayerInfo info, CallbackInfo ci) {
		PretupidClient client = PretupidClient.getOrNull();
		if (client == null) {
			return;
		}
		TabPingModule module = client.modules().get(TabPingModule.class);
		if (module.isEnabled()) {
			module.render(graphics, width, x, y, info);
			ci.cancel();
		}
	}

	@Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
	private void pretupid$badgeTabName(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
		PretupidClient client = PretupidClient.getOrNull();
		if (client == null) {
			return;
		}
		cir.setReturnValue(client.nametags().decorateTabName(info.getProfile().id(), info.getProfile().name(), cir.getReturnValue()));
	}
}

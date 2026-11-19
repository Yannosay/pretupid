package net.pretupid.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.pretupid.client.platform.Platform;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.player.LocalPlayer;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.module.visual.FreecamModule;
import net.pretupid.client.module.visual.FreelookModule;
import net.pretupid.client.module.visual.ZoomModule;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void pretupid$zoomScroll(CallbackInfo ci, @Local(argsOnly = true, ordinal = 1) double scrollY) {
		PretupidClient client = PretupidClient.getOrNull();
		if (client == null || Platform.screen() != null) {
			return;
		}
		if (client.modules().get(ZoomModule.class).handleScroll(scrollY)) {
			ci.cancel();
		}
	}

	@Inject(method = "onButton", at = @At("HEAD"))
	private void pretupid$trackClicks(CallbackInfo ci, @Local(argsOnly = true) MouseButtonInfo info, @Local(argsOnly = true) int action) {
		PretupidClient client = PretupidClient.getOrNull();
		if (client == null || action != InputConstants.PRESS || Platform.screen() != null) {
			return;
		}
		if (info.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			client.clicks().recordLeft();
		} else if (info.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
			client.clicks().recordRight();
		}
	}

	@ModifyExpressionValue(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;", ordinal = 0))
	private Object pretupid$zoomSensitivity(Object original) {
		PretupidClient client = PretupidClient.getOrNull();
		if (client == null || !(original instanceof Double sensitivity)) {
			return original;
		}
		ZoomModule zoom = client.modules().get(ZoomModule.class);
		if (!zoom.isEnabled()) {
			return original;
		}
		return sensitivity * zoom.sensitivityMultiplier();
	}

	@WrapOperation(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
	private void pretupid$freelookTurn(LocalPlayer player, double dx, double dy, Operation<Void> original) {
		PretupidClient client = PretupidClient.getOrNull();
		if (client != null) {
			FreecamModule freecam = client.modules().get(FreecamModule.class);
			if (freecam.isEnabled() && freecam.active()) {
				freecam.turn(dx, dy);
				return;
			}
			FreelookModule freelook = client.modules().get(FreelookModule.class);
			if (freelook.isEnabled() && freelook.active()) {
				freelook.turn(dx, dy);
				return;
			}
		}
		original.call(player, dx, dy);
	}

	@ModifyExpressionValue(method = "turnPlayer", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Options;smoothCamera:Z"))
	private boolean pretupid$zoomCinematic(boolean original) {
		PretupidClient client = PretupidClient.getOrNull();
		return original || (client != null && client.modules().get(ZoomModule.class).forceCinematic());
	}
}

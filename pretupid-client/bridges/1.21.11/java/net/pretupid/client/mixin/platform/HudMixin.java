package net.pretupid.client.mixin.platform;

import net.pretupid.client.platform.Platform;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Gui;
import net.pretupid.client.PretupidClient;
import net.minecraft.client.Minecraft;
import net.pretupid.client.module.visual.CinematicModule;
import net.pretupid.client.ui.screen.CinematicEditorScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class HudMixin {
	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void pretupid$cinematicHud(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
		PretupidClient client = PretupidClient.getOrNull();
		if (client == null) {
			return;
		}
		CinematicModule cinematic = client.modules().get(CinematicModule.class);
		boolean editing = Platform.screen() instanceof CinematicEditorScreen;
		if (editing || cinematic.isEnabled() && cinematic.hidesHud() && client.cinematic().playing()) {
			ci.cancel();
		}
	}
}

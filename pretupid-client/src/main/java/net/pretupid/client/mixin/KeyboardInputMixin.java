package net.pretupid.client.mixin;

import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.module.visual.FreecamModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends ClientInput {
	@Inject(method = "tick", at = @At("TAIL"))
	private void pretupid$freecamInput(CallbackInfo ci) {
		PretupidClient client = PretupidClient.getOrNull();
		if (client == null) {
			return;
		}
		FreecamModule freecam = client.modules().get(FreecamModule.class);
		if (!freecam.isEnabled() || !freecam.active()) {
			return;
		}
		freecam.captureInput(keyPresses);
		keyPresses = Input.EMPTY;
		moveVector = Vec2.ZERO;
	}
}

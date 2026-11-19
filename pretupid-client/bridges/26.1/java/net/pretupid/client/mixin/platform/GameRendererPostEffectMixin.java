package net.pretupid.client.mixin.platform;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.module.visual.MotionBlurModule;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererPostEffectMixin {
	@Shadow
	private Identifier postEffectId;

	@Shadow
	@Final
	private Minecraft minecraft;

	@Inject(method = "update", at = @At("TAIL"))
	private void pretupid$requestMotionBlur(CallbackInfo ci) {
		PretupidClient client = PretupidClient.getOrNull();
		if (client == null) {
			return;
		}
		Identifier effect = client.modules().get(MotionBlurModule.class).activeEffect(minecraft);
		boolean ours = postEffectId != null && postEffectId.getNamespace().equals(PretupidClient.MOD_ID);
		if (effect != null && (postEffectId == null || ours)) {
			postEffectId = effect;
		} else if (effect == null && ours) {
			postEffectId = null;
		}
	}
}

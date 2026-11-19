package net.pretupid.client.mixin.platform;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.module.visual.FullbrightModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LightTexture.class)
public abstract class LightTextureMixin {
	@ModifyExpressionValue(method = "updateLightTexture", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;hasEffect(Lnet/minecraft/core/Holder;)Z", ordinal = 0))
	private boolean pretupid$fullbrightEffect(boolean original) {
		FullbrightModule module = pretupid$module();
		return original || module != null && module.isEnabled();
	}

	@WrapOperation(method = "updateLightTexture", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;getNightVisionScale(Lnet/minecraft/world/entity/LivingEntity;F)F"))
	private float pretupid$fullbrightScale(LivingEntity entity, float partialTick, Operation<Float> original) {
		FullbrightModule module = pretupid$module();
		boolean hasEffect = entity.hasEffect(MobEffects.NIGHT_VISION);
		float base = hasEffect ? original.call(entity, partialTick) : 0f;
		return module == null ? base : module.nightVision(base);
	}

	private static FullbrightModule pretupid$module() {
		PretupidClient client = PretupidClient.getOrNull();
		return client == null ? null : client.modules().get(FullbrightModule.class);
	}
}

package net.pretupid.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.pretupid.client.PretupidClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({EntityRenderer.class, LivingEntityRenderer.class})
public abstract class ScreenieNameFilterMixin {
	@ModifyReturnValue(method = "shouldShowName", at = @At("RETURN"))
	private boolean pretupid$hideNamesInScreenie(boolean original) {
		PretupidClient client = PretupidClient.getOrNull();
		return original && (client == null || !client.screenies().hidesNames());
	}
}

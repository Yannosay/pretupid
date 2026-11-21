package net.pretupid.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.pretupid.client.PretupidClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityRenderDispatcher.class)
public abstract class ScreenieEntityFilterMixin {
	@ModifyReturnValue(method = "shouldRender", at = @At("RETURN"))
	private boolean pretupid$hideInScreenie(boolean original, @Local(argsOnly = true) Entity entity) {
		PretupidClient client = PretupidClient.getOrNull();
		return original && (client == null || !client.screenies().hidesEntity(entity));
	}
}

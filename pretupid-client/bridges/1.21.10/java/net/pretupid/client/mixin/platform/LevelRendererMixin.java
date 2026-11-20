package net.pretupid.client.mixin.platform;

import net.minecraft.client.renderer.LevelRenderer;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.module.visual.BlockOutlineModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
	@ModifyVariable(method = "renderHitOutline", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private int pretupid$outlineColor(int color) {
		BlockOutlineModule module = pretupid$outline();
		return module == null ? color : module.color();
	}

	@Unique
	private static BlockOutlineModule pretupid$outline() {
		PretupidClient client = PretupidClient.getOrNull();
		if (client == null) {
			return null;
		}
		BlockOutlineModule module = client.modules().get(BlockOutlineModule.class);
		return module.isEnabled() ? module : null;
	}
}

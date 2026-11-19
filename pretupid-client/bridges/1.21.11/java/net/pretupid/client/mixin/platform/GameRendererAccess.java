package net.pretupid.client.mixin.platform;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GameRenderer.class)
public interface GameRendererAccess {
	@Invoker("getFov")
	float pretupid$fov(Camera camera, float partialTick, boolean useFovSetting);
}

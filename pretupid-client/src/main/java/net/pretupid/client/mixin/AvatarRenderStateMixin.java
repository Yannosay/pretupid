package net.pretupid.client.mixin;

import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.pretupid.client.cosmetic.EmotePoseHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(AvatarRenderState.class)
public abstract class AvatarRenderStateMixin implements EmotePoseHolder {
	@Unique
	private float[][] pretupid$emotePose;

	@Override
	public float[][] pretupid$emotePose() {
		return pretupid$emotePose;
	}

	@Override
	public void pretupid$setEmotePose(float[][] pose) {
		this.pretupid$emotePose = pose;
	}
}

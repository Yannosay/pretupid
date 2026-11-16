package net.pretupid.client.mixin;

import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.pretupid.client.cosmetic.CosmeticStateHolder;
import net.pretupid.client.cosmetic.EmotePoseHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(AvatarRenderState.class)
public abstract class AvatarRenderStateMixin implements EmotePoseHolder, CosmeticStateHolder {
	@Unique
	private float[][] pretupid$emotePose;

	@Unique
	private String pretupid$back;

	@Unique
	private String pretupid$pet;

	@Override
	public float[][] pretupid$emotePose() {
		return pretupid$emotePose;
	}

	@Override
	public void pretupid$setEmotePose(float[][] pose) {
		this.pretupid$emotePose = pose;
	}

	@Override
	public String pretupid$back() {
		return pretupid$back;
	}

	@Override
	public String pretupid$pet() {
		return pretupid$pet;
	}

	@Override
	public void pretupid$setCosmetics(String back, String pet) {
		this.pretupid$back = back;
		this.pretupid$pet = pet;
	}
}

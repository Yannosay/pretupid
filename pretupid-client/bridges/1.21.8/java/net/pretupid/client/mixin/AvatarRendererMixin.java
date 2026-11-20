package net.pretupid.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.pretupid.client.cosmetic.EmotePoses;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.client.resources.PlayerSkin;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.cosmetic.CapePhysics;
import net.pretupid.client.cosmetic.CosmeticStateHolder;
import net.pretupid.client.cosmetic.EmotePoseHolder;
import net.pretupid.client.cosmetic.PetFollower;
import net.pretupid.client.cosmetic.Wearables;
import net.pretupid.client.network.RemoteProfile;
import net.pretupid.client.skins.MojangCapes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
public abstract class AvatarRendererMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;F)V", at = @At("TAIL"))
	private void pretupid$decorate(CallbackInfo ci, @Local(argsOnly = true) AbstractClientPlayer player, @Local(argsOnly = true) PlayerRenderState state) {
		PretupidClient client = PretupidClient.getOrNull();
		if (client == null) {
			return;
		}
		if (state instanceof EmotePoseHolder holder) {
			holder.pretupid$setEmotePose(client.emotes().pose(player.getUUID()));
		}
		if (state instanceof CosmeticStateHolder holder) {
			holder.pretupid$setCosmetics(null, null, null);
		}
		if (state.nameTag != null) {
			state.nameTag = client.nametags().decorateNametag(player.getUUID(), player.getGameProfile().name(), state.nameTag);
		}
		if (client.nametags().isSelf(player.getUUID())) {
			state.skin = client.localSkin().apply(state.skin);
			Wearables.apply(player, state, Wearables.own());
		} else if (client.settings().showCosmetics.enabled()) {
			client.presence().profileOf(player.getUUID()).ifPresent(profile -> Wearables.apply(player, state, profile.wearables()));
		}
		pretupid$applyCape(client, player, state);
		if (state instanceof CosmeticStateHolder holder) {
			boolean simulate = client.settings().capePhysics.enabled() && state.showCape && !state.isInvisible && state.skin != null && state.skin.capeTexture() != null;
			holder.pretupid$setCapeAngles(simulate ? CapePhysics.update(state) : null);
			boolean pet = holder.pretupid$pet() != null && !state.isInvisible && !state.isSpectator;
			holder.pretupid$setPetPlacement(pet ? PetFollower.update(state) : null);
		}
	}

	@Unique
	private static void pretupid$applyCape(PretupidClient client, Player player, PlayerRenderState state) {
		RemoteProfile.CapeRef cape;
		if (client.nametags().isSelf(player.getUUID())) {
			cape = client.cosmetics().ownCapeTexture();
			if (cape == null && client.mojangCapes().overridesOwnCape() && state.skin != null) {
				pretupid$applyMojangCape(client, player, state);
				return;
			}
		} else if (client.settings().showCosmetics.enabled()) {
			cape = client.presence().profileOf(player.getUUID()).map(RemoteProfile::cape).orElse(null);
		} else {
			cape = null;
		}
		if (cape == null || state.skin == null) {
			return;
		}
		ResourceLocation texture = client.cosmetics().capes().get(cape);
		if (texture == null) {
			return;
		}
		PlayerSkin skin = state.skin;
		state.skin = new PlayerSkin(skin.texture(), skin.textureUrl(), texture, skin.elytraTexture() != null ? skin.elytraTexture() : texture, skin.model(), skin.secure());
		state.showCape = player.isModelPartShown(PlayerModelPart.CAPE);
	}

	@Unique
	private static void pretupid$applyMojangCape(PretupidClient client, Player player, PlayerRenderState state) {
		MojangCapes.Cape active = client.mojangCapes().active();
		PlayerSkin skin = state.skin;
		if (active == null) {
			state.skin = new PlayerSkin(skin.texture(), skin.textureUrl(), null, null, skin.model(), skin.secure());
			return;
		}
		ResourceLocation texture = client.mojangCapes().texture(active);
		if (texture == null) {
			return;
		}
		state.skin = new PlayerSkin(skin.texture(), skin.textureUrl(), texture, texture, skin.model(), skin.secure());
		state.showCape = player.isModelPartShown(PlayerModelPart.CAPE);
	}

	@Inject(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V", at = @At("TAIL"))
	private void pretupid$emoteRoot(CallbackInfo ci, @Local(argsOnly = true) PlayerRenderState state, @Local(argsOnly = true) PoseStack poseStack) {
		EmotePoses.applyRoot(state, poseStack);
	}
}

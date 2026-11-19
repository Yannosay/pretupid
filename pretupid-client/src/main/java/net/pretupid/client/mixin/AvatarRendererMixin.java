package net.pretupid.client.mixin;

import net.pretupid.client.cosmetic.EmotePoses;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.player.PlayerSkin;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.cosmetic.CapePhysics;
import net.pretupid.client.cosmetic.CosmeticStateHolder;
import net.pretupid.client.cosmetic.EmotePoseHolder;
import net.pretupid.client.cosmetic.PetFollower;
import net.pretupid.client.cosmetic.Wearables;
import net.pretupid.client.module.visual.OwnNametagModule;
import net.pretupid.client.network.RemoteProfile;
import net.pretupid.client.skins.MojangCapes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
	private void pretupid$decorate(Avatar avatar, AvatarRenderState state, float partialTick, CallbackInfo ci) {
		PretupidClient client = PretupidClient.getOrNull();
		if (client == null || !(avatar instanceof Player player)) {
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
			boolean simulate = client.settings().capePhysics.enabled() && state.showCape && !state.isInvisible && state.skin != null && state.skin.cape() != null;
			holder.pretupid$setCapeAngles(simulate ? CapePhysics.update(state) : null);
			boolean pet = holder.pretupid$pet() != null && !state.isInvisible && !state.isSpectator;
			holder.pretupid$setPetPlacement(pet ? PetFollower.update(state) : null);
		}
	}

	@Unique
	private static void pretupid$applyCape(PretupidClient client, Player player, AvatarRenderState state) {
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
		Identifier texture = client.cosmetics().capes().get(cape);
		if (texture == null) {
			return;
		}
		PlayerSkin skin = state.skin;
		ClientAsset.Texture asset = new ClientAsset.ResourceTexture(texture, texture);
		state.skin = new PlayerSkin(skin.body(), asset, skin.elytra() != null ? skin.elytra() : asset, skin.model(), skin.secure());
		state.showCape = player.isModelPartShown(PlayerModelPart.CAPE);
	}

	@Unique
	private static void pretupid$applyMojangCape(PretupidClient client, Player player, AvatarRenderState state) {
		MojangCapes.Cape active = client.mojangCapes().active();
		PlayerSkin skin = state.skin;
		if (active == null) {
			state.skin = new PlayerSkin(skin.body(), null, null, skin.model(), skin.secure());
			return;
		}
		Identifier texture = client.mojangCapes().texture(active);
		if (texture == null) {
			return;
		}
		ClientAsset.Texture asset = new ClientAsset.ResourceTexture(texture, texture);
		state.skin = new PlayerSkin(skin.body(), asset, asset, skin.model(), skin.secure());
		state.showCape = player.isModelPartShown(PlayerModelPart.CAPE);
	}

	@Inject(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V", at = @At("TAIL"))
	private void pretupid$emoteRoot(AvatarRenderState state, PoseStack poseStack, float bodyRot, float scale, CallbackInfo ci) {
		EmotePoses.applyRoot(state, poseStack);
	}

	@Inject(method = "shouldShowName(Lnet/minecraft/world/entity/Avatar;D)Z", at = @At("HEAD"), cancellable = true)
	private void pretupid$showOwnNametag(Avatar avatar, double distanceSquared, CallbackInfoReturnable<Boolean> cir) {
		Minecraft minecraft = Minecraft.getInstance();
		if (avatar != minecraft.player || minecraft.options.getCameraType().isFirstPerson() || minecraft.gui.hud.isHidden()) {
			return;
		}
		PretupidClient client = PretupidClient.getOrNull();
		if (client != null && client.modules().get(OwnNametagModule.class).isEnabled() && !avatar.isInvisible()) {
			cir.setReturnValue(true);
		}
	}
}

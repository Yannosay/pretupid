package net.pretupid.client.skins;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import net.pretupid.client.PretupidClient;

public final class LocalSkinOverride {
	private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(PretupidClient.MOD_ID, "skin/local_override");

	private ClientAsset.Texture asset;
	private PlayerModelType model;

	public void set(Path file, SkinLibrary.Variant variant) {
		NativeImage image;
		try (InputStream input = Files.newInputStream(file)) {
			image = NativeImage.read(input);
		} catch (IOException exception) {
			PretupidClient.LOGGER.warn("Could not load applied skin {}", file, exception);
			return;
		}
		Minecraft.getInstance().getTextureManager().register(TEXTURE, new DynamicTexture(() -> "Pretupid applied skin", image));
		asset = new ClientAsset.ResourceTexture(TEXTURE, TEXTURE);
		model = variant == SkinLibrary.Variant.SLIM ? PlayerModelType.SLIM : PlayerModelType.WIDE;
	}

	public void clear() {
		if (asset != null) {
			Minecraft.getInstance().getTextureManager().release(TEXTURE);
			asset = null;
			model = null;
		}
	}

	public PlayerSkin apply(PlayerSkin skin) {
		if (asset == null || skin == null) {
			return skin;
		}
		return new PlayerSkin(asset, skin.cape(), skin.elytra(), model, skin.secure());
	}
}

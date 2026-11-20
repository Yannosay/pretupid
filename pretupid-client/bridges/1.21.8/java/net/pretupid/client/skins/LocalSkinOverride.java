package net.pretupid.client.skins;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.pretupid.client.PretupidClient;

public final class LocalSkinOverride {
	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(PretupidClient.MOD_ID, "skin/local_override");

	private boolean active;
	private PlayerSkin.Model model;

	public void set(Path file, SkinLibrary.Variant variant) {
		NativeImage image;
		try (InputStream input = Files.newInputStream(file)) {
			image = NativeImage.read(input);
		} catch (IOException exception) {
			PretupidClient.LOGGER.warn("Could not load applied skin {}", file, exception);
			return;
		}
		Minecraft.getInstance().getTextureManager().register(TEXTURE, new DynamicTexture(() -> "Pretupid applied skin", image));
		active = true;
		model = variant == SkinLibrary.Variant.SLIM ? PlayerSkin.Model.SLIM : PlayerSkin.Model.WIDE;
	}

	public void clear() {
		if (active) {
			Minecraft.getInstance().getTextureManager().release(TEXTURE);
			active = false;
			model = null;
		}
	}

	public PlayerSkin apply(PlayerSkin skin) {
		if (!active || skin == null) {
			return skin;
		}
		return new PlayerSkin(TEXTURE, null, skin.capeTexture(), skin.elytraTexture(), model, skin.secure());
	}
}

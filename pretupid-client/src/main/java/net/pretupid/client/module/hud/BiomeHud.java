package net.pretupid.client.module.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.locale.Language;
import net.minecraft.resources.Identifier;
import net.pretupid.client.hud.HudPosition;
import net.pretupid.client.hud.TextHudModule;

public final class BiomeHud extends TextHudModule {
	public BiomeHud() {
		super("biome", "wheat_seeds", false, new HudPosition(HudPosition.Anchor.START, 4, HudPosition.Anchor.END, 76));
	}

	@Override
	protected String value(Minecraft minecraft, boolean preview) {
		if (minecraft.level == null || minecraft.player == null) {
			return I18n.get("biome.minecraft.plains");
		}
		return minecraft.level.getBiome(minecraft.player.blockPosition()).unwrapKey()
			.map(key -> {
				Identifier id = key.identifier();
				String translationKey = "biome." + id.getNamespace() + "." + id.getPath();
				return Language.getInstance().has(translationKey) ? I18n.get(translationKey) : id.getPath();
			})
			.orElse("?");
	}
}

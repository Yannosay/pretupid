package net.pretupid.client.cosmetic.model;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.cosmetic.Catalog;
import net.pretupid.client.cosmetic.CosmeticsService;

public final class CosmeticModels {
	public record Entry(CosmeticModel model, Identifier texture) {
	}

	private record Cached(Catalog.WearableEntry source, boolean textured, Entry entry) {
	}

	private static final Map<String, Cached> BAKED = new HashMap<>();
	private static final Map<String, Catalog.WearableEntry> BROKEN = new HashMap<>();
	private static int generation;

	private CosmeticModels() {
	}

	public static Entry get(Catalog.WearableKind kind, String id) {
		if (id == null) {
			return null;
		}
		CosmeticsService cosmetics = PretupidClient.get().cosmetics();
		Catalog.WearableEntry definition = cosmetics.catalog().wearable(kind, id).orElse(null);
		if (definition == null) {
			cosmetics.requestCatalogRefresh();
			return null;
		}
		String key = kind.id() + "/" + id;
		NativeImage texture = definition.texture() == null ? null : cosmetics.wearableTextures().get(definition.texture());
		Cached cached = BAKED.get(key);
		if (cached != null && cached.source() == definition && cached.textured() == (texture != null)) {
			return cached.entry();
		}
		if (BROKEN.get(key) == definition) {
			return null;
		}
		if (cached != null) {
			Minecraft.getInstance().getTextureManager().release(cached.entry().texture());
			BAKED.remove(key);
		}
		try {
			Shape.Anchor anchor = switch (kind) {
				case HAT -> Shape.Anchor.HEAD;
				case BACK -> Shape.Anchor.BODY;
				case PET -> Shape.Anchor.ROOT;
			};
			Shape shape = WearableParser.parse(definition.model(), anchor, texture != null);
			ShapeBaker.Baked baked = ShapeBaker.bake(shape, texture);
			Identifier location = Identifier.fromNamespaceAndPath(PretupidClient.MOD_ID, "cosmetic/" + key.toLowerCase(Locale.ROOT) + "_" + (++generation));
			Minecraft.getInstance().getTextureManager().register(location, new DynamicTexture(() -> "pretupid cosmetic " + key, baked.texture()));
			Entry entry = new Entry(new CosmeticModel(baked.root(), shape), location);
			BAKED.put(key, new Cached(definition, texture != null, entry));
			BROKEN.remove(key);
			return entry;
		} catch (RuntimeException exception) {
			BROKEN.put(key, definition);
			PretupidClient.LOGGER.warn("Wearable {} could not be built: {}", key, exception.getMessage());
			return null;
		}
	}

	public static void clear() {
		for (Cached cached : BAKED.values()) {
			Minecraft.getInstance().getTextureManager().release(cached.entry().texture());
		}
		BAKED.clear();
		BROKEN.clear();
	}
}

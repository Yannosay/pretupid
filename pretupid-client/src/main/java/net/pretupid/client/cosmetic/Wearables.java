package net.pretupid.client.cosmetic;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.cosmetic.model.CosmeticModels;

public final class Wearables {
	public static final List<String> HATS = List.of("top_hat", "crown", "halo", "party_hat", "wizard_hat", "cat_ears", "propeller_cap", "headphones");
	public static final List<String> BACK = CosmeticModels.backItems();
	public static final List<String> PETS = CosmeticModels.pets();

	public record Loadout(String hat, String back, String pet) {
		public static final Loadout NONE = new Loadout(null, null, null);

		public boolean empty() {
			return hat == null && back == null && pet == null;
		}
	}

	private Wearables() {
	}

	public static String validHat(String id) {
		return id != null && HATS.contains(id) ? id : null;
	}

	public static String validBack(String id) {
		return id != null && BACK.contains(id) ? id : null;
	}

	public static String validPet(String id) {
		return id != null && PETS.contains(id) ? id : null;
	}

	public static Loadout own() {
		var settings = PretupidClient.get().settings();
		return new Loadout(validHat(settings.hat.get()), validBack(settings.back.get()), validPet(settings.pet.get()));
	}

	private static final Map<String, ItemStack> HAT_STACKS = new HashMap<>();

	private static ItemStack hatStack(String hat) {
		return HAT_STACKS.computeIfAbsent(hat, Wearables::createHatStack);
	}

	private static ItemStack createHatStack(String hat) {
		ItemStack stack = new ItemStack(Items.PAPER);
		stack.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath(PretupidClient.MOD_ID, "hat/" + hat));
		return stack;
	}

	public static void apply(Player player, AvatarRenderState state, Loadout loadout) {
		if (loadout == null || loadout.empty() || player.isInvisible()) {
			return;
		}
		if (loadout.hat() != null && state.headEquipment.isEmpty() && state.headItem.isEmpty() && state.wornHeadType == null) {
			Minecraft.getInstance().getItemModelResolver().updateForLiving(state.headItem, hatStack(loadout.hat()), ItemDisplayContext.HEAD, player);
		}
		if (state instanceof CosmeticStateHolder holder) {
			holder.pretupid$setCosmetics(loadout.back(), loadout.pet());
		}
	}
}

package net.pretupid.client.cosmetic;

import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import net.pretupid.client.PretupidClient;

public final class Wearables {
	public static final List<String> HATS = List.of("top_hat", "crown", "halo", "party_hat", "wizard_hat", "cat_ears", "propeller_cap", "headphones");
	public static final List<String> BOOTS = List.of("sneakers", "cowboy_boots", "flame_boots", "frost_boots", "gold_boots");
	public static final Map<String, Parrot.Variant> PETS = Map.of(
		"parrot_red", Parrot.Variant.RED_BLUE,
		"parrot_blue", Parrot.Variant.BLUE,
		"parrot_green", Parrot.Variant.GREEN,
		"parrot_cyan", Parrot.Variant.YELLOW_BLUE,
		"parrot_gray", Parrot.Variant.GRAY);
	public static final List<String> PET_ORDER = List.of("parrot_red", "parrot_blue", "parrot_green", "parrot_cyan", "parrot_gray");

	public record Loadout(String hat, String boots, String pet) {
		public static final Loadout NONE = new Loadout(null, null, null);

		public boolean empty() {
			return hat == null && boots == null && pet == null;
		}
	}

	private Wearables() {
	}

	public static String validHat(String id) {
		return id != null && HATS.contains(id) ? id : null;
	}

	public static String validBoots(String id) {
		return id != null && BOOTS.contains(id) ? id : null;
	}

	public static String validPet(String id) {
		return id != null && PETS.containsKey(id) ? id : null;
	}

	public static Loadout own() {
		var settings = PretupidClient.get().settings();
		return new Loadout(validHat(settings.hat.get()), validBoots(settings.boots.get()), validPet(settings.pet.get()));
	}

	private static final Map<String, ItemStack> STACKS = new java.util.HashMap<>();

	private static ItemStack hatStack(String hat) {
		return STACKS.computeIfAbsent("hat:" + hat, key -> createHatStack(hat));
	}

	private static ItemStack bootsStack(String boots) {
		return STACKS.computeIfAbsent("boots:" + boots, key -> createBootsStack(boots));
	}

	private static ItemStack createHatStack(String hat) {
		ItemStack stack = new ItemStack(Items.PAPER);
		stack.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath(PretupidClient.MOD_ID, "hat/" + hat));
		return stack;
	}

	private static ItemStack createBootsStack(String boots) {
		ItemStack stack = new ItemStack(Items.LEATHER_BOOTS);
		ResourceKey<net.minecraft.world.item.equipment.EquipmentAsset> asset = ResourceKey.create(EquipmentAssets.ROOT_ID, Identifier.fromNamespaceAndPath(PretupidClient.MOD_ID, boots));
		stack.set(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.FEET).setAsset(asset).build());
		return stack;
	}

	public static void apply(Player player, AvatarRenderState state, Loadout loadout) {
		if (loadout == null || loadout.empty() || player.isInvisible()) {
			return;
		}
		if (loadout.hat() != null && state.headEquipment.isEmpty() && state.headItem.isEmpty() && state.wornHeadType == null) {
			Minecraft.getInstance().getItemModelResolver().updateForLiving(state.headItem, hatStack(loadout.hat()), ItemDisplayContext.HEAD, player);
		}
		if (loadout.boots() != null && state.feetEquipment.isEmpty()) {
			state.feetEquipment = bootsStack(loadout.boots());
		}
		if (loadout.pet() != null && state.parrotOnLeftShoulder == null) {
			state.parrotOnLeftShoulder = PETS.get(loadout.pet());
		}
	}
}

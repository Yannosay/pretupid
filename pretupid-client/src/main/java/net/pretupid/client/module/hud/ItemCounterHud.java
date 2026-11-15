package net.pretupid.client.module.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.pretupid.client.hud.HudPosition;
import net.pretupid.client.hud.TextHudModule;
import net.pretupid.client.setting.EnumSetting;
import net.pretupid.client.ui.Theme;

public final class ItemCounterHud extends TextHudModule {
	public enum Tracked {
		ARROWS,
		TOTEMS,
		GOLDEN_APPLES,
		ENDER_PEARLS,
		FIREWORKS;

		boolean matches(ItemStack stack) {
			Item item = stack.getItem();
			return switch (this) {
				case ARROWS -> item == Items.ARROW || item == Items.SPECTRAL_ARROW || item == Items.TIPPED_ARROW;
				case TOTEMS -> item == Items.TOTEM_OF_UNDYING;
				case GOLDEN_APPLES -> item == Items.GOLDEN_APPLE || item == Items.ENCHANTED_GOLDEN_APPLE;
				case ENDER_PEARLS -> item == Items.ENDER_PEARL;
				case FIREWORKS -> item == Items.FIREWORK_ROCKET;
			};
		}
	}

	private final EnumSetting<Tracked> tracked = add(new EnumSetting<>("tracked", Tracked.ARROWS));

	public ItemCounterHud() {
		super("item_counter", "arrow", false, new HudPosition(HudPosition.Anchor.START, 4, HudPosition.Anchor.START, 160));
	}

	@Override
	protected String label() {
		return I18n.get("pretupid.module.item_counter.tracked." + tracked.get().name().toLowerCase(java.util.Locale.ROOT));
	}

	private int count(Minecraft minecraft) {
		if (minecraft.player == null) {
			return 0;
		}
		Inventory inventory = minecraft.player.getInventory();
		int total = 0;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (!stack.isEmpty() && tracked.get().matches(stack)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	@Override
	protected String value(Minecraft minecraft, boolean preview) {
		return minecraft.player == null && preview ? "64" : Integer.toString(count(minecraft));
	}

	@Override
	protected int valueColor(Minecraft minecraft, boolean preview) {
		return minecraft.player != null && count(minecraft) == 0 ? Theme.DANGER : super.valueColor(minecraft, preview);
	}
}

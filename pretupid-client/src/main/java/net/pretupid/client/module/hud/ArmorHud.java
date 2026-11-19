package net.pretupid.client.module.hud;

import net.pretupid.client.platform.Gfx;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.pretupid.client.hud.HudModule;
import net.pretupid.client.hud.HudPosition;
import net.pretupid.client.setting.BooleanSetting;
import net.pretupid.client.setting.ColorSetting;
import net.pretupid.client.setting.EnumSetting;
import net.pretupid.client.ui.Draw;
import net.pretupid.client.util.Colors;

public final class ArmorHud extends HudModule {
	public enum ArmorLayout {
		VERTICAL,
		HORIZONTAL
	}

	public enum DurabilityDisplay {
		NONE,
		PERCENT,
		REMAINING
	}

	private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	private final EnumSetting<ArmorLayout> layout = add(new EnumSetting<>("layout", ArmorLayout.VERTICAL));
	private final EnumSetting<DurabilityDisplay> durability = add(new EnumSetting<>("durability", DurabilityDisplay.PERCENT));
	private final BooleanSetting showHeld = add(new BooleanSetting("show_held", true));
	private final BooleanSetting dynamicColor = add(new BooleanSetting("dynamic_color", true));
	private final ColorSetting textColor = shared(new ColorSetting("value_color", 0xFFFFFFFF));

	public ArmorHud() {
		super("armor", "iron_chestplate", false, new HudPosition(HudPosition.Anchor.END, 4, HudPosition.Anchor.CENTER, 0));
	}

	@Override
	protected boolean backgroundByDefault() {
		return false;
	}

	private List<ItemStack> items(Minecraft minecraft, boolean preview) {
		List<ItemStack> items = new ArrayList<>();
		LocalPlayer player = minecraft.player;
		if (player != null) {
			for (EquipmentSlot slot : ARMOR_SLOTS) {
				ItemStack stack = player.getItemBySlot(slot);
				if (!stack.isEmpty()) {
					items.add(stack);
				}
			}
			if (showHeld.enabled()) {
				ItemStack main = player.getMainHandItem();
				if (!main.isEmpty()) {
					items.add(main);
				}
				ItemStack off = player.getItemBySlot(EquipmentSlot.OFFHAND);
				if (!off.isEmpty()) {
					items.add(off);
				}
			}
		}
		if (items.isEmpty() && preview) {
			items.add(new ItemStack(Items.DIAMOND_HELMET));
			items.add(new ItemStack(Items.DIAMOND_CHESTPLATE));
			items.add(new ItemStack(Items.DIAMOND_LEGGINGS));
			items.add(new ItemStack(Items.DIAMOND_BOOTS));
		}
		return items;
	}

	@Override
	public boolean visible(Minecraft minecraft) {
		return super.visible(minecraft) && !items(minecraft, false).isEmpty();
	}

	private String label(ItemStack stack) {
		if (stack.isDamageableItem() && durability.get() != DurabilityDisplay.NONE) {
			int remaining = stack.getMaxDamage() - stack.getDamageValue();
			if (durability.get() == DurabilityDisplay.PERCENT) {
				return Math.round(remaining * 100f / stack.getMaxDamage()) + "%";
			}
			return Integer.toString(remaining);
		}
		if (stack.getCount() > 1) {
			return Integer.toString(stack.getCount());
		}
		return "";
	}

	private int color(ItemStack stack) {
		if (!dynamicColor.enabled() || !stack.isDamageableItem()) {
			return textColor.resolve();
		}
		float ratio = 1f - stack.getDamageValue() / (float) stack.getMaxDamage();
		if (ratio > 0.5f) {
			return Colors.lerp(0xFFFACC15, 0xFF4ADE80, (ratio - 0.5f) * 2f);
		}
		return Colors.lerp(0xFFF87171, 0xFFFACC15, ratio * 2f);
	}

	@Override
	public int width(Font font, Minecraft minecraft, boolean preview) {
		List<ItemStack> items = items(minecraft, preview);
		if (layout.get() == ArmorLayout.HORIZONTAL) {
			return Math.max(18, items.size() * 20 - 2) + padding() * 2;
		}
		int text = 0;
		for (ItemStack stack : items) {
			text = Math.max(text, font.width(label(stack)));
		}
		return 16 + (text > 0 ? text + 4 : 0) + padding() * 2;
	}

	@Override
	public int height(Font font, Minecraft minecraft, boolean preview) {
		int count = Math.max(1, items(minecraft, preview).size());
		if (layout.get() == ArmorLayout.HORIZONTAL) {
			return 16 + (durability.get() == DurabilityDisplay.NONE ? 0 : 8) + padding() * 2;
		}
		return count * 18 - 2 + padding() * 2;
	}

	@Override
	public void render(Gfx graphics, Font font, Minecraft minecraft, boolean preview) {
		List<ItemStack> items = items(minecraft, preview);
		drawBackground(graphics, width(font, minecraft, preview), height(font, minecraft, preview));
		int p = padding();
		boolean shadow = textShadow.enabled();
		for (int i = 0; i < items.size(); i++) {
			ItemStack stack = items.get(i);
			String text = label(stack);
			if (layout.get() == ArmorLayout.HORIZONTAL) {
				int x = p + i * 20;
				graphics.item(stack, x, p);
				if (!text.isEmpty()) {
					float scale = 0.75f;
					float textX = x + 8 - font.width(text) * scale / 2f;
					Draw.scaled(graphics, font, Component.literal(text), textX, p + 17, scale, color(stack), shadow);
				}
			} else {
				int y = p + i * 18;
				graphics.item(stack, p, y);
				if (!text.isEmpty()) {
					graphics.text(font, text, p + 20, y + 4, color(stack), shadow);
				}
			}
		}
	}
}

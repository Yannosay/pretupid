package net.pretupid.client.module.utility;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.module.Category;
import net.pretupid.client.module.Module;
import net.pretupid.client.setting.BooleanSetting;
import net.pretupid.client.setting.NumberSetting;

public final class DurabilityAlertModule extends Module {
	private static final EquipmentSlot[] SLOTS = {
		EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
	};

	private final NumberSetting threshold = add(new NumberSetting("threshold", 10, 1, 50, 1, "%"));
	private final BooleanSetting sound = add(new BooleanSetting("sound", true));
	private final Map<EquipmentSlot, Boolean> alerted = new EnumMap<>(EquipmentSlot.class);
	private int cooldown;

	public DurabilityAlertModule() {
		super("durability_alert", Category.UTILITY, "brush", false);
	}

	@Override
	public void onTick(Minecraft minecraft) {
		if (++cooldown < 10) {
			return;
		}
		cooldown = 0;
		LocalPlayer player = minecraft.player;
		if (player == null) {
			alerted.clear();
			return;
		}
		for (EquipmentSlot slot : SLOTS) {
			ItemStack stack = player.getItemBySlot(slot);
			if (stack.isEmpty() || !stack.isDamageableItem()) {
				alerted.remove(slot);
				continue;
			}
			int remaining = stack.getMaxDamage() - stack.getDamageValue();
			float percent = remaining * 100f / stack.getMaxDamage();
			boolean low = percent <= threshold.floatValue();
			if (low && !alerted.getOrDefault(slot, false)) {
				alerted.put(slot, true);
				PretupidClient.get().notifications().warning(
					Component.translatable("pretupid.notification.durability"),
					Component.translatable("pretupid.notification.durability_body", stack.getHoverName(), remaining),
					stack.copy());
				if (sound.enabled()) {
					minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ANVIL_LAND, 1.6f, 0.35f));
				}
			} else if (!low) {
				alerted.remove(slot);
			}
		}
	}
}

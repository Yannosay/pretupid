package net.pretupid.client.module.utility;

import net.pretupid.client.platform.Platform;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.module.Category;
import net.pretupid.client.module.Module;
import net.pretupid.client.setting.BooleanSetting;

public final class DeathCoordinatesModule extends Module {
	private final BooleanSetting showDimension = add(new BooleanSetting("show_dimension", true));
	private final BooleanSetting copyButton = add(new BooleanSetting("copy_button", true));
	private boolean wasDead;

	public DeathCoordinatesModule() {
		super("death_coordinates", Category.UTILITY, "filled_map", false);
	}

	@Override
	public void onTick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null) {
			wasDead = false;
			return;
		}
		boolean dead = player.isDeadOrDying();
		if (dead && !wasDead) {
			announce(minecraft, player);
		}
		wasDead = dead;
	}

	private void announce(Minecraft minecraft, LocalPlayer player) {
		BlockPos pos = player.blockPosition();
		String coordinates = pos.getX() + " " + pos.getY() + " " + pos.getZ();
		MutableComponent message = Component.empty()
			.append(Component.literal("☠ ").withStyle(ChatFormatting.RED))
			.append(Component.translatable("pretupid.death.message", Component.literal(coordinates).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD)).withStyle(ChatFormatting.GRAY));
		if (showDimension.enabled()) {
			String dimension = minecraft.level.dimension().identifier().toString();
			message.append(Component.literal(" (" + dimension + ")").withStyle(ChatFormatting.DARK_GRAY));
		}
		if (copyButton.enabled()) {
			message.append(Component.literal(" "));
			message.append(Component.translatable("pretupid.death.copy").withStyle(style -> style
				.withColor(PretupidClient.get().settings().moduleText.resolve() & 0xFFFFFF)
				.withClickEvent(new ClickEvent.CopyToClipboard(coordinates))
				.withHoverEvent(new HoverEvent.ShowText(Component.translatable("pretupid.death.copy_hover")))));
		}
		Platform.addChatMessage(message);
	}
}

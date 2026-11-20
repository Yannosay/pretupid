package net.pretupid.client.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.pretupid.client.module.Module;
import net.pretupid.client.module.ModuleManager;

public final class ServerRulesGuide {
	public static final String CHANNEL = "pretupid:rules";

	private ServerRulesGuide() {
	}

	public static List<Component> lines(ModuleManager modules) {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable("pretupid.rules_guide.title").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
		lines.add(Component.translatable("pretupid.rules_guide.channel", copyable(CHANNEL)).withStyle(ChatFormatting.GRAY));
		JsonArray competitive = new JsonArray();
		MutableComponent all = Component.empty();
		boolean first = true;
		for (Module module : modules.all()) {
			if (!first) {
				all.append(Component.literal(", ").withStyle(ChatFormatting.DARK_GRAY));
			}
			first = false;
			boolean advantage = ServerCompatibility.COMPETITIVE_MODULES.contains(module.id());
			MutableComponent id = Component.literal(module.id()).withStyle(style -> style
				.withColor(advantage ? ChatFormatting.GOLD : ChatFormatting.WHITE)
				.withClickEvent(new ClickEvent.CopyToClipboard(module.id()))
				.withHoverEvent(new HoverEvent.ShowText(module.name().copy().append("\n").append(Component.translatable(
					advantage ? "pretupid.rules_guide.advantage" : "pretupid.rules_guide.copy")))));
			all.append(id);
			if (advantage) {
				competitive.add(module.id());
			}
		}
		lines.add(Component.translatable("pretupid.rules_guide.modules").withStyle(ChatFormatting.GRAY));
		lines.add(all);
		JsonObject example = new JsonObject();
		example.add("disable", competitive);
		lines.add(Component.translatable("pretupid.rules_guide.example", copyable(example.toString())).withStyle(ChatFormatting.GRAY));
		lines.add(Component.translatable("pretupid.rules_guide.note").withStyle(ChatFormatting.DARK_GRAY));
		return lines;
	}

	private static Component copyable(String text) {
		return Component.literal(text).withStyle(style -> style
			.withColor(ChatFormatting.YELLOW)
			.withUnderlined(true)
			.withClickEvent(new ClickEvent.CopyToClipboard(text))
			.withHoverEvent(new HoverEvent.ShowText(Component.translatable("pretupid.rules_guide.copy"))));
	}
}

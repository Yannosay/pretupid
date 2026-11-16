package net.pretupid.client.module.utility;

import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.pretupid.client.module.Category;
import net.pretupid.client.module.Module;
import net.pretupid.client.setting.TextSetting;

public final class NickHiderModule extends Module {
	private final TextSetting alias = add(new TextSetting("alias", "You", 16));

	public NickHiderModule() {
		super("nick_hider", Category.UTILITY, "name_tag", false);
	}

	public String alias() {
		String value = alias.get().trim();
		return value.isEmpty() ? "You" : value;
	}

	public String ownName() {
		return Minecraft.getInstance().getUser().getName();
	}

	public Component hide(Component message) {
		String name = ownName();
		if (message == null || name == null || name.isEmpty() || !message.getString().contains(name)) {
			return message;
		}
		String replacement = alias();
		MutableComponent result = Component.empty();
		message.visit((style, text) -> {
			result.append(Component.literal(text.replace(name, replacement)).withStyle(style));
			return Optional.empty();
		}, Style.EMPTY);
		return result;
	}
}

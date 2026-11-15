package net.pretupid.client.module.utility;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.pretupid.client.module.Category;
import net.pretupid.client.module.Module;
import net.pretupid.client.setting.ColorSetting;
import net.pretupid.client.setting.EnumSetting;

public final class ChatTimestampsModule extends Module {
	public enum TimestampFormat {
		HOURS_MINUTES("HH:mm"),
		HOURS_MINUTES_SECONDS("HH:mm:ss"),
		TWELVE_HOUR("h:mm a");

		private final DateTimeFormatter formatter;

		TimestampFormat(String pattern) {
			this.formatter = DateTimeFormatter.ofPattern(pattern, Locale.ROOT);
		}
	}

	private final EnumSetting<TimestampFormat> format = add(new EnumSetting<>("format", TimestampFormat.HOURS_MINUTES));
	private final ColorSetting color = add(new ColorSetting("color", 0xFF6C7584));

	public ChatTimestampsModule() {
		super("chat_timestamps", Category.UTILITY, "writable_book", false);
	}

	public Component decorate(Component message) {
		String stamp = "[" + LocalTime.now().format(format.get().formatter) + "] ";
		return Component.empty()
			.append(Component.literal(stamp).withStyle(style -> style.withColor(color.argb() & 0xFFFFFF)))
			.append(message);
	}
}

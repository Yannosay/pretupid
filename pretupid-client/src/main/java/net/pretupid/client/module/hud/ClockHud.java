package net.pretupid.client.module.hud;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.pretupid.client.hud.HudPosition;
import net.pretupid.client.hud.TextHudModule;
import net.pretupid.client.setting.BooleanSetting;
import net.pretupid.client.setting.EnumSetting;

public final class ClockHud extends TextHudModule {
	public enum ClockSource {
		REAL_TIME,
		GAME_TIME
	}

	private static final DateTimeFormatter H24 = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);
	private static final DateTimeFormatter H24_SECONDS = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT);
	private static final DateTimeFormatter H12 = DateTimeFormatter.ofPattern("h:mm a", Locale.ROOT);
	private static final DateTimeFormatter H12_SECONDS = DateTimeFormatter.ofPattern("h:mm:ss a", Locale.ROOT);

	private final EnumSetting<ClockSource> source = add(new EnumSetting<>("source", ClockSource.REAL_TIME));
	private final BooleanSetting twentyFourHour = add(new BooleanSetting("twenty_four_hour", true));
	private final BooleanSetting seconds = add(new BooleanSetting("seconds", false));
	private final BooleanSetting showDay = add(new BooleanSetting("show_day", true));

	public ClockHud() {
		super("clock", "clock_00", false, new HudPosition(HudPosition.Anchor.START, 4, HudPosition.Anchor.START, 76));
	}

	@Override
	protected String label() {
		return source.get() == ClockSource.GAME_TIME ? I18n.get("pretupid.hud.label.clock_game") : super.label();
	}

	@Override
	protected String value(Minecraft minecraft, boolean preview) {
		if (source.get() == ClockSource.REAL_TIME || minecraft.level == null) {
			DateTimeFormatter formatter = twentyFourHour.enabled() ? (seconds.enabled() ? H24_SECONDS : H24) : (seconds.enabled() ? H12_SECONDS : H12);
			return LocalTime.now().format(formatter);
		}
		long time = minecraft.level.getOverworldClockTime();
		long day = time / 24000L + 1L;
		long dayTicks = Math.floorMod(time, 24000L);
		int hour = (int) ((dayTicks / 1000L + 6L) % 24L);
		int minute = (int) ((dayTicks % 1000L) * 60L / 1000L);
		String clock;
		if (twentyFourHour.enabled()) {
			clock = String.format(Locale.ROOT, "%02d:%02d", hour, minute);
		} else {
			int displayHour = hour % 12 == 0 ? 12 : hour % 12;
			clock = String.format(Locale.ROOT, "%d:%02d %s", displayHour, minute, hour < 12 ? "AM" : "PM");
		}
		return showDay.enabled() ? I18n.get("pretupid.hud.day", day) + " " + clock : clock;
	}
}

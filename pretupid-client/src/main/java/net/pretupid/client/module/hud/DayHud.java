package net.pretupid.client.module.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.pretupid.client.hud.HudPosition;
import net.pretupid.client.hud.TextHudModule;
import net.pretupid.client.setting.BooleanSetting;

public final class DayHud extends TextHudModule {
	private static final long TICKS_PER_DAY = 24_000L;

	private final BooleanSetting showTime = add(new BooleanSetting("show_time", true));
	private final BooleanSetting twelveHour = add(new BooleanSetting("twelve_hour", false));

	public DayHud() {
		super("day", "clock_00", false, new HudPosition(HudPosition.Anchor.START, 4, HudPosition.Anchor.END, 64));
	}

	@Override
	protected String value(Minecraft minecraft, boolean preview) {
		long time = minecraft.level == null ? 6_000L : minecraft.level.getDefaultClockTime();
		long day = Math.floorDiv(time, TICKS_PER_DAY) + 1;
		String dayText = I18n.get("pretupid.hud.day.value", day);
		if (!showTime.enabled()) {
			return dayText;
		}
		return dayText + " · " + clock(Math.floorMod(time, TICKS_PER_DAY));
	}

	private String clock(long ticksIntoDay) {
		int hours = (int) ((ticksIntoDay / 1000L + 6L) % 24L);
		int minutes = (int) (ticksIntoDay % 1000L * 60L / 1000L);
		if (!twelveHour.enabled()) {
			return String.format("%02d:%02d", hours, minutes);
		}
		int displayHour = hours % 12 == 0 ? 12 : hours % 12;
		return String.format("%d:%02d %s", displayHour, minutes, hours < 12 ? "AM" : "PM");
	}
}

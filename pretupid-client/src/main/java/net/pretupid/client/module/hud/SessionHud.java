package net.pretupid.client.module.hud;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.pretupid.client.hud.HudPosition;
import net.pretupid.client.hud.TextHudModule;

public final class SessionHud extends TextHudModule {
	private long joinedAt;

	public SessionHud() {
		super("session", "hopper", false, new HudPosition(HudPosition.Anchor.START, 4, HudPosition.Anchor.START, 148));
	}

	public void onWorldJoined() {
		joinedAt = System.currentTimeMillis();
	}

	@Override
	protected String value(Minecraft minecraft, boolean preview) {
		long elapsed = joinedAt == 0L ? 0L : (System.currentTimeMillis() - joinedAt) / 1000L;
		long hours = elapsed / 3600L;
		long minutes = (elapsed / 60L) % 60L;
		long seconds = elapsed % 60L;
		return hours > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds) : String.format(Locale.ROOT, "%02d:%02d", minutes, seconds);
	}
}

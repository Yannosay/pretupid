package net.pretupid.client.hud;

import net.pretupid.client.platform.Gfx;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;

public interface OverlayRenderer {
	void renderOverlay(Gfx graphics, Minecraft minecraft, DeltaTracker deltaTracker);
}

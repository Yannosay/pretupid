package net.pretupid.client.hud;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public interface OverlayRenderer {
	void renderOverlay(GuiGraphicsExtractor graphics, Minecraft minecraft, DeltaTracker deltaTracker);
}

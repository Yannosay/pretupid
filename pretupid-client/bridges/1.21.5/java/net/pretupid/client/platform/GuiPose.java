package net.pretupid.client.platform;

import com.mojang.blaze3d.vertex.PoseStack;

public final class GuiPose {
	private final PoseStack stack;

	GuiPose(PoseStack stack) {
		this.stack = stack;
	}

	public GuiPose pushMatrix() {
		stack.pushPose();
		return this;
	}

	public GuiPose popMatrix() {
		stack.popPose();
		return this;
	}

	public GuiPose translate(float x, float y) {
		stack.translate(x, y, 0f);
		return this;
	}

	public GuiPose scale(float x, float y) {
		stack.scale(x, y, 1f);
		return this;
	}
}

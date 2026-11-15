package net.pretupid.client.module.visual;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.pretupid.client.module.Category;
import net.pretupid.client.module.Module;
import net.pretupid.client.setting.BooleanSetting;

public final class FreelookModule extends Module {
	private final BooleanSetting hold = add(new BooleanSetting("hold", true));
	private final BooleanSetting thirdPerson = add(new BooleanSetting("third_person", true));
	private final BooleanSetting invertPitch = add(new BooleanSetting("invert_pitch", false));
	private boolean active;
	private float yaw;
	private float pitch;
	private CameraType previous;

	public FreelookModule() {
		super("freelook", Category.VISUAL, "spider_eye", false);
	}

	public boolean active() {
		return active;
	}

	public boolean holdMode() {
		return hold.enabled();
	}

	public float yaw() {
		return yaw;
	}

	public float pitch() {
		return pitch;
	}

	public void setHeld(Minecraft minecraft, boolean down) {
		if (down && !active) {
			start(minecraft);
		} else if (!down && active) {
			stop(minecraft);
		}
	}

	public void toggleActive(Minecraft minecraft) {
		setHeld(minecraft, !active);
	}

	public void turn(double dx, double dy) {
		yaw += (float) dx * 0.15F;
		pitch = Mth.clamp(pitch + (float) dy * 0.15F * (invertPitch.enabled() ? -1F : 1F), -90F, 90F);
	}

	private void start(Minecraft minecraft) {
		if (!isEnabled() || minecraft.player == null) {
			return;
		}
		yaw = minecraft.player.getYRot();
		pitch = minecraft.player.getXRot();
		previous = minecraft.options.getCameraType();
		if (thirdPerson.enabled() && previous.isFirstPerson()) {
			minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
		}
		active = true;
	}

	private void stop(Minecraft minecraft) {
		active = false;
		if (previous != null && minecraft.options.getCameraType() != previous) {
			minecraft.options.setCameraType(previous);
		}
		previous = null;
	}

	@Override
	public void onTick(Minecraft minecraft) {
		if (active && minecraft.player == null) {
			stop(minecraft);
		}
	}

	@Override
	protected void onDisable() {
		if (active) {
			stop(Minecraft.getInstance());
		}
	}
}

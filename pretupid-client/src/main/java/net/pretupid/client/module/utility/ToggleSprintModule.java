package net.pretupid.client.module.utility;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.pretupid.client.hud.HudPosition;
import net.pretupid.client.hud.TextHudModule;
import net.pretupid.client.module.Category;
import net.pretupid.client.setting.BooleanSetting;

public final class ToggleSprintModule extends TextHudModule {
	private final BooleanSetting toggleSprint = add(new BooleanSetting("toggle_sprint", true));
	private final BooleanSetting toggleSneak = add(new BooleanSetting("toggle_sneak", false));
	private final BooleanSetting showStatus = add(new BooleanSetting("show_status", true));
	private Boolean previousToggleSprint;
	private Boolean previousToggleCrouch;

	public ToggleSprintModule() {
		super("toggle_sprint", Category.UTILITY, "sugar", false, new HudPosition(HudPosition.Anchor.START, 4, HudPosition.Anchor.END, 52));
		toggleSprint.onChange(value -> apply());
		toggleSneak.onChange(value -> apply());
	}

	@Override
	protected boolean backgroundByDefault() {
		return false;
	}

	@Override
	protected void onEnable() {
		apply();
	}

	@Override
	protected void onDisable() {
		if (previousToggleSprint == null && previousToggleCrouch == null) {
			return;
		}
		Options options = Minecraft.getInstance().options;
		if (previousToggleSprint != null) {
			options.toggleSprint().set(previousToggleSprint);
		}
		if (previousToggleCrouch != null) {
			options.toggleCrouch().set(previousToggleCrouch);
		}
		previousToggleSprint = null;
		previousToggleCrouch = null;
		options.save();
	}

	@Override
	public void onProfileApplied() {
		apply();
	}

	private void apply() {
		if (!isEnabled()) {
			return;
		}
		Options options = Minecraft.getInstance().options;
		if (previousToggleSprint == null) {
			previousToggleSprint = options.toggleSprint().get();
			previousToggleCrouch = options.toggleCrouch().get();
		}
		options.toggleSprint().set(toggleSprint.enabled());
		options.toggleCrouch().set(toggleSneak.enabled());
		options.save();
	}

	@Override
	public boolean visible(Minecraft minecraft) {
		return showStatus.enabled() && super.visible(minecraft) && !state(minecraft, false).isEmpty();
	}

	@Override
	protected String label() {
		return "";
	}

	@Override
	protected String value(Minecraft minecraft, boolean preview) {
		String state = state(minecraft, preview);
		return state.isEmpty() ? I18n.get("pretupid.hud.sprint.sprinting_toggled") : state;
	}

	private String state(Minecraft minecraft, boolean preview) {
		LocalPlayer player = minecraft.player;
		Options options = minecraft.options;
		if (player == null) {
			return preview ? I18n.get("pretupid.hud.sprint.sprinting_toggled") : "";
		}
		if (player.getAbilities().flying) {
			return I18n.get("pretupid.hud.sprint.flying");
		}
		boolean sneakToggled = options.toggleCrouch().get() && options.keyShift.isDown();
		if (player.isCrouching()) {
			return I18n.get(sneakToggled ? "pretupid.hud.sprint.sneaking_toggled" : "pretupid.hud.sprint.sneaking");
		}
		boolean sprintToggled = options.toggleSprint().get() && options.keySprint.isDown();
		if (sprintToggled) {
			return I18n.get("pretupid.hud.sprint.sprinting_toggled");
		}
		if (player.isSprinting()) {
			return I18n.get("pretupid.hud.sprint.sprinting");
		}
		return preview ? I18n.get("pretupid.hud.sprint.sprinting_toggled") : "";
	}
}

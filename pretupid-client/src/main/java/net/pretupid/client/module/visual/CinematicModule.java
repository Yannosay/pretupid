package net.pretupid.client.module.visual;

import net.pretupid.client.platform.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.cinematic.CinematicDirector;
import net.pretupid.client.module.Category;
import net.pretupid.client.module.Module;
import net.pretupid.client.setting.BooleanSetting;
import net.pretupid.client.setting.NumberSetting;
import net.pretupid.client.ui.Icon;
import net.pretupid.client.ui.Theme;
import net.pretupid.client.ui.screen.CinematicEditorScreen;

public final class CinematicModule extends Module {
	private final NumberSetting spacing = add(new NumberSetting("spacing", 2.0, 0.25, 10.0, 0.25, "s"));
	private final BooleanSetting letterbox = add(new BooleanSetting("letterbox", true));
	private final BooleanSetting hideHud = add(new BooleanSetting("hide_hud", true));
	private final BooleanSetting showPath = add(new BooleanSetting("show_path", true));

	public CinematicModule() {
		super("cinematic", Category.VISUAL, "spyglass", false);
	}

	public double spacing() {
		return spacing.get();
	}

	public boolean letterbox() {
		return letterbox.enabled();
	}

	public BooleanSetting letterboxSetting() {
		return letterbox;
	}

	public boolean hidesHud() {
		return hideHud.enabled();
	}

	public boolean showsPath() {
		return showPath.enabled();
	}

	public void openEditor(Minecraft minecraft) {
		if (minecraft.player == null) {
			return;
		}
		if (!isEnabled()) {
			notify(isPaused() ? pauseReason() : Component.translatable("pretupid.cinematic.turn_on"), isPaused());
			return;
		}
		Platform.setScreen(new CinematicEditorScreen());
	}

	public void addKeyframe(Minecraft minecraft) {
		if (minecraft.player == null) {
			return;
		}
		if (!isEnabled()) {
			notify(isPaused() ? pauseReason() : Component.translatable("pretupid.cinematic.turn_on"), isPaused());
			return;
		}
		CinematicDirector director = PretupidClient.get().cinematic();
		int index = director.addKeyframeAtCamera(minecraft, spacing());
		if (index < 0) {
			notify(Component.translatable("pretupid.cinematic.full"), true);
			return;
		}
		int count = director.current().keyframes().size();
		notify(Component.translatable("pretupid.cinematic.added", count, String.format(java.util.Locale.ROOT, "%.2f", director.current().keyframes().get(index).time())), false);
	}

	private void notify(Component text, boolean warning) {
		PretupidClient.get().notifications().push(Component.translatable("pretupid.module.cinematic.name"), text, Icon.item("spyglass"), null,
			warning ? Theme.WARNING : Theme.accent(), 3000L);
	}

	@Override
	protected void onDisable() {
		PretupidClient.get().cinematic().stop();
	}
}

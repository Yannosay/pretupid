package net.pretupid.client.module.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.hud.HudModule;
import net.pretupid.client.hud.HudPosition;
import net.pretupid.client.module.Category;
import net.pretupid.client.module.ManagedModule;
import net.pretupid.client.music.MusicController;
import net.pretupid.client.music.MusicTrack;
import net.pretupid.client.platform.Gfx;
import net.pretupid.client.platform.Platform;
import net.pretupid.client.setting.BooleanSetting;
import net.pretupid.client.setting.ColorSetting;
import net.pretupid.client.setting.KeySetting;
import net.pretupid.client.setting.NumberSetting;
import net.pretupid.client.ui.Draw;
import net.pretupid.client.ui.Theme;
import net.pretupid.client.ui.screen.MusicScreen;
import net.pretupid.client.util.Colors;

public final class NowPlayingModule extends HudModule implements ManagedModule {
	private static final int WIDTH = 150;
	private static final int HEIGHT = 26;
	private static final int BARS = 3;
	private static final long FADE_IN_MS = 250L;
	private static final long FADE_OUT_MS = 500L;
	private static final MusicTrack PREVIEW = new MusicTrack(Identifier.withDefaultNamespace("music/game/sweden"), "Sweden", "C418");

	private final NumberSetting displaySeconds = add(new NumberSetting("display_seconds", 6, 2, 30, 1, " s"));
	private final BooleanSetting alwaysShow = add(new BooleanSetting("always_show", false));
	private final BooleanSetting showWhenMuted = add(new BooleanSetting("show_when_muted", false));
	private final KeySetting skipKey = add(new KeySetting("skip_key", KeySetting.UNBOUND));
	private final ColorSetting titleColor = add(new ColorSetting("title_color", 0xFFFFFFFF).followingModuleText());
	private final ColorSetting artistColor = add(new ColorSetting("artist_color", 0xFFA3ABB9));

	private MusicTrack current;
	private long shownAt;
	private boolean skipHeld;

	public NowPlayingModule() {
		super("now_playing", Category.HUD, "jukebox", false, new HudPosition(HudPosition.Anchor.CENTER, 0, HudPosition.Anchor.START, 48));
		displaySeconds.visibleWhen(() -> !alwaysShow.enabled());
	}

	@Override
	public void onTick(Minecraft minecraft) {
		MusicController music = PretupidClient.get().music();
		MusicTrack playing = music.current(minecraft);
		if (playing == null ? current != null : !playing.equals(current)) {
			current = playing;
			shownAt = System.currentTimeMillis();
		}
		boolean down = skipKey.isBound() && Platform.screen() == null && Platform.isKeyDown(skipKey.get());
		if (down && !skipHeld && current != null) {
			music.skip(minecraft);
		}
		skipHeld = down;
	}

	@Override
	protected void onDisable() {
		Minecraft minecraft = Minecraft.getInstance();
		if (PretupidClient.getOrNull() != null) {
			PretupidClient.get().music().useDynamic(minecraft);
		}
		current = null;
		skipHeld = false;
	}

	@Override
	public Component manageLabel() {
		return Component.translatable("pretupid.music.open");
	}

	@Override
	public boolean canManage(Minecraft minecraft) {
		return true;
	}

	@Override
	public Screen createManager(Screen parent) {
		return new MusicScreen(parent);
	}

	private float opacity() {
		if (alwaysShow.enabled()) {
			return 1f;
		}
		long age = System.currentTimeMillis() - shownAt;
		long duration = displaySeconds.intValue() * 1000L;
		if (age >= duration) {
			return 0f;
		}
		float in = Math.min(1f, age / (float) FADE_IN_MS);
		float out = Math.min(1f, (duration - age) / (float) FADE_OUT_MS);
		return Math.min(in, out);
	}

	@Override
	public boolean visible(Minecraft minecraft) {
		return super.visible(minecraft) && current != null && (showWhenMuted.enabled() || MusicController.audible(minecraft)) && opacity() > 0.02f;
	}

	@Override
	public int width(Font font, Minecraft minecraft, boolean preview) {
		return WIDTH;
	}

	@Override
	public int height(Font font, Minecraft minecraft, boolean preview) {
		return HEIGHT;
	}

	@Override
	public void render(Gfx graphics, Font font, Minecraft minecraft, boolean preview) {
		MusicTrack track = preview && current == null ? PREVIEW : current;
		if (track == null) {
			return;
		}
		float alpha = preview ? 1f : opacity();
		if (background.enabled()) {
			Draw.chamfer(graphics, 0, 0, WIDTH, HEIGHT, Colors.multiplyAlpha(backgroundColor.resolve(), alpha));
		}
		long now = System.currentTimeMillis();
		int accent = Colors.multiplyAlpha(Theme.accent(), alpha);
		for (int bar = 0; bar < BARS; bar++) {
			float wave = 0.5f + 0.5f * (float) Math.sin(now / (190.0 + bar * 45.0) + bar * 1.9);
			int height = 4 + Math.round(wave * 12f);
			Draw.rect(graphics, 6 + bar * 4, HEIGHT / 2 + 8 - height, 3, height, accent);
		}
		int textX = 6 + BARS * 4 + 4;
		int textWidth = WIDTH - textX - 5;
		boolean shadow = textShadow.enabled();
		graphics.text(font, Draw.ellipsize(font, track.title(), textWidth), textX, 4, Colors.multiplyAlpha(titleColor.resolve(), alpha), shadow);
		graphics.text(font, Draw.ellipsize(font, track.artist(), textWidth), textX, 14, Colors.multiplyAlpha(artistColor.resolve(), alpha), shadow);
	}
}

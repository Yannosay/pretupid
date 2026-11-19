package net.pretupid.client.hud;

import net.pretupid.client.platform.Gfx;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.pretupid.client.module.Category;
import net.pretupid.client.module.Module;
import net.pretupid.client.setting.BooleanSetting;
import net.pretupid.client.setting.ColorSetting;
import net.pretupid.client.setting.NumberSetting;
import net.pretupid.client.setting.Setting;
import net.pretupid.client.ui.Draw;

public abstract class HudModule extends Module {
	public static final int PADDING = 4;

	private final HudPosition position;
	protected final NumberSetting scale;
	protected final BooleanSetting background;
	protected final ColorSetting backgroundColor;
	protected final BooleanSetting textShadow;

	protected HudModule(String id, Category category, String icon, boolean enabledByDefault, HudPosition position) {
		super(id, category, icon, enabledByDefault);
		this.position = position;
		this.scale = shared(new NumberSetting("scale", 1.0, 0.5, 3.0, 0.05, "x"));
		this.background = shared(new BooleanSetting("background", backgroundByDefault()));
		this.backgroundColor = shared(new ColorSetting("background_color", 0x6A0B0E14, true));
		this.textShadow = shared(new BooleanSetting("text_shadow", true));
	}

	protected HudModule(String id, String icon, boolean enabledByDefault, HudPosition position) {
		this(id, Category.HUD, icon, enabledByDefault, position);
	}

	protected boolean backgroundByDefault() {
		return true;
	}

	protected final <S extends Setting<?>> S shared(S setting) {
		add(setting);
		setting.bindTranslation("pretupid.hud");
		return setting;
	}

	public final HudPosition position() {
		return position;
	}

	public final float scale() {
		return scale.floatValue();
	}

	public final void setScale(double value) {
		scale.set(value);
	}

	public final double minScale() {
		return scale.min();
	}

	public final double maxScale() {
		return scale.max();
	}

	public abstract int width(Font font, Minecraft minecraft, boolean preview);

	public abstract int height(Font font, Minecraft minecraft, boolean preview);

	public abstract void render(Gfx graphics, Font font, Minecraft minecraft, boolean preview);

	public boolean visible(Minecraft minecraft) {
		return minecraft.player != null;
	}

	protected final void drawBackground(Gfx graphics, int width, int height) {
		if (background.enabled()) {
			Draw.chamfer(graphics, 0, 0, width, height, backgroundColor.resolve());
		}
	}

	protected final int padding() {
		return background.enabled() ? PADDING : 0;
	}

	@Override
	protected void onReset() {
		position.reset();
	}

	@Override
	public JsonObject save() {
		JsonObject object = super.save();
		object.add("position", position.toJson());
		return object;
	}

	@Override
	public void load(JsonObject object) {
		super.load(object);
		position.fromJson(object.get("position"));
	}

	@Override
	public void loadDefaults() {
		super.loadDefaults();
		position.reset();
	}
}

package net.pretupid.client.module.performance;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ParticleStatus;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.module.Category;
import net.pretupid.client.module.Module;
import net.pretupid.client.setting.BooleanSetting;

public final class FpsBoostModule extends Module {
	private final BooleanSetting clouds = add(new BooleanSetting("clouds", true));
	private final BooleanSetting particles = add(new BooleanSetting("particles", true));
	private final BooleanSetting shadows = add(new BooleanSetting("shadows", true));
	private final BooleanSetting biomeBlend = add(new BooleanSetting("biome_blend", true));
	private final BooleanSetting entityDistance = add(new BooleanSetting("entity_distance", false));
	private final BooleanSetting viewBobbing = add(new BooleanSetting("view_bobbing", false));
	private JsonObject backup = new JsonObject();

	public FpsBoostModule() {
		super("fps_boost", Category.PERFORMANCE, "firework_rocket", false);
	}

	@Override
	protected void onEnable() {
		Minecraft minecraft = Minecraft.getInstance();
		Options options = minecraft.options;
		backup = new JsonObject();
		int applied = 0;
		if (clouds.enabled()) {
			backup.addProperty("clouds", options.cloudStatus().get().name());
			options.cloudStatus().set(CloudStatus.OFF);
			applied++;
		}
		if (particles.enabled()) {
			backup.addProperty("particles", options.particles().get().name());
			options.particles().set(ParticleStatus.DECREASED);
			applied++;
		}
		if (shadows.enabled()) {
			backup.addProperty("shadows", options.entityShadows().get());
			options.entityShadows().set(false);
			applied++;
		}
		if (biomeBlend.enabled()) {
			backup.addProperty("biomeBlend", options.biomeBlendRadius().get());
			options.biomeBlendRadius().set(0);
			applied++;
		}
		if (entityDistance.enabled()) {
			backup.addProperty("entityDistance", options.entityDistanceScaling().get());
			options.entityDistanceScaling().set(Math.min(options.entityDistanceScaling().get(), 0.75));
			applied++;
		}
		if (viewBobbing.enabled()) {
			backup.addProperty("viewBobbing", options.bobView().get());
			options.bobView().set(false);
			applied++;
		}
		options.save();
		PretupidClient.get().notifications().info(name(), Component.translatable("pretupid.notification.fps_boost_applied", applied), icon());
	}

	@Override
	protected void onDisable() {
		Minecraft minecraft = Minecraft.getInstance();
		Options options = minecraft.options;
		restoreEnum(backup.get("clouds"), options.cloudStatus(), CloudStatus.class);
		restoreEnum(backup.get("particles"), options.particles(), ParticleStatus.class);
		restoreBoolean(backup.get("shadows"), options.entityShadows());
		restoreBoolean(backup.get("viewBobbing"), options.bobView());
		JsonElement blend = backup.get("biomeBlend");
		if (blend != null && blend.isJsonPrimitive()) {
			options.biomeBlendRadius().set(Math.clamp(blend.getAsInt(), 0, 7));
		}
		JsonElement distance = backup.get("entityDistance");
		if (distance != null && distance.isJsonPrimitive()) {
			options.entityDistanceScaling().set(Math.clamp(distance.getAsDouble(), 0.5, 5.0));
		}
		backup = new JsonObject();
		options.save();
		PretupidClient.get().notifications().info(name(), Component.translatable("pretupid.notification.fps_boost_restored"), icon());
	}

	@Override
	public void onProfileApplied() {
		if (isEnabled() && backup.isEmpty()) {
			onEnable();
		} else if (!isEnabled() && !backup.isEmpty()) {
			onDisable();
		}
	}

	private static <E extends Enum<E>> void restoreEnum(JsonElement element, OptionInstance<E> option, Class<E> type) {
		if (element == null || !element.isJsonPrimitive()) {
			return;
		}
		for (E constant : type.getEnumConstants()) {
			if (constant.name().equals(element.getAsString())) {
				option.set(constant);
				return;
			}
		}
	}

	private static void restoreBoolean(JsonElement element, OptionInstance<Boolean> option) {
		if (element != null && element.isJsonPrimitive()) {
			option.set(element.getAsBoolean());
		}
	}

	@Override
	public JsonObject save() {
		JsonObject object = super.save();
		object.add("backup", backup.deepCopy());
		return object;
	}

	@Override
	public void load(JsonObject object) {
		super.load(object);
		JsonElement element = object.get("backup");
		backup = element != null && element.isJsonObject() ? element.getAsJsonObject().deepCopy() : new JsonObject();
	}

	@Override
	public void loadDefaults() {
		super.loadDefaults();
		backup = new JsonObject();
	}
}

package net.pretupid.client.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.pretupid.client.nametag.NameAnimation;
import net.pretupid.client.nametag.NameEffect;
import net.pretupid.client.nametag.NametagStyle;
import net.pretupid.client.setting.BooleanSetting;
import net.pretupid.client.setting.ColorSetting;
import net.pretupid.client.setting.EnumSetting;
import net.pretupid.client.setting.NumberSetting;
import net.pretupid.client.setting.Setting;
import net.pretupid.client.setting.TextSetting;

public final class ClientSettings {
	private final List<Setting<?>> settings = new ArrayList<>();
	private final List<Setting<?>> view = Collections.unmodifiableList(settings);

	public final ColorSetting accent = add(new ColorSetting("accent", 0xFF4A9EFF));
	public final BooleanSetting animations = add(new BooleanSetting("animations", true));
	public final NumberSetting animationSpeed = add(new NumberSetting("animation_speed", 1.0, 0.5, 2.0, 0.1, "x"));
	public final BooleanSetting uiSounds = add(new BooleanSetting("ui_sounds", true));
	public final BooleanSetting menuBlur = add(new BooleanSetting("menu_blur", true));
	public final BooleanSetting toggleToasts = add(new BooleanSetting("toggle_toasts", true));
	public final BooleanSetting titleScreenButton = add(new BooleanSetting("title_screen_button", true));
	public final BooleanSetting pauseScreenButton = add(new BooleanSetting("pause_screen_button", true));
	public final BooleanSetting hideHudInDebug = add(new BooleanSetting("hide_hud_in_debug", true));
	public final BooleanSetting sharePresence = add(new BooleanSetting("share_presence", true));
	public final BooleanSetting showOtherBadges = add(new BooleanSetting("show_other_badges", true));
	public final BooleanSetting tabListBadges = add(new BooleanSetting("tab_list_badges", true));
	public final BooleanSetting autoProfileSwitch = add(new BooleanSetting("auto_profile_switch", true));
	public final BooleanSetting enhancedTitleScreen = add(new BooleanSetting("enhanced_title_screen", true));
	public final BooleanSetting titleScreenPanels = add(new BooleanSetting("title_screen_panels", true));
	public final BooleanSetting newsButton = add(new BooleanSetting("news_button", true));
	public final BooleanSetting updateChecks = add(new BooleanSetting("update_checks", true));
	public final BooleanSetting showCosmetics = add(new BooleanSetting("show_cosmetics", true));
	public final BooleanSetting showTrails = add(new BooleanSetting("show_trails", true));
	public final BooleanSetting emoteThirdPerson = add(new BooleanSetting("emote_third_person", true));
	public final BooleanSetting allowFriendRequests = add(new BooleanSetting("allow_friend_requests", true));
	public final BooleanSetting shareServerWithFriends = add(new BooleanSetting("share_server_with_friends", false));
	public final BooleanSetting friendNotifications = add(new BooleanSetting("friend_notifications", true));
	public final BooleanSetting nametagBadge = add(new BooleanSetting("nametag_badge", true));
	public final EnumSetting<NameAnimation> nametagAnimation = add(new EnumSetting<>("nametag_animation", NameAnimation.NONE));
	public final EnumSetting<NameEffect> nametagEffect = add(new EnumSetting<>("nametag_effect", NameEffect.NONE));
	public final ColorSetting nametagPrimary = add(new ColorSetting("nametag_primary", 0xFF8FC4FF));
	public final ColorSetting nametagSecondary = add(new ColorSetting("nametag_secondary", 0xFFC4A8FF));
	public final NumberSetting nametagSpeed = add(new NumberSetting("nametag_speed", 1.0, 0.1, 3.0, 0.1, "x"));
	public final BooleanSetting nametagBold = add(new BooleanSetting("nametag_bold", false));
	public final TextSetting cape = add(new TextSetting("cape", "", 48));
	public final TextSetting trail = add(new TextSetting("trail", "", 32));
	public final TextSetting hat = add(new TextSetting("hat", "", 32));
	public final TextSetting boots = add(new TextSetting("boots", "", 32));
	public final TextSetting pet = add(new TextSetting("pet", "", 32));
	public final TextSetting emoteWheel = add(new TextSetting("emote_wheel", "", 300));
	public final TextSetting emoteKeys = add(new TextSetting("emote_keys", "", 600));
	public final BooleanSetting emoteWheelHold = add(new BooleanSetting("emote_wheel_hold", true));
	public final TextSetting newsSeen = add(new TextSetting("news_seen", "0", 20));
	public final TextSetting skippedUpdate = add(new TextSetting("skipped_update", "", 40));
	public final TextSetting lastServerAddress = add(new TextSetting("last_server_address", "", 253));
	public final TextSetting lastServerName = add(new TextSetting("last_server_name", "", 64));
	public final TextSetting restrictedServers = add(new TextSetting("restricted_servers", "", 4000));
	public final BooleanSetting serverCompatibility = add(new BooleanSetting("server_compatibility", true));
	private NametagStyle cachedStyle;

	public ClientSettings() {
		net.pretupid.client.setting.SettingChangeBus.subscribe(setting -> {
			if (setting == nametagBadge || setting == nametagAnimation || setting == nametagEffect || setting == nametagPrimary
				|| setting == nametagSecondary || setting == nametagSpeed || setting == nametagBold) {
				cachedStyle = null;
			}
		});
		nametagSecondary.visibleWhen(() -> nametagAnimation.get().usesSecondary());
		nametagPrimary.visibleWhen(() -> nametagAnimation.get().usesPrimary());
		nametagSpeed.visibleWhen(() -> nametagAnimation.get().animates() || nametagEffect.get().moves());
	}

	public NametagStyle nametagStyle() {
		NametagStyle style = cachedStyle;
		if (style == null) {
			style = new NametagStyle(nametagBadge.enabled(), nametagAnimation.get(), nametagEffect.get(), nametagPrimary.argb(),
				nametagSecondary.argb(), nametagSpeed.floatValue(), nametagBold.enabled());
			cachedStyle = style;
		}
		return style;
	}

	private <S extends Setting<?>> S add(S setting) {
		setting.bindTranslation("pretupid.client_setting");
		settings.add(setting);
		return setting;
	}

	public List<Setting<?>> all() {
		return view;
	}

	public JsonObject save() {
		JsonObject object = new JsonObject();
		for (Setting<?> setting : settings) {
			object.add(setting.id(), setting.toJson());
		}
		return object;
	}

	public void load(JsonObject object) {
		for (Setting<?> setting : settings) {
			JsonElement element = object.get(setting.id());
			if (element != null) {
				setting.fromJson(element);
			}
		}
	}
}

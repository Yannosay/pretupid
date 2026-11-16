package net.pretupid.client.module.utility;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.config.JsonFiles;
import net.pretupid.client.hud.HudModule;
import net.pretupid.client.hud.HudPosition;
import net.pretupid.client.module.Category;
import net.pretupid.client.setting.BooleanSetting;
import net.pretupid.client.setting.ColorSetting;
import net.pretupid.client.setting.KeySetting;
import net.pretupid.client.setting.NumberSetting;

public final class WaypointsModule extends HudModule {
	public static final int MAX_NAME_LENGTH = 24;
	private static final int MAX_PER_WORLD = 64;
	private static final int ROW_HEIGHT = 11;
	private static final String DEATH_NAME = "Death";
	private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

	public record Waypoint(String name, int x, int y, int z, String dimension) {
	}

	private final NumberSetting maxShown = add(new NumberSetting("max_shown", 3, 1, 8, 1));
	private final BooleanSetting deathWaypoint = add(new BooleanSetting("death_waypoint", true));
	private final BooleanSetting showCoordinates = add(new BooleanSetting("show_coordinates", false));
	private final KeySetting addKey = add(new KeySetting("add_key", KeySetting.UNBOUND));
	private final ColorSetting nameColor = add(new ColorSetting("name_color", 0xFF8FC4FF));
	private final ColorSetting distanceColor = add(new ColorSetting("distance_color", 0xFFFFFFFF));

	private final Path file = FabricLoader.getInstance().getConfigDir().resolve(PretupidClient.MOD_ID).resolve("waypoints.json");
	private final Map<String, List<Waypoint>> worlds = new LinkedHashMap<>();
	private boolean loaded;
	private boolean wasDead;
	private boolean addKeyHeld;

	public WaypointsModule() {
		super("waypoints", Category.UTILITY, "compass_00", false, new HudPosition(HudPosition.Anchor.END, 4, HudPosition.Anchor.START, 40));
	}

	@Override
	public void onTick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null) {
			wasDead = false;
			addKeyHeld = false;
			return;
		}
		boolean dead = player.isDeadOrDying();
		if (dead && !wasDead && deathWaypoint.enabled()) {
			put(minecraft, DEATH_NAME);
		}
		wasDead = dead;
		boolean keyDown = addKey.isBound() && minecraft.gui.screen() == null && InputConstants.isKeyDown(addKey.get());
		if (keyDown && !addKeyHeld) {
			Component result = add(minecraft, nextName(minecraft));
			minecraft.gui.hud.getChat().addClientSystemMessage(result);
		}
		addKeyHeld = keyDown;
	}

	public Component add(Minecraft minecraft, String name) {
		String clean = sanitize(name);
		if (clean.isEmpty()) {
			return Component.translatable("pretupid.waypoints.invalid_name").withStyle(ChatFormatting.RED);
		}
		if (minecraft.player == null || minecraft.level == null) {
			return Component.translatable("pretupid.waypoints.no_world").withStyle(ChatFormatting.RED);
		}
		List<Waypoint> list = current(minecraft);
		boolean replacing = list.stream().anyMatch(waypoint -> waypoint.name().equalsIgnoreCase(clean));
		if (!replacing && list.size() >= MAX_PER_WORLD) {
			return Component.translatable("pretupid.waypoints.full", MAX_PER_WORLD).withStyle(ChatFormatting.RED);
		}
		Waypoint waypoint = put(minecraft, clean);
		return Component.translatable("pretupid.waypoints.added", Component.literal(waypoint.name()).withStyle(ChatFormatting.AQUA),
			waypoint.x() + " " + waypoint.y() + " " + waypoint.z()).withStyle(ChatFormatting.GRAY);
	}

	public Component remove(Minecraft minecraft, String name) {
		if (minecraft.level == null) {
			return Component.translatable("pretupid.waypoints.no_world").withStyle(ChatFormatting.RED);
		}
		boolean removed = current(minecraft).removeIf(waypoint -> waypoint.name().equalsIgnoreCase(name.trim()));
		if (!removed) {
			return Component.translatable("pretupid.waypoints.not_found", name).withStyle(ChatFormatting.RED);
		}
		persist();
		return Component.translatable("pretupid.waypoints.removed", Component.literal(name).withStyle(ChatFormatting.AQUA)).withStyle(ChatFormatting.GRAY);
	}

	public Component list(Minecraft minecraft) {
		if (minecraft.level == null) {
			return Component.translatable("pretupid.waypoints.no_world").withStyle(ChatFormatting.RED);
		}
		List<Waypoint> list = current(minecraft);
		if (list.isEmpty()) {
			return Component.translatable("pretupid.waypoints.empty").withStyle(ChatFormatting.GRAY);
		}
		var message = Component.translatable("pretupid.waypoints.list_header", list.size()).withStyle(ChatFormatting.GRAY);
		for (Waypoint waypoint : list) {
			message.append(Component.literal("\n • ").withStyle(ChatFormatting.DARK_GRAY))
				.append(Component.literal(waypoint.name()).withStyle(ChatFormatting.AQUA))
				.append(Component.literal(" " + waypoint.x() + " " + waypoint.y() + " " + waypoint.z() + " ").withStyle(ChatFormatting.WHITE))
				.append(Component.literal(shortDimension(waypoint.dimension())).withStyle(ChatFormatting.DARK_GRAY));
		}
		return message;
	}

	public List<String> names(Minecraft minecraft) {
		if (minecraft.level == null) {
			return List.of();
		}
		return current(minecraft).stream().map(Waypoint::name).toList();
	}

	private Waypoint put(Minecraft minecraft, String name) {
		LocalPlayer player = minecraft.player;
		Waypoint waypoint = new Waypoint(name, player.getBlockX(), player.getBlockY(), player.getBlockZ(), minecraft.level.dimension().identifier().toString());
		List<Waypoint> list = current(minecraft);
		list.removeIf(existing -> existing.name().equalsIgnoreCase(name));
		list.add(0, waypoint);
		persist();
		return waypoint;
	}

	private String nextName(Minecraft minecraft) {
		List<Waypoint> list = current(minecraft);
		for (int index = list.size() + 1; ; index++) {
			String candidate = I18n.get("pretupid.waypoints.default_name", index);
			if (list.stream().noneMatch(waypoint -> waypoint.name().equalsIgnoreCase(candidate))) {
				return candidate;
			}
		}
	}

	private static String sanitize(String name) {
		StringBuilder builder = new StringBuilder();
		name.trim().codePoints()
			.filter(cp -> cp >= 0x20 && cp != 0x7F && cp != 0xA7)
			.limit(MAX_NAME_LENGTH)
			.forEach(builder::appendCodePoint);
		return builder.toString().trim();
	}

	private static String shortDimension(String dimension) {
		int colon = dimension.indexOf(':');
		return colon >= 0 && dimension.startsWith("minecraft:") ? dimension.substring(colon + 1) : dimension;
	}

	private static String worldKey(Minecraft minecraft) {
		MinecraftServer local = minecraft.getSingleplayerServer();
		if (local != null) {
			return "local:" + local.getWorldData().getLevelName().toLowerCase(Locale.ROOT);
		}
		ServerData server = minecraft.getCurrentServer();
		return server == null ? "unknown" : "server:" + server.ip.toLowerCase(Locale.ROOT);
	}

	private List<Waypoint> current(Minecraft minecraft) {
		ensureLoaded();
		return worlds.computeIfAbsent(worldKey(minecraft), key -> new ArrayList<>());
	}

	private void ensureLoaded() {
		if (loaded) {
			return;
		}
		loaded = true;
		JsonFiles.read(file).ifPresent(root -> {
			for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
				if (!entry.getValue().isJsonArray()) {
					continue;
				}
				List<Waypoint> list = new ArrayList<>();
				for (JsonElement element : entry.getValue().getAsJsonArray()) {
					parse(element).ifPresent(list::add);
					if (list.size() >= MAX_PER_WORLD) {
						break;
					}
				}
				worlds.put(entry.getKey(), list);
			}
		});
	}

	private static java.util.Optional<Waypoint> parse(JsonElement element) {
		if (!element.isJsonObject()) {
			return java.util.Optional.empty();
		}
		JsonObject object = element.getAsJsonObject();
		try {
			String name = sanitize(object.get("name").getAsString());
			String dimension = object.get("dimension").getAsString();
			if (name.isEmpty() || dimension.length() > 128) {
				return java.util.Optional.empty();
			}
			return java.util.Optional.of(new Waypoint(name, object.get("x").getAsInt(), object.get("y").getAsInt(), object.get("z").getAsInt(), dimension));
		} catch (RuntimeException exception) {
			return java.util.Optional.empty();
		}
	}

	private void persist() {
		JsonObject root = new JsonObject();
		for (Map.Entry<String, List<Waypoint>> entry : worlds.entrySet()) {
			if (entry.getValue().isEmpty()) {
				continue;
			}
			JsonArray array = new JsonArray();
			for (Waypoint waypoint : entry.getValue()) {
				JsonObject object = new JsonObject();
				object.addProperty("name", waypoint.name());
				object.addProperty("x", waypoint.x());
				object.addProperty("y", waypoint.y());
				object.addProperty("z", waypoint.z());
				object.addProperty("dimension", waypoint.dimension());
				array.add(object);
			}
			root.add(entry.getKey(), array);
		}
		try {
			JsonFiles.write(file, root);
		} catch (IOException exception) {
			PretupidClient.LOGGER.warn("Could not save waypoints: {}", exception.getMessage());
		}
	}

	private record Shown(Waypoint waypoint, double distance, String arrow) {
	}

	private List<Shown> shown(Minecraft minecraft, boolean preview) {
		LocalPlayer player = minecraft.player;
		if (preview && (player == null || minecraft.level == null || current(minecraft).isEmpty())) {
			return List.of(new Shown(new Waypoint(I18n.get("pretupid.waypoints.preview_home"), 0, 64, 0, ""), 128, ARROWS[1]),
				new Shown(new Waypoint(DEATH_NAME, 0, 12, 0, ""), 412, ARROWS[5]));
		}
		if (player == null || minecraft.level == null) {
			return List.of();
		}
		String dimension = minecraft.level.dimension().identifier().toString();
		return current(minecraft).stream()
			.filter(waypoint -> waypoint.dimension().equals(dimension))
			.map(waypoint -> {
				double dx = waypoint.x() + 0.5 - player.getX();
				double dz = waypoint.z() + 0.5 - player.getZ();
				double distance = Math.sqrt(dx * dx + dz * dz + Math.pow(waypoint.y() - player.getY(), 2));
				float bearing = (float) Math.toDegrees(Math.atan2(-dx, dz));
				float relative = Mth.wrapDegrees(bearing - player.getYRot());
				int sector = Math.floorMod(Math.round(relative / 45f), 8);
				return new Shown(waypoint, distance, ARROWS[sector]);
			})
			.sorted(Comparator.comparingDouble(Shown::distance))
			.limit(maxShown.intValue())
			.toList();
	}

	private String line(Shown shown) {
		String distance = shown.distance() >= 1000 ? String.format(Locale.ROOT, "%.1f km", shown.distance() / 1000.0) : Math.round(shown.distance()) + " m";
		String coordinates = showCoordinates.enabled() ? " (" + shown.waypoint().x() + ", " + shown.waypoint().y() + ", " + shown.waypoint().z() + ")" : "";
		return shown.arrow() + " " + shown.waypoint().name() + "  " + distance + coordinates;
	}

	@Override
	public boolean visible(Minecraft minecraft) {
		return super.visible(minecraft) && !shown(minecraft, false).isEmpty();
	}

	@Override
	public int width(Font font, Minecraft minecraft, boolean preview) {
		int width = 40;
		for (Shown shown : shown(minecraft, preview)) {
			width = Math.max(width, font.width(line(shown)));
		}
		return width + padding() * 2;
	}

	@Override
	public int height(Font font, Minecraft minecraft, boolean preview) {
		return Math.max(1, shown(minecraft, preview).size()) * ROW_HEIGHT + padding() * 2 - 2;
	}

	@Override
	public void render(GuiGraphicsExtractor graphics, Font font, Minecraft minecraft, boolean preview) {
		List<Shown> rows = shown(minecraft, preview);
		drawBackground(graphics, width(font, minecraft, preview), height(font, minecraft, preview));
		int y = padding();
		boolean shadow = textShadow.enabled();
		for (Shown shown : rows) {
			String prefix = shown.arrow() + " " + shown.waypoint().name() + "  ";
			graphics.text(font, prefix, padding(), y, nameColor.resolve(), shadow);
			graphics.text(font, line(shown).substring(prefix.length()), padding() + font.width(prefix), y, distanceColor.resolve(), shadow);
			y += ROW_HEIGHT;
		}
	}
}

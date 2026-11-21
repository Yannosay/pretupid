package net.pretupid.client.module.utility;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.config.JsonFiles;
import net.pretupid.client.hud.HudModule;
import net.pretupid.client.hud.HudPosition;
import net.pretupid.client.hud.OverlayRenderer;
import net.pretupid.client.module.Category;
import net.pretupid.client.module.ManagedModule;
import net.pretupid.client.platform.Gfx;
import net.pretupid.client.platform.Platform;
import net.pretupid.client.setting.BooleanSetting;
import net.pretupid.client.setting.ColorSetting;
import net.pretupid.client.setting.KeySetting;
import net.pretupid.client.setting.NumberSetting;
import net.pretupid.client.ui.Draw;
import net.pretupid.client.ui.Icon;
import net.pretupid.client.ui.Theme;
import net.pretupid.client.ui.screen.WaypointsScreen;
import net.pretupid.client.util.Colors;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public final class WaypointsModule extends HudModule implements OverlayRenderer, ManagedModule {
	public static final int MAX_NAME_LENGTH = 24;
	public static final int[] PALETTE = {0xFF5AB0FF, 0xFF4ADE80, 0xFFFACC15, 0xFFF97316, 0xFFF43F5E, 0xFFC084FC, 0xFF2DD4BF, 0xFFFFFFFF};
	private static final int MAX_PER_WORLD = 96;
	private static final int MAX_DEATHS = 3;
	private static final int ROW_HEIGHT = 11;
	private static final double ARRIVAL_DISTANCE = 3.0;
	private static final String OVERWORLD = "minecraft:overworld";
	private static final String NETHER = "minecraft:the_nether";
	private static final int ARROW_SIZE = 7;
	private static final int ARROW_SPACE = 10;
	private static final String[] ARROW_STRAIGHT = {"...#...", "..###..", ".#####.", "#######", "..###..", "..###..", "..###.."};
	private static final String[] ARROW_DIAGONAL = {"..#####", "....###", "...####", "..###.#", ".###..#", "###....", ".#....."};
	private static final boolean[][] ARROWS = buildArrows();

	public record Waypoint(String name, int x, int y, int z, String dimension, int color, boolean visible, boolean death, long created) {
		public Waypoint withName(String value) {
			return new Waypoint(value, x, y, z, dimension, color, visible, death, created);
		}

		public Waypoint withColor(int value) {
			return new Waypoint(name, x, y, z, dimension, value, visible, death, created);
		}

		public Waypoint withVisible(boolean value) {
			return new Waypoint(name, x, y, z, dimension, color, value, death, created);
		}

		public String coordinates() {
			return x + " " + y + " " + z;
		}
	}

	public record Located(Waypoint waypoint, Vec3 position, double distance, boolean converted) {
	}

	private final NumberSetting maxShown = add(new NumberSetting("max_shown", 3, 1, 8, 1));
	private final BooleanSetting worldMarkers = add(new BooleanSetting("world_markers", true));
	private final NumberSetting markerRange = add(new NumberSetting("marker_range", 2000, 50, 10000, 50, " m"));
	private final BooleanSetting crossDimension = add(new BooleanSetting("cross_dimension", false));
	private final BooleanSetting deathWaypoint = add(new BooleanSetting("death_waypoint", true));
	private final BooleanSetting clearDeathOnArrival = add(new BooleanSetting("clear_death_on_arrival", true));
	private final BooleanSetting showCoordinates = add(new BooleanSetting("show_coordinates", false));
	private final KeySetting addKey = add(new KeySetting("add_key", KeySetting.UNBOUND));
	private final ColorSetting nameColor = add(new ColorSetting("name_color", 0xFF8FC4FF).followingModuleText());
	private final ColorSetting distanceColor = add(new ColorSetting("distance_color", 0xFFFFFFFF));

	private final Path file = FabricLoader.getInstance().getConfigDir().resolve(PretupidClient.MOD_ID).resolve("waypoints.json");
	private final Map<String, List<Waypoint>> worlds = new LinkedHashMap<>();
	private boolean loaded;
	private boolean wasDead;
	private boolean addKeyHeld;

	public WaypointsModule() {
		super("waypoints", Category.UTILITY, "compass_00", false, new HudPosition(HudPosition.Anchor.END, 4, HudPosition.Anchor.START, 40));
		markerRange.visibleWhen(worldMarkers::enabled);
		clearDeathOnArrival.visibleWhen(deathWaypoint::enabled);
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
			addDeath(minecraft);
		}
		wasDead = dead;
		if (!dead && clearDeathOnArrival.enabled()) {
			clearReachedDeaths(minecraft);
		}
		boolean keyDown = addKey.isBound() && Platform.screen() == null && Platform.isKeyDown(addKey.get());
		if (keyDown && !addKeyHeld) {
			Platform.addChatMessage(add(minecraft, nextName(minecraft)));
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
		Optional<Waypoint> existing = list.stream().filter(waypoint -> waypoint.name().equalsIgnoreCase(clean)).findFirst();
		if (existing.isEmpty() && list.size() >= MAX_PER_WORLD) {
			return Component.translatable("pretupid.waypoints.full", MAX_PER_WORLD).withStyle(ChatFormatting.RED);
		}
		LocalPlayer player = minecraft.player;
		int color = existing.map(Waypoint::color).orElse(PALETTE[Math.floorMod(list.size(), PALETTE.length)]);
		Waypoint waypoint = new Waypoint(clean, player.getBlockX(), player.getBlockY(), player.getBlockZ(), dimension(minecraft), color, true, false, System.currentTimeMillis());
		list.removeIf(entry -> entry.name().equalsIgnoreCase(clean));
		list.add(0, waypoint);
		persist();
		return Component.translatable("pretupid.waypoints.added", Component.literal(waypoint.name()).withStyle(ChatFormatting.AQUA), waypoint.coordinates())
			.withStyle(ChatFormatting.GRAY);
	}

	private void addDeath(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		List<Waypoint> list = current(minecraft);
		String name = I18n.get("pretupid.waypoints.death_name", java.time.LocalTime.now().withNano(0).withSecond(0).toString());
		list.removeIf(entry -> entry.name().equalsIgnoreCase(name));
		list.add(0, new Waypoint(sanitize(name), player.getBlockX(), player.getBlockY(), player.getBlockZ(), dimension(minecraft), 0xFFF43F5E, true, true, System.currentTimeMillis()));
		List<Waypoint> deaths = list.stream().filter(Waypoint::death).sorted(Comparator.comparingLong(Waypoint::created).reversed()).toList();
		for (int i = MAX_DEATHS; i < deaths.size(); i++) {
			list.remove(deaths.get(i));
		}
		while (list.size() > MAX_PER_WORLD) {
			list.removeLast();
		}
		persist();
	}

	private void clearReachedDeaths(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		String dimension = dimension(minecraft);
		List<Waypoint> list = current(minecraft);
		boolean removed = list.removeIf(waypoint -> waypoint.death() && waypoint.dimension().equals(dimension)
			&& player.position().distanceTo(new Vec3(waypoint.x() + 0.5, waypoint.y(), waypoint.z() + 0.5)) < ARRIVAL_DISTANCE
			&& System.currentTimeMillis() - waypoint.created() > 5000L);
		if (removed) {
			persist();
			PretupidClient.get().notifications().push(Component.translatable("pretupid.module.waypoints.name"), Component.translatable("pretupid.waypoints.death_reached"),
				Icon.item("recovery_compass_00"), null, Theme.SUCCESS, 3000L);
		}
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
				.append(Component.literal(waypoint.name()).withStyle(style -> style.withColor(waypoint.color() & 0xFFFFFF)))
				.append(Component.literal(" " + waypoint.coordinates() + " ").withStyle(ChatFormatting.WHITE))
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

	public List<Waypoint> waypoints(Minecraft minecraft) {
		if (minecraft.level == null) {
			return List.of();
		}
		return List.copyOf(current(minecraft));
	}

	public boolean replace(Minecraft minecraft, Waypoint previous, Waypoint updated) {
		if (minecraft.level == null) {
			return false;
		}
		String clean = sanitize(updated.name());
		if (clean.isEmpty()) {
			return false;
		}
		List<Waypoint> list = current(minecraft);
		int index = list.indexOf(previous);
		if (index < 0) {
			return false;
		}
		boolean taken = list.stream().anyMatch(entry -> entry != previous && entry.name().equalsIgnoreCase(clean));
		if (taken) {
			return false;
		}
		list.set(index, updated.withName(clean));
		persist();
		return true;
	}

	public void delete(Minecraft minecraft, Waypoint waypoint) {
		if (minecraft.level != null && current(minecraft).remove(waypoint)) {
			persist();
		}
	}

	public String nextName(Minecraft minecraft) {
		List<Waypoint> list = current(minecraft);
		for (int index = list.size() + 1; ; index++) {
			String candidate = I18n.get("pretupid.waypoints.default_name", index);
			if (list.stream().noneMatch(waypoint -> waypoint.name().equalsIgnoreCase(candidate))) {
				return candidate;
			}
		}
	}

	public static String sanitize(String name) {
		StringBuilder builder = new StringBuilder();
		name.trim().codePoints()
			.filter(cp -> cp >= 0x20 && cp != 0x7F && cp != 0xA7)
			.limit(MAX_NAME_LENGTH)
			.forEach(builder::appendCodePoint);
		return builder.toString().trim();
	}

	public static String shortDimension(String dimension) {
		int colon = dimension.indexOf(':');
		return colon >= 0 && dimension.startsWith("minecraft:") ? dimension.substring(colon + 1) : dimension;
	}

	public static String dimension(Minecraft minecraft) {
		return minecraft.level == null ? "" : minecraft.level.dimension().identifier().toString();
	}

	public Optional<Located> locate(Minecraft minecraft, Waypoint waypoint) {
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null) {
			return Optional.empty();
		}
		String here = dimension(minecraft);
		double x = waypoint.x() + 0.5;
		double z = waypoint.z() + 0.5;
		boolean converted = false;
		if (!waypoint.dimension().equals(here)) {
			if (!crossDimension.enabled()) {
				return Optional.empty();
			}
			if (waypoint.dimension().equals(OVERWORLD) && here.equals(NETHER)) {
				x /= 8.0;
				z /= 8.0;
			} else if (waypoint.dimension().equals(NETHER) && here.equals(OVERWORLD)) {
				x *= 8.0;
				z *= 8.0;
			} else {
				return Optional.empty();
			}
			converted = true;
		}
		Vec3 position = new Vec3(x, waypoint.y(), z);
		return Optional.of(new Located(waypoint, position, position.distanceTo(player.position()), converted));
	}

	public List<Located> locatedHere(Minecraft minecraft) {
		if (minecraft.player == null || minecraft.level == null) {
			return List.of();
		}
		List<Located> result = new ArrayList<>();
		for (Waypoint waypoint : current(minecraft)) {
			if (waypoint.visible()) {
				locate(minecraft, waypoint).ifPresent(result::add);
			}
		}
		return result;
	}

	@Override
	public Component manageLabel() {
		return Component.translatable("pretupid.waypoints.manage");
	}

	@Override
	public boolean canManage(Minecraft minecraft) {
		return minecraft.level != null;
	}

	@Override
	public Screen createManager(Screen parent) {
		return new WaypointsScreen(parent);
	}

	public static String worldKey(Minecraft minecraft) {
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
				int index = 0;
				for (JsonElement element : entry.getValue().getAsJsonArray()) {
					parse(element, index++).ifPresent(list::add);
					if (list.size() >= MAX_PER_WORLD) {
						break;
					}
				}
				worlds.put(entry.getKey(), list);
			}
		});
	}

	private static Optional<Waypoint> parse(JsonElement element, int index) {
		if (!element.isJsonObject()) {
			return Optional.empty();
		}
		JsonObject object = element.getAsJsonObject();
		try {
			String name = sanitize(object.get("name").getAsString());
			String dimension = object.get("dimension").getAsString();
			if (name.isEmpty() || dimension.length() > 128) {
				return Optional.empty();
			}
			boolean death = object.has("death") ? object.get("death").getAsBoolean() : name.equals("Death");
			int color = object.has("color") ? Colors.parseHex(object.get("color").getAsString(), PALETTE[index % PALETTE.length]) | 0xFF000000
				: (death ? 0xFFF43F5E : PALETTE[index % PALETTE.length]);
			boolean visible = !object.has("visible") || object.get("visible").getAsBoolean();
			long created = object.has("created") ? object.get("created").getAsLong() : 0L;
			return Optional.of(new Waypoint(name, object.get("x").getAsInt(), object.get("y").getAsInt(), object.get("z").getAsInt(), dimension, color, visible, death, created));
		} catch (RuntimeException exception) {
			return Optional.empty();
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
				object.addProperty("color", Colors.toRgbHex(waypoint.color()));
				object.addProperty("visible", waypoint.visible());
				object.addProperty("death", waypoint.death());
				object.addProperty("created", waypoint.created());
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

	private record Shown(Waypoint waypoint, double distance, int sector) {
	}

	private List<Shown> shown(Minecraft minecraft, boolean preview) {
		LocalPlayer player = minecraft.player;
		if (preview && (player == null || minecraft.level == null || current(minecraft).isEmpty())) {
			return List.of(new Shown(new Waypoint(I18n.get("pretupid.waypoints.preview_home"), 0, 64, 0, "", PALETTE[0], true, false, 0L), 128, 1),
				new Shown(new Waypoint(I18n.get("pretupid.waypoints.preview_death"), 0, 12, 0, "", 0xFFF43F5E, true, true, 0L), 412, 5));
		}
		if (player == null || minecraft.level == null) {
			return List.of();
		}
		List<Shown> rows = new ArrayList<>();
		for (Waypoint waypoint : current(minecraft)) {
			if (!waypoint.visible()) {
				continue;
			}
			locate(minecraft, waypoint).ifPresent(located -> {
				double dx = located.position().x - player.getX();
				double dz = located.position().z - player.getZ();
				float bearing = (float) Math.toDegrees(Math.atan2(-dx, dz));
				float relative = Mth.wrapDegrees(bearing - player.getYRot());
				int sector = Math.floorMod(Math.round(relative / 45f), 8);
				rows.add(new Shown(waypoint, located.distance(), sector));
			});
		}
		rows.sort(Comparator.comparingDouble(Shown::distance));
		return rows.size() > maxShown.intValue() ? rows.subList(0, maxShown.intValue()) : rows;
	}

	public static String distanceText(double distance) {
		return distance >= 1000 ? String.format(Locale.ROOT, "%.1f km", distance / 1000.0) : Math.round(distance) + " m";
	}

	private String line(Shown shown) {
		String coordinates = showCoordinates.enabled() ? " (" + shown.waypoint().x() + ", " + shown.waypoint().y() + ", " + shown.waypoint().z() + ")" : "";
		return shown.waypoint().name() + "  " + distanceText(shown.distance()) + coordinates;
	}

	@Override
	public boolean visible(Minecraft minecraft) {
		return super.visible(minecraft) && !shown(minecraft, false).isEmpty();
	}

	@Override
	public int width(Font font, Minecraft minecraft, boolean preview) {
		int width = 40;
		for (Shown shown : shown(minecraft, preview)) {
			width = Math.max(width, font.width(line(shown)) + 6 + ARROW_SPACE);
		}
		return width + padding() * 2;
	}

	@Override
	public int height(Font font, Minecraft minecraft, boolean preview) {
		return Math.max(1, shown(minecraft, preview).size()) * ROW_HEIGHT + padding() * 2 - 2;
	}

	@Override
	public void render(Gfx graphics, Font font, Minecraft minecraft, boolean preview) {
		List<Shown> rows = shown(minecraft, preview);
		drawBackground(graphics, width(font, minecraft, preview), height(font, minecraft, preview));
		int y = padding();
		boolean shadow = textShadow.enabled();
		for (Shown shown : rows) {
			Draw.rect(graphics, padding(), y + 1, 3, 7, shown.waypoint().color());
			String prefix = shown.waypoint().name() + "  ";
			int arrowX = padding() + 6;
			int nameColorValue = nameColor.resolve();
			if (shadow) {
				drawArrow(graphics, arrowX + 1, y + 1, shown.sector(), (nameColorValue & 0xFF000000) | ((nameColorValue & 0xFCFCFC) >> 2));
			}
			drawArrow(graphics, arrowX, y, shown.sector(), nameColorValue);
			int x = arrowX + ARROW_SPACE;
			graphics.text(font, prefix, x, y, nameColorValue, shadow);
			graphics.text(font, line(shown).substring(prefix.length()), x + font.width(prefix), y, distanceColor.resolve(), shadow);
			y += ROW_HEIGHT;
		}
	}

	@Override
	public void renderOverlay(Gfx graphics, Minecraft minecraft, DeltaTracker deltaTracker) {
		if (!worldMarkers.enabled() || minecraft.player == null || minecraft.level == null || Platform.hudHidden()) {
			return;
		}
		Camera camera = Platform.camera();
		if (camera == null || !camera.isInitialized()) {
			return;
		}
		Matrix4f matrix = Platform.viewProjection(camera);
		Vec3 eye = camera.position();
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		Font font = minecraft.font;
		double range = markerRange.get();
		for (Waypoint waypoint : current(minecraft)) {
			if (!waypoint.visible()) {
				continue;
			}
			Optional<Located> located = locate(minecraft, waypoint);
			if (located.isEmpty() || located.get().distance() > range) {
				continue;
			}
			drawMarker(graphics, font, matrix, eye, located.get(), width, height);
		}
	}

	private record Projected(float x, float y, boolean behind) {
	}

	private static Projected project(Matrix4f matrix, Vec3 eye, Vec3 point, int width, int height) {
		Vec3 relative = point.subtract(eye);
		Vector4f clip = matrix.transform(new Vector4f((float) relative.x, (float) relative.y, (float) relative.z, 1f));
		if (clip.w <= 0.05f) {
			return new Projected(-clip.x, -clip.y, true);
		}
		float nx = clip.x / clip.w;
		float ny = clip.y / clip.w;
		return new Projected((nx * 0.5f + 0.5f) * width, (1f - (ny * 0.5f + 0.5f)) * height, false);
	}

	private void drawMarker(Gfx graphics, Font font, Matrix4f matrix, Vec3 eye, Located located, int width, int height) {
		Vec3 base = located.position();
		Projected pin = project(matrix, eye, base.add(0, 1.4, 0), width, height);
		double distance = located.distance();
		int color = located.waypoint().color();
		float fade = (float) Math.clamp((distance - 1.5) / 5.0, 0.3, 1.0);
		int alpha = Math.round(255 * fade);
		int margin = 14;
		boolean offScreen = pin.behind() || pin.x() < margin || pin.x() > width - margin || pin.y() < margin || pin.y() > height - margin;
		if (offScreen) {
			drawEdgePointer(graphics, font, pin, located, width, height, margin);
			return;
		}
		int sx = Math.round(pin.x());
		int sy = Math.round(pin.y());
		Projected top = project(matrix, eye, base.add(0, 48, 0), width, height);
		Projected foot = project(matrix, eye, base, width, height);
		if (!top.behind() && !foot.behind() && distance > 6) {
			drawBeam(graphics, foot.x(), foot.y(), top.x(), top.y(), color, Math.round(alpha * 0.35f));
		}
		if (distance < 8) {
			float pulse = (float) ((System.currentTimeMillis() % 1400L) / 1400.0);
			ring(graphics, sx, sy + 4, Math.round(6 + pulse * 10), Colors.withAlpha(color, Math.round(alpha * (1f - pulse) * 0.8f)));
		}
		drawPin(graphics, font, sx, sy, color, alpha, located.waypoint());
		float centerDistance = (float) Math.hypot(sx - width / 2.0, sy - height / 2.0);
		boolean focused = centerDistance < Math.min(width, height) * 0.18f;
		String meters = distanceText(distance);
		int dy = located.waypoint().y() - Mth.floor(eye.y);
		String vertical = Math.abs(dy) >= 4 ? (dy > 0 ? " ↑" : " ↓") + Math.abs(dy) : "";
		if (!focused) {
			String text = meters + vertical;
			int w = font.width(text) + 6;
			Draw.rect(graphics, sx - w / 2, sy - 22, w, 11, Colors.withAlpha(0x000000, Math.round(120 * fade)));
			graphics.text(font, text, sx - font.width(text) / 2, sy - 20, Colors.withAlpha(0xFFFFFF, alpha), false);
			return;
		}
		String name = located.waypoint().name() + (located.converted() ? " ⇄" : "");
		String detail = meters + vertical;
		if (showCoordinates.enabled()) {
			detail += "  " + located.waypoint().x() + " " + located.waypoint().y() + " " + located.waypoint().z();
		}
		int box = Math.max(font.width(name), font.width(detail)) + 10;
		int boxTop = sy - 33;
		Draw.rect(graphics, sx - box / 2, boxTop, box, 22, Colors.withAlpha(0x0B0D12, Math.round(200 * fade)));
		Draw.rect(graphics, sx - box / 2, boxTop, 2, 22, Colors.withAlpha(color, alpha));
		graphics.text(font, name, sx - font.width(name) / 2, boxTop + 3, Colors.withAlpha(0xFFFFFF, alpha), false);
		graphics.text(font, detail, sx - font.width(detail) / 2, boxTop + 12, Colors.withAlpha(0xB8C2D0, alpha), false);
	}

	private static void drawPin(Gfx graphics, Font font, int cx, int cy, int color, int alpha, Waypoint waypoint) {
		disc(graphics, cx, cy, 6, Colors.withAlpha(0x000000, Math.round(alpha * 0.55f)));
		triangle(graphics, cx - 4, cy + 3, cx + 4, cy + 3, cx, cy + 10, Colors.withAlpha(0x000000, Math.round(alpha * 0.55f)));
		disc(graphics, cx, cy, 5, Colors.withAlpha(color, alpha));
		triangle(graphics, cx - 3, cy + 3, cx + 3, cy + 3, cx, cy + 8, Colors.withAlpha(color, alpha));
		String glyph = waypoint.death() ? "☠" : waypoint.name().isEmpty() ? "•" : waypoint.name().substring(0, 1).toUpperCase(Locale.ROOT);
		int textColor = luminance(color) > 0.6f ? 0xFF101318 : 0xFFFFFFFF;
		graphics.text(font, glyph, cx - font.width(glyph) / 2 + 1, cy - 3, Colors.withAlpha(textColor, alpha), false);
	}

	private void drawEdgePointer(Gfx graphics, Font font, Projected pin, Located located, int width, int height, int margin) {
		float cx = width / 2f;
		float cy = height / 2f;
		float dx = pin.behind() ? pin.x() : pin.x() - cx;
		float dy = pin.behind() ? -pin.y() : pin.y() - cy;
		float length = (float) Math.max(1e-3, Math.hypot(dx, dy));
		dx /= length;
		dy /= length;
		float scale = Math.min((cx - margin) / Math.max(1e-3f, Math.abs(dx)), (cy - margin) / Math.max(1e-3f, Math.abs(dy)));
		int px = Math.round(cx + dx * scale);
		int py = Math.round(cy + dy * scale);
		int color = located.waypoint().color();
		disc(graphics, px, py, 7, 0xA0000000);
		disc(graphics, px, py, 6, Colors.withAlpha(color, 230));
		float nx = -dy;
		float ny = dx;
		triangle(graphics, px + 0.5f + dx * 5 + nx * 5, py + 0.5f + dy * 5 + ny * 5, px + 0.5f + dx * 5 - nx * 5, py + 0.5f + dy * 5 - ny * 5,
			px + 0.5f + dx * 13, py + 0.5f + dy * 13, Colors.withAlpha(color, 230));
		String glyph = located.waypoint().death() ? "☠" : located.waypoint().name().isEmpty() ? "•" : located.waypoint().name().substring(0, 1).toUpperCase(Locale.ROOT);
		int textColor = luminance(color) > 0.6f ? 0xFF101318 : 0xFFFFFFFF;
		graphics.text(font, glyph, px - font.width(glyph) / 2 + 1, py - 3, textColor, false);
		String meters = distanceText(located.distance());
		int textX = Math.clamp(Math.round(px - dx * 14) - font.width(meters) / 2, 2, width - font.width(meters) - 2);
		int textY = Math.clamp(Math.round(py - dy * 14) - 4, 2, height - 10);
		graphics.text(font, meters, textX, textY, 0xFFFFFFFF, true);
	}

	private static boolean[][] buildArrows() {
		boolean[][] arrows = new boolean[8][];
		for (int sector = 0; sector < 8; sector++) {
			String[] source = sector % 2 == 0 ? ARROW_STRAIGHT : ARROW_DIAGONAL;
			boolean[] cells = new boolean[ARROW_SIZE * ARROW_SIZE];
			for (int y = 0; y < ARROW_SIZE; y++) {
				for (int x = 0; x < ARROW_SIZE; x++) {
					int px = x;
					int py = y;
					for (int turn = 0; turn < sector / 2; turn++) {
						int next = ARROW_SIZE - 1 - py;
						py = px;
						px = next;
					}
					cells[py * ARROW_SIZE + px] = source[y].charAt(x) == '#';
				}
			}
			arrows[sector] = cells;
		}
		return arrows;
	}

	private static void drawArrow(Gfx graphics, int x, int y, int sector, int color) {
		boolean[] cells = ARROWS[Math.floorMod(sector, 8)];
		for (int row = 0; row < ARROW_SIZE; row++) {
			int column = 0;
			while (column < ARROW_SIZE) {
				if (!cells[row * ARROW_SIZE + column]) {
					column++;
					continue;
				}
				int start = column;
				while (column < ARROW_SIZE && cells[row * ARROW_SIZE + column]) {
					column++;
				}
				graphics.fill(x + start, y + row, x + column, y + row + 1, color);
			}
		}
	}

	private static float luminance(int argb) {
		return (0.299f * ((argb >> 16) & 0xFF) + 0.587f * ((argb >> 8) & 0xFF) + 0.114f * (argb & 0xFF)) / 255f;
	}

	private static void drawBeam(Gfx graphics, float x1, float y1, float x2, float y2, int color, int alpha) {
		int steps = Math.max(1, Math.round(Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1))));
		steps = Math.min(steps, 800);
		for (int i = 0; i <= steps; i++) {
			float t = i / (float) steps;
			int x = Math.round(x1 + (x2 - x1) * t);
			int y = Math.round(y1 + (y2 - y1) * t);
			int a = Math.round(alpha * (1f - t));
			if (a < 4) {
				break;
			}
			graphics.fill(x - 1, y, x + 2, y + 1, Colors.withAlpha(color, a));
		}
	}

	private static void disc(Gfx graphics, int cx, int cy, int radius, int color) {
		for (int dy = -radius; dy <= radius; dy++) {
			int half = (int) Math.floor(Math.sqrt(radius * radius - dy * dy + radius * 0.6));
			graphics.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, color);
		}
	}

	private static void ring(Gfx graphics, int cx, int cy, int radius, int color) {
		if ((color >>> 24) < 4) {
			return;
		}
		int segments = Math.max(16, radius * 6);
		for (int i = 0; i < segments; i++) {
			double angle = Math.PI * 2 * i / segments;
			int x = (int) Math.round(cx + Math.cos(angle) * radius);
			int y = (int) Math.round(cy + Math.sin(angle) * radius * 0.45);
			graphics.fill(x, y, x + 1, y + 1, color);
		}
	}

	private static void triangle(Gfx graphics, float x1, float y1, float x2, float y2, float x3, float y3, int color) {
		int minY = (int) Math.floor(Math.min(y1, Math.min(y2, y3)));
		int maxY = (int) Math.ceil(Math.max(y1, Math.max(y2, y3)));
		float[][] edges = {{x1, y1, x2, y2}, {x2, y2, x3, y3}, {x3, y3, x1, y1}};
		for (int y = minY; y < maxY; y++) {
			float sample = y + 0.5f;
			float left = Float.MAX_VALUE;
			float right = -Float.MAX_VALUE;
			for (float[] edge : edges) {
				float ay = edge[1];
				float by = edge[3];
				if (ay == by || sample < Math.min(ay, by) || sample >= Math.max(ay, by)) {
					continue;
				}
				float x = edge[0] + (edge[2] - edge[0]) * (sample - ay) / (by - ay);
				left = Math.min(left, x);
				right = Math.max(right, x);
			}
			if (left <= right) {
				int start = Math.round(left);
				graphics.fill(start, y, Math.max(start + 1, Math.round(right)), y + 1, color);
			}
		}
	}
}

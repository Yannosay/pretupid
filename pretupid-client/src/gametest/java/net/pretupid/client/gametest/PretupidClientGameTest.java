package net.pretupid.client.gametest;

import net.pretupid.client.platform.Platform;
import net.pretupid.client.ui.ChatHistory;
import com.mojang.blaze3d.platform.InputConstants;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.Socket;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.config.ClientSettings;
import net.pretupid.client.cosmetic.Catalog;
import net.pretupid.client.cosmetic.EmoteAnimation;
import net.pretupid.client.emoji.ChatEmojis;
import net.pretupid.client.emoji.EmojiIndex;
import net.pretupid.client.emoji.EmojiPicker;
import net.pretupid.client.module.Module;
import net.pretupid.client.module.visual.CinematicModule;
import net.pretupid.client.module.visual.FreecamModule;
import net.pretupid.client.ui.screen.CinematicEditorScreen;
import net.pretupid.client.ui.screen.ScreenieScreen;
import net.pretupid.client.ui.screen.WaypointsScreen;
import net.pretupid.client.screenie.MemeTemplate;
import net.pretupid.client.module.hud.BiomeHud;
import net.pretupid.client.module.hud.DayHud;
import net.pretupid.client.module.hud.LookAtHud;
import net.pretupid.client.module.utility.CompactChatModule;
import net.pretupid.client.module.utility.NickHiderModule;
import net.pretupid.client.module.utility.WaypointsModule;
import net.pretupid.client.module.visual.BlockOutlineModule;
import net.pretupid.client.module.visual.OwnNametagModule;
import net.pretupid.client.nametag.NameAnimation;
import net.pretupid.client.nametag.NameEffect;
import net.pretupid.client.nametag.NametagPresets;
import net.pretupid.client.social.LocalHosting;
import net.pretupid.client.cosmetic.EmoteWheel;
import net.pretupid.client.ui.Icon;
import net.pretupid.client.ui.screen.ClientMenuScreen;
import net.pretupid.client.ui.screen.EmoteWheelScreen;
import net.pretupid.client.ui.screen.HudEditorScreen;

public final class PretupidClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			waitForChunks(world);
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("weather clear");
			BlockPos feet = context.computeOnClient(minecraft -> minecraft.player.blockPosition());
			world.getServer().runCommand("setblock " + feet.getX() + " " + feet.getY() + " " + (feet.getZ() + 3) + " minecraft:diamond_block");
			context.waitTicks(10);

			testHudModules(context, feet);
			testAccentLabels(context);
			testChatModules(context);
			testChatEmojis(context);
			testFreecam(context, world, feet);
			testCinematic(context, world, feet);
			testWaypoints(context, world, feet);
			testNewModules(context, world, feet);
			testScreenies(context);
			testTexturedWearable(context);
			testCosmetics(context);
			testEmotes(context);
			testDeadPose(context, world, feet);
			testEmoteWheel(context);
			testNotifications(context);
			testMenuPages(context);
			testHudEditor(context);
			testHosting(context);
			context.setScreen(() -> new PauseScreen(true));
			context.waitTicks(5);
			context.takeScreenshot("pretupid-pause-screen");
			context.setScreen(() -> null);
		}
		context.waitForScreen(TitleScreen.class);
		context.waitTicks(20);
		context.takeScreenshot("pretupid-title-screen");
		testTitleScreenEntrance(context);
		if (System.getenv("PRETUPID_SHOWCASE") != null) {
			try (TestSingleplayerContext scenic = context.worldBuilder().setUseConsistentSettings(false).adjustSettings(state -> state.setSeed("pretupid")).create()) {
				waitForChunks(scenic);
				BlockPos spot = scenic.getServer().computeOnServer(PretupidClientGameTest::scenicSpot);
				for (String command : new String[] {"gamerule doDaylightCycle false", "gamerule advance_time false", "gamerule doWeatherCycle false", "gamerule doMobSpawning false", "weather clear", "difficulty easy"}) {
					try {
						scenic.getServer().runCommand(command);
					} catch (RuntimeException ignored) {
					}
				}
				scenic.getServer().runCommand("fill " + (spot.getX() - 5) + " " + spot.getY() + " " + (spot.getZ() - 5) + " " + (spot.getX() + 5) + " " + (spot.getY() + 4) + " " + (spot.getZ() + 5) + " minecraft:air");
				scenic.getServer().runCommand("tp @a " + spot.getX() + " " + spot.getY() + " " + spot.getZ() + " 160 8");
				context.waitTicks(40);
				waitForChunks(scenic);
				showcase(context, scenic, spot);
			}
		}
	}

	private static BlockPos scenicSpot(net.minecraft.server.MinecraftServer server) {
		net.minecraft.server.level.ServerLevel level = server.overworld();
		var found = level.findClosestBiome3d(biome -> biome.is(net.minecraft.world.level.biome.Biomes.CHERRY_GROVE), BlockPos.ZERO, 6400, 32, 64);
		BlockPos origin = found == null ? BlockPos.ZERO : found.getFirst();
		BlockPos best = origin;
		int bestY = Integer.MIN_VALUE;
		for (int dx = -24; dx <= 24; dx += 8) {
			for (int dz = -24; dz <= 24; dz += 8) {
				int x = origin.getX() + dx;
				int z = origin.getZ() + dz;
				level.getChunk(x >> 4, z >> 4);
				int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				if (y > bestY && level.getBlockState(new BlockPos(x, y - 1, z)).isSolidRender()) {
					bestY = y;
					best = new BlockPos(x, y, z);
				}
			}
		}
		return bestY == Integer.MIN_VALUE ? origin.atY(level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, origin.getX(), origin.getZ())) : best;
	}

	private static float widgetAlpha(net.minecraft.client.gui.components.AbstractWidget widget) {
		try {
			Field field = net.minecraft.client.gui.components.AbstractWidget.class.getDeclaredField("alpha");
			field.setAccessible(true);
			return field.getFloat(widget);
		} catch (ReflectiveOperationException exception) {
			return -1f;
		}
	}

	private static void testTitleScreenEntrance(ClientGameTestContext context) {
		boolean animations = context.computeOnClient(minecraft -> PretupidClient.get().settings().animations.enabled());
		context.runOnClient(minecraft -> PretupidClient.get().settings().animations.set(true));
		context.setScreen(() -> new net.minecraft.client.gui.screens.GenericMessageScreen(Component.literal("")));
		context.waitTicks(40);
		context.setScreen(() -> new TitleScreen(true));
		context.waitTick();
		int[] first = context.computeOnClient(minecraft -> titleButton());
		context.takeScreenshot("pretupid-title-entering");
		context.waitTicks(30);
		int[] last = context.computeOnClient(minecraft -> titleButton());
		check(first[0] > last[0], "The title screen buttons did not slide in (" + first[0] + " then " + last[0] + ")");
		check(first[1] < 100 && last[1] == 100, "The title screen buttons did not fade in (" + first[1] + "% then " + last[1] + "%)");
		context.takeScreenshot("pretupid-title-entered");
		context.runOnClient(minecraft -> PretupidClient.get().settings().animations.set(animations));
	}

	private static int[] titleButton() {
		var widgets = Platform.widgets(Platform.screen());
		var widget = widgets.stream().max(java.util.Comparator.comparingInt(net.minecraft.client.gui.components.AbstractWidget::getY)).orElseThrow();
		return new int[] {widget.getY(), Math.round(widgetAlpha(widget) * 100f)};
	}

	private static void waitForChunks(TestSingleplayerContext world) {
		for (String accessor : new String[] {"getConnection", "getClientLevel", "getClientWorld"}) {
			try {
				Object level = TestSingleplayerContext.class.getMethod(accessor).invoke(world);
				level.getClass().getMethod("waitForChunksRender").invoke(level);
				return;
			} catch (ReflectiveOperationException exception) {
				continue;
			}
		}
		throw new AssertionError("No way to wait for chunks in this Fabric API");
	}

	private static void enable(Minecraft minecraft, Class<? extends Module> type) {
		PretupidClient.get().modules().get(type).setEnabled(true);
	}

	private static void testHudModules(ClientGameTestContext context, BlockPos feet) {
		context.runOnClient(minecraft -> {
			enable(minecraft, BiomeHud.class);
			enable(minecraft, DayHud.class);
			enable(minecraft, LookAtHud.class);
			enable(minecraft, BlockOutlineModule.class);
		});
		lookAt(context, feet.offset(0, 0, 3));
		context.waitTicks(10);
		String target = context.computeOnClient(minecraft -> minecraft.level.getBlockState(feet.offset(0, 0, 3)).getBlock().getName().getString());
		check(target.toLowerCase().contains("diamond"), "Looking At target is " + target);
		context.takeScreenshot("pretupid-hud-modules");
	}

	private static void testAccentLabels(ClientGameTestContext context) {
		context.runOnClient(minecraft -> {
			ClientSettings settings = PretupidClient.get().settings();
			settings.accent.setArgb(0xFFFF5577);
			try {
				Field field = net.pretupid.client.hud.TextHudModule.class.getDeclaredField("labelColor");
				field.setAccessible(true);
				net.pretupid.client.setting.ColorSetting label = (net.pretupid.client.setting.ColorSetting) field.get(PretupidClient.get().modules().get(BiomeHud.class));
				check(label.resolve() == 0xFFFF5577, "HUD labels did not follow the interface color");
				settings.moduleText.setArgb(0xFF22CCAA);
				check(label.resolve() == 0xFF22CCAA, "HUD labels did not follow the module text color");
				settings.moduleText.reset();
				check(label.resolve() == 0xFFFF5577, "The module text color did not return to the interface color");
				label.setArgb(0xFF00FF00);
				check(label.resolve() == 0xFF00FF00, "A custom label color was overridden by the interface color");
				label.reset();
			} catch (ReflectiveOperationException exception) {
				throw new AssertionError("HUD label color is not reachable", exception);
			}
		});
		context.waitTicks(3);
		context.takeScreenshot("pretupid-hud-accent");
		context.runOnClient(minecraft -> PretupidClient.get().settings().accent.reset());
	}

	private static List<Component> chatMessages(Minecraft minecraft) {
		if (!(Platform.chat() instanceof ChatHistory history)) {
			throw new AssertionError("Chat history is not readable");
		}
		List<Component> messages = new java.util.ArrayList<>();
		for (int i = 0; i < history.size(); i++) {
			messages.add(history.content(i));
		}
		return messages;
	}

	private static void look(ClientGameTestContext context, float yaw, float pitch) {
		context.runOnClient(minecraft -> {
			minecraft.player.setYRot(yaw);
			minecraft.player.setXRot(pitch);
			minecraft.player.yRotO = yaw;
			minecraft.player.xRotO = pitch;
		});
	}

	private static void lookAt(ClientGameTestContext context, BlockPos target) {
		context.runOnClient(minecraft -> {
			var eye = minecraft.player.getEyePosition();
			double dx = target.getX() + 0.5 - eye.x;
			double dy = target.getY() + 0.5 - eye.y;
			double dz = target.getZ() + 0.5 - eye.z;
			float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
			float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
			minecraft.player.setYRot(yaw);
			minecraft.player.setXRot(pitch);
			minecraft.player.yRotO = yaw;
			minecraft.player.xRotO = pitch;
		});
	}

	private static void testChatModules(ClientGameTestContext context) {
		context.runOnClient(minecraft -> {
			enable(minecraft, CompactChatModule.class);
			ChatComponent chat = Platform.chat();
			for (int i = 0; i < 3; i++) {
				Platform.addChatMessage(Component.literal("Pretupid compact chat test"));
			}
			String latest = chatMessages(minecraft).getFirst().getString();
			check(latest.endsWith("(3×)"), "Compact Chat produced \"" + latest + "\"");
			long copies = chatMessages(minecraft).stream().filter(message -> message.getString().startsWith("Pretupid compact chat test")).count();
			check(copies == 1, "Compact Chat kept " + copies + " copies");

			NickHiderModule nickHider = PretupidClient.get().modules().get(NickHiderModule.class);
			nickHider.setEnabled(true);
			String name = minecraft.getUser().getName();
			Platform.addChatMessage(Component.literal("<" + name + "> hello from the game test"));
			String hidden = chatMessages(minecraft).getFirst().getString();
			check(!hidden.contains(name) && hidden.contains(nickHider.alias()), "Nick Hider produced \"" + hidden + "\"");
		});
		context.waitTicks(5);
		context.takeScreenshot("pretupid-chat-modules");
		context.runOnClient(minecraft -> PretupidClient.get().modules().get(NickHiderModule.class).setEnabled(false));
	}

	private static void testChatEmojis(ClientGameTestContext context) {
		int fire = EmojiIndex.get().byName("fire").orElseThrow().codepoint();
		int sob = EmojiIndex.get().byName("sob").orElseThrow().codepoint();
		context.runOnClient(minecraft -> {
			ChatEmojis.Outgoing outgoing = PretupidClient.get().chatEmojis().prepare("gg :fire: nice :sob:", minecraft.getUser().getProfileId());
			check(outgoing.message().equals("gg nice"), "Emoji were not stripped from the sent text: \"" + outgoing.message() + "\"");
			ChatEmojis.Outgoing only = PretupidClient.get().chatEmojis().prepare(":fire:", minecraft.getUser().getProfileId());
			check(only.overlay() == null && only.message().equals("🔥"), "An emoji-only message did not fall back to Unicode");
			minecraft.player.connection.sendChat("gg :fire: nice :sob:");
		});
		for (int i = 0; i < 60 && !context.computeOnClient(minecraft -> chatMessages(minecraft).getFirst().getString().contains("nice")); i++) {
			context.waitTick();
		}
		context.runOnClient(minecraft -> {
			String shown = chatMessages(minecraft).getFirst().getString();
			check(shown.contains(new String(Character.toChars(fire))) && shown.contains(new String(Character.toChars(sob))),
				"Own chat message did not get its emoji back: \"" + shown + "\"");
			Platform.addChatMessage(Component.literal("Unicode emoji 😭 and :thumbsup: work"));
			String unicode = chatMessages(minecraft).getFirst().getString();
			check(unicode.contains(new String(Character.toChars(sob))) && !unicode.contains(":thumbsup:"), "Incoming emoji were not rendered: \"" + unicode + "\"");
		});
		context.setScreen(() -> Platform.chatScreen(":smi"));
		context.waitTicks(5);
		context.runOnClient(minecraft -> {
			try {
				for (Field field : ChatScreen.class.getDeclaredFields()) {
					if (field.getType() == EmojiPicker.class) {
						field.setAccessible(true);
						Object picker = field.get(Platform.screen());
						Field open = EmojiPicker.class.getDeclaredField("open");
						open.setAccessible(true);
						open.setBoolean(picker, true);
					}
				}
			} catch (ReflectiveOperationException exception) {
				throw new AssertionError("The emoji picker is not reachable", exception);
			}
		});
		context.waitTicks(3);
		context.takeScreenshot("pretupid-chat-emoji");
		context.setScreen(() -> null);
	}

	private static void testCinematic(ClientGameTestContext context, TestSingleplayerContext world, BlockPos feet) {
		context.runOnClient(minecraft -> PretupidClient.get().modules().get(CinematicModule.class).setEnabled(true));
		int[][] spots = {{-6, 3, -6, -45, 20}, {6, 5, -6, 45, 30}, {8, 2, 6, 135, 10}, {-4, 6, 8, -140, 35}};
		for (int[] spot : spots) {
			world.getServer().runCommand("tp @a " + (feet.getX() + spot[0]) + " " + (feet.getY() + spot[1]) + " " + (feet.getZ() + spot[2]) + " " + spot[3] + " " + spot[4]);
			context.waitTicks(4);
			context.runOnClient(minecraft -> check(PretupidClient.get().cinematic().addKeyframeAtCamera(minecraft, 2.0) >= 0, "A keyframe could not be added"));
		}
		world.getServer().runCommand("tp @a " + feet.getX() + " " + (feet.getY() + 12) + " " + (feet.getZ() - 14) + " 0 40");
		context.waitTicks(10);
		context.runOnClient(minecraft -> {
			var shot = PretupidClient.get().cinematic().current();
			check(shot != null && shot.keyframes().size() == 4 && Math.abs(shot.duration() - 6.0) < 1e-6, "The shot does not hold four keyframes over six seconds");
			var middle = shot.sample(3.0);
			check(middle != null && middle.position().distanceTo(shot.keyframes().get(1).position()) > 0.5, "The camera path does not move between keyframes");
		});
		context.setScreen(CinematicEditorScreen::new);
		context.waitTicks(5);
		context.takeScreenshot("pretupid-cinematic-editor");
		context.runOnClient(minecraft -> PretupidClient.get().cinematic().preview(3.0));
		context.waitTicks(3);
		context.takeScreenshot("pretupid-cinematic-preview");
		context.runOnClient(minecraft -> {
			PretupidClient.get().cinematic().preview(0);
			PretupidClient.get().cinematic().play();
		});
		context.waitTicks(30);
		context.runOnClient(minecraft -> check(PretupidClient.get().cinematic().playing(), "The shot stopped playing early"));
		context.takeScreenshot("pretupid-cinematic-playing");
		context.waitTicks(120);
		context.runOnClient(minecraft -> check(!PretupidClient.get().cinematic().playing(), "The shot did not finish"));
		context.setScreen(() -> null);
		world.getServer().runCommand("tp @a " + feet.getX() + " " + feet.getY() + " " + feet.getZ() + " 180 10");
		context.waitTicks(5);
		context.runOnClient(minecraft -> PretupidClient.get().cinematic().addTemplate(net.pretupid.client.cinematic.ShotTemplate.ORBIT, minecraft));
		context.setScreen(CinematicEditorScreen::new);
		context.runOnClient(minecraft -> PretupidClient.get().cinematic().preview(4.5));
		context.waitTicks(5);
		context.takeScreenshot("pretupid-cinematic-orbit");
		java.nio.file.Path[] renderFolder = new java.nio.file.Path[1];
		context.runOnClient(minecraft -> {
			var director = PretupidClient.get().cinematic();
			director.current().keyframes().removeIf(keyframe -> keyframe.time() > 1.6);
			check(director.startRender(20, 1, result -> renderFolder[0] = result.folder()), "The render did not start");
		});
		for (int i = 0; i < 400 && context.computeOnClient(minecraft -> PretupidClient.get().cinematic().rendering()); i++) {
			context.waitTick();
		}
		context.waitTicks(20);
		context.runOnClient(minecraft -> {
			check(renderFolder[0] != null, "The render did not finish");
			try (var files = java.nio.file.Files.list(renderFolder[0])) {
				long count = files.filter(path -> path.getFileName().toString().endsWith(".png")).count();
				check(count >= 30, "Only " + count + " frames were rendered");
			} catch (java.io.IOException exception) {
				throw new AssertionError("The render folder cannot be read", exception);
			}
		});
		context.setScreen(() -> null);
		context.runOnClient(minecraft -> {
			PretupidClient.get().cinematic().deleteShot();
			PretupidClient.get().cinematic().deleteShot();
			PretupidClient.get().modules().get(CinematicModule.class).setEnabled(false);
		});
		world.getServer().runCommand("tp @a " + feet.getX() + " " + feet.getY() + " " + feet.getZ() + " 180 10");
		context.waitTicks(5);
	}

	private static void testFreecam(ClientGameTestContext context, TestSingleplayerContext world, BlockPos feet) {
		context.runOnClient(minecraft -> {
			FreecamModule freecam = PretupidClient.get().modules().get(FreecamModule.class);
			freecam.setEnabled(true);
			freecam.toggleActive(minecraft);
			check(freecam.active(), "Freecam did not start in singleplayer");
		});
		var before = context.computeOnClient(minecraft -> minecraft.player.position());
		context.getInput().holdKey(options -> options.keyUp);
		context.getInput().holdKey(options -> options.keyJump);
		context.waitTicks(25);
		context.getInput().releaseKey(options -> options.keyUp);
		context.getInput().releaseKey(options -> options.keyJump);
		context.waitTicks(10);
		context.runOnClient(minecraft -> {
			var after = minecraft.player.position();
			check(after.distanceTo(before) < 0.2, "The player moved while Freecam was active");
			var camera = Platform.cameraPosition();
			check(camera.distanceTo(minecraft.player.getEyePosition()) > 3, "The Freecam camera did not fly away");
		});
		context.takeScreenshot("pretupid-freecam");
		context.runOnClient(minecraft -> {
			FreecamModule freecam = PretupidClient.get().modules().get(FreecamModule.class);
			freecam.toggleActive(minecraft);
			freecam.setEnabled(false);
		});
		context.waitTicks(5);
	}

	private static void testWaypoints(ClientGameTestContext context, TestSingleplayerContext world, BlockPos feet) {
		context.runOnClient(minecraft -> {
			WaypointsModule waypoints = PretupidClient.get().modules().get(WaypointsModule.class);
			waypoints.setEnabled(true);
			waypoints.add(minecraft, "Game test base");
			check(waypoints.names(minecraft).contains("Game test base"), "Waypoint was not saved");
		});
		world.getServer().runCommand("tp @a " + (feet.getX() + 40) + " " + feet.getY() + " " + (feet.getZ() + 25));
		context.waitTicks(10);
		lookAt(context, feet.offset(0, 1, 0));
		context.waitTicks(10);
		context.takeScreenshot("pretupid-waypoints");
		context.runOnClient(minecraft -> PretupidClient.get().modules().get(WaypointsModule.class).add(minecraft, "Camp"));
		world.getServer().runCommand("tp @a " + (feet.getX() + 12) + " " + feet.getY() + " " + (feet.getZ() + 6));
		context.waitTicks(10);
		lookAt(context, feet.offset(0, 1, 0));
		context.waitTicks(10);
		context.takeScreenshot("pretupid-waypoints-edge");
		context.runOnClient(minecraft -> PretupidClient.get().modules().get(WaypointsModule.class).remove(minecraft, "Camp"));
		context.setScreen(() -> new WaypointsScreen(null));
		context.waitTicks(5);
		context.takeScreenshot("pretupid-waypoints-manager");
		context.setScreen(() -> null);
		world.getServer().runCommand("tp @a " + feet.getX() + " " + feet.getY() + " " + feet.getZ() + " 180 10");
		context.runOnClient(minecraft -> {
			WaypointsModule waypoints = PretupidClient.get().modules().get(WaypointsModule.class);
			waypoints.remove(minecraft, "Game test base");
			check(!waypoints.names(minecraft).contains("Game test base"), "Waypoint was not removed");
		});
		context.waitTicks(20);
	}

	private static void testNewModules(ClientGameTestContext context, TestSingleplayerContext world, BlockPos feet) {
		world.getServer().runCommand("gamemode creative @a");
		context.runOnClient(minecraft -> {
			var modules = PretupidClient.get().modules();
			modules.get(WaypointsModule.class).setEnabled(true);
			modules.get(net.pretupid.client.module.utility.MinimapModule.class).setEnabled(true);
			modules.get(net.pretupid.client.module.hud.NowPlayingModule.class).setEnabled(true);
			modules.get(net.pretupid.client.module.hud.SpeedrunTimerModule.class).setEnabled(true);
			WaypointsModule waypoints = modules.get(WaypointsModule.class);
			waypoints.add(minecraft, "Here");
			modules.get(net.pretupid.client.module.hud.SpeedrunTimerModule.class).commandStart(minecraft);
			var music = PretupidClient.get().music();
			var tracks = music.catalog().tracks(minecraft);
			check(tracks.size() > 40, "Music catalog is incomplete: " + tracks.size());
			var track = tracks.stream().filter(entry -> entry.title().equals("Sweden")).findFirst().orElse(tracks.get(0));
			music.play(minecraft, track);
			check(track.equals(music.current(minecraft)), "Chosen track is not current");
			music.enqueue(minecraft, tracks.get(1));
			check(music.queue().size() == 1, "Track was not queued");
		});
		String[][] spots = {{"0", "-30"}, {"24", "-24"}, {"30", "0"}, {"24", "24"}, {"0", "30"}, {"-24", "24"}, {"-30", "0"}, {"-24", "-24"}};
		for (int i = 0; i < spots.length; i++) {
			int x = feet.getX() + Integer.parseInt(spots[i][0]);
			int z = feet.getZ() + Integer.parseInt(spots[i][1]);
			int index = i;
			world.getServer().runCommand("tp @a " + x + " " + feet.getY() + " " + z);
			context.waitTicks(3);
			context.runOnClient(minecraft -> PretupidClient.get().modules().get(WaypointsModule.class).add(minecraft, "Dir " + index));
		}
		world.getServer().runCommand("tp @a " + feet.getX() + " " + feet.getY() + " " + feet.getZ() + " 0 20");
		context.waitTicks(80);
		context.takeScreenshot("pretupid-new-modules");
		context.setScreen(() -> new net.pretupid.client.ui.screen.MusicScreen(null));
		context.waitTicks(5);
		context.takeScreenshot("pretupid-music-player");
		context.setScreen(() -> null);
		world.getServer().runCommand("tp @a " + feet.getX() + " " + feet.getY() + " " + feet.getZ() + " 45 20");
		context.runOnClient(minecraft -> {
			var music = PretupidClient.get().music();
			music.skip(minecraft);
			check(music.queue().isEmpty(), "Skip did not advance the queue");
			setNowPlayingWhenMuted(true);
		});
		context.waitTicks(30);
		context.takeScreenshot("pretupid-now-playing");
		context.runOnClient(minecraft -> {
			var music = PretupidClient.get().music();
			check(music.current(minecraft) != null && music.overridesVanilla(), "Queued track stopped playing");
			setNowPlayingWhenMuted(false);
			music.useDynamic(minecraft);
			check(!music.overridesVanilla(), "Dynamic music was not restored");
		});
		world.getServer().runCommand("execute in minecraft:the_nether run tp @a " + feet.getX() + " 70 " + feet.getZ());
		context.waitTicks(60);
		context.runOnClient(minecraft -> {
			WaypointsModule waypoints = PretupidClient.get().modules().get(WaypointsModule.class);
			check(waypoints.locatedHere(minecraft).isEmpty(), "Overworld waypoints are visible in the Nether");
		});
		context.takeScreenshot("pretupid-minimap-nether");
		world.getServer().runCommand("execute in minecraft:the_end run tp @a 0 70 0");
		context.waitTicks(40);
		context.runOnClient(minecraft -> {
			WaypointsModule waypoints = PretupidClient.get().modules().get(WaypointsModule.class);
			check(waypoints.locatedHere(minecraft).isEmpty(), "Overworld waypoints are visible in the End");
		});
		world.getServer().runCommand("execute in minecraft:overworld run tp @a " + feet.getX() + " " + feet.getY() + " " + feet.getZ() + " 180 10");
		world.getServer().runCommand("gamemode survival @a");
		context.waitTicks(40);
		context.runOnClient(minecraft -> {
			var modules = PretupidClient.get().modules();
			WaypointsModule waypoints = modules.get(WaypointsModule.class);
			waypoints.remove(minecraft, "Here");
			for (int i = 0; i < 8; i++) {
				waypoints.remove(minecraft, "Dir " + i);
			}
			modules.get(net.pretupid.client.module.hud.SpeedrunTimerModule.class).commandReset(minecraft);
			modules.get(net.pretupid.client.module.hud.SpeedrunTimerModule.class).setEnabled(false);
			modules.get(net.pretupid.client.module.hud.NowPlayingModule.class).setEnabled(false);
			modules.get(net.pretupid.client.module.utility.MinimapModule.class).setEnabled(false);
		});
		context.waitTicks(5);
	}

	private static void setNowPlayingWhenMuted(boolean value) {
		PretupidClient.get().modules().get(net.pretupid.client.module.hud.NowPlayingModule.class).settings().stream()
			.filter(setting -> setting.id().equals("show_when_muted"))
			.map(net.pretupid.client.setting.BooleanSetting.class::cast)
			.forEach(setting -> setting.set(value));
	}

	private static void testScreenies(ClientGameTestContext context) {
		int windowWidth = context.computeOnClient(minecraft -> minecraft.getWindow().getWidth());
		calm(context);
		context.runOnClient(minecraft -> PretupidClient.get().screenies().request());
		for (int i = 0; i < 200 && !context.computeOnClient(minecraft -> Platform.screen() instanceof ScreenieScreen); i++) {
			context.waitTick();
		}
		context.runOnClient(minecraft -> {
			check(Platform.screen() instanceof ScreenieScreen, "The screenie studio did not open");
			try {
				ScreenieScreen studio = (ScreenieScreen) Platform.screen();
				Field sourceField = ScreenieScreen.class.getDeclaredField("source");
				sourceField.setAccessible(true);
				java.awt.image.BufferedImage source = (java.awt.image.BufferedImage) sourceField.get(studio);
				check(source.getWidth() == windowWidth * 2, "The screenie is " + source.getWidth() + " wide instead of " + windowWidth * 2);
			} catch (ReflectiveOperationException exception) {
				throw new AssertionError("The screenie studio is not reachable", exception);
			}
		});
		context.waitTicks(30);
		context.takeScreenshot("pretupid-screenie-studio");
		context.setScreen(() -> null);
		context.waitTicks(5);
		context.runOnClient(minecraft -> PretupidClient.get().screenies().requestMeme());
		for (int i = 0; i < 200 && !context.computeOnClient(minecraft -> Platform.screen() instanceof ScreenieScreen); i++) {
			context.waitTick();
		}
		context.runOnClient(minecraft -> {
			check(Platform.screen() instanceof ScreenieScreen, "The meme editor did not open");
			fillMeme((ScreenieScreen) Platform.screen(), "Top text test", "Bottom text test");
		});
		context.waitTicks(30);
		context.takeScreenshot("pretupid-meme-editor");
		context.runOnClient(minecraft -> {
			var module = PretupidClient.get().modules().get(net.pretupid.client.module.utility.ScreeniesModule.class);
			module.exportAspect().set(net.pretupid.client.module.utility.ScreeniesModule.ExportAspect.SQUARE);
			try {
				var exported = ScreenieScreen.class.getDeclaredMethod("exported");
				exported.setAccessible(true);
				var image = (java.awt.image.BufferedImage) exported.invoke(Platform.screen());
				check(image.getWidth() == 1080 && image.getHeight() == 1080, "A square 1K export is " + image.getWidth() + "x" + image.getHeight());
			} catch (ReflectiveOperationException exception) {
				throw new AssertionError("The export settings are not reachable", exception);
			}
		});
		context.waitTicks(10);
		context.takeScreenshot("pretupid-meme-square");
		context.runOnClient(minecraft -> PretupidClient.get().modules().get(net.pretupid.client.module.utility.ScreeniesModule.class).exportAspect()
			.set(net.pretupid.client.module.utility.ScreeniesModule.ExportAspect.ORIGINAL));
		for (String shot : new String[] {"FACE", "WORM", "DUTCH"}) {
			Object before = context.computeOnClient(minecraft -> Platform.screen());
			context.runOnClient(minecraft -> {
				try {
					var retake = ScreenieScreen.class.getDeclaredMethod("retake", net.pretupid.client.screenie.MemeShot.class);
					retake.setAccessible(true);
					retake.invoke(Platform.screen(), net.pretupid.client.screenie.MemeShot.valueOf(shot));
				} catch (ReflectiveOperationException exception) {
					throw new AssertionError("The meme editor cannot retake shots", exception);
				}
			});
			context.waitTicks(2);
			context.runOnClient(minecraft -> check(Platform.screen() instanceof ScreenieScreen, "The meme editor closed during a retake"));
			for (int i = 0; i < 200 && context.computeOnClient(minecraft -> Platform.screen() == before); i++) {
				context.waitTick();
			}
			context.runOnClient(minecraft -> {
				check(Platform.screen() instanceof ScreenieScreen && Platform.screen() != before, "The meme editor did not show the retaken picture");
				try {
					Field field = ScreenieScreen.class.getDeclaredField("slotInputs");
					field.setAccessible(true);
					var inputs = (java.util.List<?>) field.get(Platform.screen());
					check("Top text test".equals(((net.pretupid.client.ui.TextInput) inputs.get(0)).value()), "The meme text was lost after a retake");
				} catch (ReflectiveOperationException exception) {
					throw new AssertionError("The meme editor is not reachable", exception);
				}
			});
			context.waitTicks(20);
			context.takeScreenshot("pretupid-meme-" + shot.toLowerCase(java.util.Locale.ROOT));
		}
		long before = context.computeOnClient(minecraft -> countScreenies());
		context.runOnClient(minecraft -> {
			try {
				ScreenieScreen studio = (ScreenieScreen) Platform.screen();
				Field sourceField = ScreenieScreen.class.getDeclaredField("source");
				sourceField.setAccessible(true);
				java.awt.image.BufferedImage source = (java.awt.image.BufferedImage) sourceField.get(studio);
				PretupidClient.get().screenies().save(source, net.pretupid.client.screenie.ScreenieLook.NATURAL.withFilter(net.pretupid.client.screenie.ScreenieLook.Filter.VIVID),
					MemeTemplate.CLASSIC.layers(java.util.List.of("Top text test", "Bottom text test")), MemeTemplate.CLASSIC);
			} catch (ReflectiveOperationException exception) {
				throw new AssertionError("The screenie studio is not reachable", exception);
			}
		});
		for (int i = 0; i < 200 && context.computeOnClient(minecraft -> countScreenies()) <= before; i++) {
			context.waitTick();
		}
		check(context.computeOnClient(minecraft -> countScreenies()) > before, "The screenie was not saved");
		context.setScreen(() -> null);
		context.waitTicks(5);
	}

	@SuppressWarnings("unchecked")
	private static void fillMeme(ScreenieScreen screen, String top, String bottom) {
		try {
			Field field = ScreenieScreen.class.getDeclaredField("slotInputs");
			field.setAccessible(true);
			java.util.List<net.pretupid.client.ui.TextInput> inputs = (java.util.List<net.pretupid.client.ui.TextInput>) field.get(screen);
			check(inputs.size() == 2, "The meme editor has " + inputs.size() + " text fields instead of 2");
			inputs.get(0).setValue(top);
			inputs.get(1).setValue(bottom);
		} catch (ReflectiveOperationException exception) {
			throw new AssertionError("The meme editor is not reachable", exception);
		}
	}

	private static long countScreenies() {
		java.nio.file.Path folder = net.pretupid.client.screenie.Screenies.folder();
		if (!java.nio.file.Files.isDirectory(folder)) {
			return 0;
		}
		try (var files = java.nio.file.Files.list(folder)) {
			return files.filter(path -> path.toString().endsWith(".png")).count();
		} catch (java.io.IOException exception) {
			return 0;
		}
	}

	private static void testCosmetics(ClientGameTestContext context) {
		context.runOnClient(minecraft -> {
			ClientSettings settings = PretupidClient.get().settings();
			enable(minecraft, OwnNametagModule.class);
			var rainbow = NametagPresets.find("rainbow");
			check(rainbow.isPresent(), "The rainbow nametag preset is missing");
			settings.applyNametagStyle(rainbow.get().style());
			check(settings.nametagStyle().effect().moves(), "Applying a nametag preset did not change the style");
			settings.nametagEffect.set(NameEffect.SHAKE);
			check(settings.nametagStyle().effect() == NameEffect.SHAKE && settings.nametagStyle().animation() == NameAnimation.RAINBOW,
				"Customizing a preset did not keep the other fields");
			settings.hat.set("crown");
			settings.cape.set("catalog:pretupid");
			minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
		});
		look(context, 180f, 10f);
		for (int i = 0; i < 200 && !context.computeOnClient(minecraft -> PretupidClient.get().cosmetics().ownCapeTexture() != null
			&& PretupidClient.get().cosmetics().capes().get(PretupidClient.get().cosmetics().ownCapeTexture()) != null); i++) {
			context.waitTick();
		}
		context.waitTicks(10);
		context.takeScreenshot("pretupid-cape-still");
		context.getInput().holdKey(options -> options.keyUp);
		context.waitTicks(14);
		context.takeScreenshot("pretupid-cape-running");
		context.getInput().releaseKey(options -> options.keyUp);
		context.waitTicks(4);
		context.takeScreenshot("pretupid-cape-stopping");
		context.waitTicks(30);
		for (String back : wearableIds(Catalog.WearableKind.BACK)) {
			context.runOnClient(minecraft -> PretupidClient.get().settings().back.set(back));
			context.waitTicks(15);
			context.takeScreenshot("pretupid-back-" + back);
		}
		context.runOnClient(minecraft -> {
			PretupidClient.get().settings().back.set("");
			minecraft.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
		});
		for (String pet : wearableIds(Catalog.WearableKind.PET)) {
			context.runOnClient(minecraft -> PretupidClient.get().settings().pet.set(pet));
			context.waitTicks(15);
			context.takeScreenshot("pretupid-pet-" + pet);
		}
		context.getInput().holdKey(options -> options.keyLeft);
		context.waitTicks(10);
		context.takeScreenshot("pretupid-pet-following");
		context.getInput().releaseKey(options -> options.keyLeft);
		context.waitTicks(20);
		context.takeScreenshot("pretupid-nametag-wave");
		context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
		context.setScreen(() -> {
			ClientMenuScreen menu = new ClientMenuScreen(null);
			menu.select(ClientMenuScreen.Tab.COSMETICS);
			showCapeSection(menu);
			return menu;
		});
		context.waitTicks(30);
		context.takeScreenshot("pretupid-cosmetics-capes");
		context.setScreen(() -> null);
	}

	private static void showCapeSection(ClientMenuScreen menu) {
		showSection(menu, "CAPE");
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static void showSection(ClientMenuScreen menu, String name) {
		try {
			Field pages = ClientMenuScreen.class.getDeclaredField("pages");
			pages.setAccessible(true);
			Object page = ((java.util.Map<?, ?>) pages.get(menu)).get(ClientMenuScreen.Tab.COSMETICS);
			Field section = page.getClass().getDeclaredField("section");
			section.setAccessible(true);
			section.set(page, Enum.valueOf((Class) section.getType(), name));
		} catch (ReflectiveOperationException exception) {
			throw new AssertionError("Cosmetics page is not reachable", exception);
		}
	}

	private static void testTexturedWearable(ClientGameTestContext context) {
		context.runOnClient(minecraft -> {
			com.google.gson.JsonObject model = com.google.gson.JsonParser.parseString(
				"{\"textureWidth\":16,\"textureHeight\":16,\"nodes\":[{\"name\":\"pet\",\"boxes\":["
					+ "{\"from\":[-2,-2,-2],\"size\":[4,4,4],\"uv\":[0,0]},"
					+ "{\"from\":[-1,2,-1],\"size\":[2,1,2],\"color\":\"#FF0000\"}],"
					+ "\"animations\":[{\"property\":\"y\",\"wave\":\"sine\",\"amplitude\":1,\"speed\":0.1}]}]}").getAsJsonObject();
			com.mojang.blaze3d.platform.NativeImage texture = new com.mojang.blaze3d.platform.NativeImage(16, 16, true);
			texture.fillRect(0, 0, 16, 16, 0xFF3366FF);
			net.pretupid.client.cosmetic.model.Shape shape = net.pretupid.client.cosmetic.model.WearableParser.parse(model, net.pretupid.client.cosmetic.model.Shape.Anchor.ROOT, true);
			net.pretupid.client.cosmetic.model.ShapeBaker.Baked baked = net.pretupid.client.cosmetic.model.ShapeBaker.bake(shape, texture);
			check(baked.texture().getWidth() >= 16 && baked.texture().getHeight() > 16, "Textured wearable did not reserve room for painted boxes");
			check(baked.texture().getPixel(5, 5) == 0xFF3366FF, "Uploaded texture was not copied into the wearable texture");
			check(baked.root().getChild("pet") != null, "Textured wearable lost its node");
			baked.texture().close();
			texture.close();
			boolean known = PretupidClient.get().cosmetics().catalog().wearable(Catalog.WearableKind.HAT, "crown").isPresent();
			check(known, "The crown is missing from the wearable catalog");
		});
	}

	private static java.util.List<String> wearableIds(Catalog.WearableKind kind) {
		return PretupidClient.get().cosmetics().catalog().wearables(kind).stream().map(Catalog.WearableEntry::id).toList();
	}

	private static void testDeadPose(ClientGameTestContext context, TestSingleplayerContext world, BlockPos feet) {
		Catalog.EmoteEntry dead;
		try (var stream = PretupidClientGameTest.class.getResourceAsStream("/dead_pose.json")) {
			var json = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
			dead = new Catalog.EmoteEntry("dead_pose", "Dead Pose", "", EmoteAnimation.fromJson(json));
		} catch (java.io.IOException exception) {
			throw new AssertionError("Dead pose data is missing", exception);
		}
		check(dead.animation().hold() && dead.animation().sneakOnly(), "Dead pose does not hold until sneaking");
		world.getServer().runCommand("tp @a " + feet.getX() + " " + feet.getY() + " " + feet.getZ() + " 90 0");
		context.waitTicks(5);
		context.runOnClient(minecraft -> {
			ClientSettings settings = PretupidClient.get().settings();
			settings.hat.set("");
			settings.back.set("");
			settings.pet.set("");
			settings.cape.set("");
			PretupidClient.get().emotes().playLocal(dead);
			minecraft.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
		});
		context.waitTicks(14);
		context.takeScreenshot("pretupid-dead-pose-falling");
		context.waitTicks(80);
		boolean holding = context.computeOnClient(minecraft -> PretupidClient.get().emotes().playing(minecraft.player.getUUID()));
		check(holding, "Dead pose ended before sneaking");
		context.takeScreenshot("pretupid-dead-pose-front");
		context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		look(context, 180f, 40f);
		context.waitTicks(5);
		context.takeScreenshot("pretupid-dead-pose-side");
		context.getInput().holdKey(options -> options.keyUp);
		context.waitTicks(10);
		context.getInput().releaseKey(options -> options.keyUp);
		check(context.computeOnClient(minecraft -> PretupidClient.get().emotes().playing(minecraft.player.getUUID())), "Walking stopped the dead pose");
		context.getInput().holdShift();
		context.getInput().holdKey(options -> options.keyShift);
		context.waitTicks(3);
		context.getInput().releaseKey(options -> options.keyShift);
		context.getInput().releaseShift();
		check(!context.computeOnClient(minecraft -> PretupidClient.get().emotes().playing(minecraft.player.getUUID())), "Sneaking did not end the dead pose");
		context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
		world.getServer().runCommand("tp @a " + feet.getX() + " " + feet.getY() + " " + feet.getZ() + " 180 10");
		context.waitTicks(5);
	}

	private static void testEmotes(ClientGameTestContext context) {
		Catalog.EmoteEntry emote = context.computeOnClient(minecraft -> PretupidClient.get().cosmetics().catalog().emotes().stream().findFirst().orElse(null));
		if (emote == null) {
			return;
		}
		context.runOnClient(minecraft -> PretupidClient.get().emotes().playLocal(emote));
		context.getInput().holdKey(options -> options.keyUp);
		context.waitTicks(10);
		context.getInput().releaseKey(options -> options.keyUp);
		boolean walking = context.computeOnClient(minecraft -> PretupidClient.get().emotes().playing(minecraft.player.getUUID()));
		check(walking, "Walking stopped the emote");
		context.getInput().holdShift();
		context.getInput().holdKey(options -> options.keyShift);
		context.waitTicks(3);
		context.getInput().releaseKey(options -> options.keyShift);
		context.getInput().releaseShift();
		boolean sneaking = context.computeOnClient(minecraft -> PretupidClient.get().emotes().playing(minecraft.player.getUUID()));
		check(!sneaking, "Sneaking did not stop the emote");
		context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
	}

	private static void testEmoteWheel(ClientGameTestContext context) {
		int available = context.computeOnClient(minecraft -> PretupidClient.get().cosmetics().catalog().emotes().size());
		if (available == 0) {
			return;
		}
		context.runOnClient(minecraft -> {
			PretupidClient client = PretupidClient.get();
			client.settings().emoteWheel.set("");
			EmoteWheel wheel = client.emoteWheel();
			Catalog catalog = client.cosmetics().catalog();
			java.util.List<Catalog.EmoteEntry> filled = wheel.filled(catalog);
			check(filled.size() == Math.min(EmoteWheel.SIZE, catalog.emotes().size()), "The wheel did not start with the first emotes, it has " + filled.size());
			check(filled.getFirst().id().equals(catalog.emotes().getFirst().id()), "The first wheel slot is not the first emote");
			String first = wheel.slotIds()[0];
			String second = wheel.slotIds()[1];
			wheel.swap(0, 1);
			check(wheel.slotIds()[0].equals(second) && wheel.slotIds()[1].equals(first), "Swapping wheel slots did not work");
			wheel.swap(0, 1);
			wheel.clear(5);
			check(wheel.slotIds()[5].isEmpty(), "Clearing a wheel slot did not work");
			if (catalog.emotes().size() > EmoteWheel.SIZE) {
				wheel.assign(5, catalog.emotes().get(EmoteWheel.SIZE).id());
				check(wheel.slotOf(catalog.emotes().get(EmoteWheel.SIZE).id()) == 5, "Assigning a wheel slot did not work");
			}
			wheel.assign(2, first);
			check(wheel.slotIds()[2].equals(first) && wheel.slotOf(first) == 2, "Assigning an emote that is already in the wheel did not move it");
			client.settings().emoteWheel.set("");
			wheel.ensureDefaults(catalog);
		});
		context.setScreen(EmoteWheelScreen::new);
		context.waitTicks(20);
		context.takeScreenshot("pretupid-emote-wheel");
		double[] target = context.computeOnClient(minecraft -> {
			double scale = minecraft.getWindow().getGuiScale();
			double angle = -Math.PI / 2 + Math.PI * 2 / 6;
			return new double[] {
				minecraft.getWindow().getScreenWidth() / 2.0 + 72 * Math.cos(angle) * scale,
				minecraft.getWindow().getScreenHeight() / 2.0 + 72 * Math.sin(angle) * 0.82 * scale
			};
		});
		context.getInput().setCursorPos(target[0], target[1]);
		context.waitTicks(10);
		context.takeScreenshot("pretupid-emote-wheel-hover");
		context.setScreen(() -> null);
	}

	private static void testNotifications(ClientGameTestContext context) {
		for (int i = 1; i <= 4; i++) {
			int index = i;
			context.runOnClient(minecraft -> PretupidClient.get().notifications().push(Component.literal("Alert " + index),
				Component.literal("Quick alert number " + index), Icon.item("bell"), null, 0xFF4ADE80, 1800L));
			context.waitTicks(20);
		}
		context.takeScreenshot("pretupid-quick-alerts");
	}

	private static void showcase(ClientGameTestContext context, TestSingleplayerContext world, BlockPos feet) {
		context.runOnClient(minecraft -> minecraft.options.guiScale().set(3));
		context.getInput().resizeWindow(1600, 900);
		context.runOnClient(minecraft -> {
			ClientSettings settings = PretupidClient.get().settings();
			settings.hat.set("crown");
			settings.back.set("angel_wings");
			settings.pet.set("ghost");
			settings.cape.set("catalog:pretupid");
			settings.applyNametagStyle(NametagPresets.find("rainbow").orElseThrow().style());
			enable(minecraft, OwnNametagModule.class);
			List<Class<? extends Module>> shown = List.of(net.pretupid.client.module.hud.FpsHud.class, net.pretupid.client.module.hud.KeystrokesHud.class,
				net.pretupid.client.module.hud.ArmorHud.class, net.pretupid.client.module.hud.DirectionHud.class);
			for (Module module : PretupidClient.get().modules().all()) {
				if (module instanceof net.pretupid.client.hud.HudModule hud) {
					hud.position().reset();
					hud.setScale(1.0);
					hud.setEnabled(shown.contains(hud.getClass()));
				}
			}
		});
		world.getServer().runCommand("time set 12300");
		world.getServer().runCommand("clear @a");
		world.getServer().runCommand("give @a minecraft:diamond_sword");
		world.getServer().runCommand("item replace entity @a armor.chest with minecraft:diamond_chestplate");
		world.getServer().runCommand("item replace entity @a armor.legs with minecraft:iron_leggings");
		world.getServer().runCommand("tp @a " + feet.getX() + " " + feet.getY() + " " + feet.getZ() + " 160 8");
		context.waitTicks(40);
		context.runOnClient(minecraft -> {
			minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			PretupidClient.get().modules().get(CinematicModule.class).setEnabled(true);
			var director = PretupidClient.get().cinematic();
			director.newShot();
			net.minecraft.world.phys.Vec3 eye = minecraft.player.getEyePosition();
			double yaw = Math.toRadians(minecraft.player.getYRot());
			net.minecraft.world.phys.Vec3 forward = new net.minecraft.world.phys.Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
			net.minecraft.world.phys.Vec3 right = new net.minecraft.world.phys.Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
			net.minecraft.world.phys.Vec3 camera = eye.add(forward.scale(3.4)).add(right.scale(-1.8)).add(0, 0.35, 0);
			net.minecraft.world.phys.Vec3 delta = eye.add(0, -0.35, 0).subtract(camera);
			float cameraYaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
			float cameraPitch = (float) -Math.toDegrees(Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z)));
			director.current().add(new net.pretupid.client.cinematic.CameraKeyframe(0, camera, cameraYaw, cameraPitch, 0f, 62f,
				net.pretupid.client.cinematic.CameraKeyframe.Ease.SMOOTH));
			director.preview(0);
		});
		context.waitTicks(20);
		calm(context);
		context.takeScreenshot("showcase-ingame");
		context.runOnClient(minecraft -> {
			PretupidClient.get().cinematic().stop();
			PretupidClient.get().cinematic().deleteShot();
			minecraft.options.setCameraType(CameraType.FIRST_PERSON);
		});
		context.setScreen(() -> Platform.chatScreen("on my way :running:"));
		context.waitTicks(5);
		context.runOnClient(minecraft -> {
			Platform.chat().clearMessages(false);
			Platform.addChatMessage(Component.literal("<Alex> who wants to go mining? :pick:"));
			Platform.addChatMessage(Component.literal("<Player0> gg :fire: that base is insane :sob:"));
			Platform.addChatMessage(Component.literal("<Steve> :joy: :joy: :100:"));
		});
		context.waitTicks(10);
		context.takeScreenshot("showcase-emoji");
		context.setScreen(() -> null);
		calm(context);
		world.getServer().runCommand("tp @a " + feet.getX() + " " + feet.getY() + " " + feet.getZ() + " 160 0");
		double behindX = Math.sin(Math.toRadians(160)) * 1.8 + Math.cos(Math.toRadians(160)) * 0.9;
		double behindZ = -Math.cos(Math.toRadians(160)) * 1.8 + Math.sin(Math.toRadians(160)) * 0.9;
		world.getServer().runCommand("summon minecraft:creeper " + (feet.getX() + 0.5 + behindX) + " " + feet.getY() + " " + (feet.getZ() + 0.5 + behindZ) + " {NoAI:1b,Silent:1b,Rotation:[160f,0f]}");
		context.runOnClient(minecraft -> {
			PretupidClient.get().modules().get(OwnNametagModule.class).setEnabled(false);
			PretupidClient.get().settings().pet.set("");
		});
		context.waitTicks(20);
		context.runOnClient(minecraft -> PretupidClient.get().screenies().requestMeme(net.pretupid.client.screenie.MemeShot.FACE,
			net.pretupid.client.screenie.MemeTemplate.CLASSIC, List.of("When you hear", "Ssssssss")));
		for (int i = 0; i < 200 && !context.computeOnClient(minecraft -> Platform.screen() instanceof ScreenieScreen); i++) {
			context.waitTick();
		}
		context.waitTicks(40);
		calm(context);
		context.takeScreenshot("showcase-screenie");
		world.getServer().runCommand("kill @e[type=minecraft:creeper]");
		context.runOnClient(minecraft -> {
			enable(minecraft, OwnNametagModule.class);
			PretupidClient.get().settings().pet.set("ghost");
		});
		context.setScreen(() -> null);
		context.runOnClient(minecraft -> PretupidClient.get().modules().get(CinematicModule.class).setEnabled(true));
		world.getServer().runCommand("tp @a " + feet.getX() + " " + feet.getY() + " " + feet.getZ() + " 160 8");
		context.waitTicks(5);
		context.runOnClient(minecraft -> PretupidClient.get().cinematic().addTemplate(net.pretupid.client.cinematic.ShotTemplate.ORBIT, minecraft));
		world.getServer().runCommand("gamemode spectator @a");
		world.getServer().runCommand("tp @a " + feet.getX() + " " + (feet.getY() + 9) + " " + (feet.getZ() + 13) + " 180 34");
		context.waitTicks(10);
		context.setScreen(CinematicEditorScreen::new);
		context.waitTicks(10);
		calm(context);
		context.takeScreenshot("showcase-cinematic");
		context.setScreen(() -> null);
		context.runOnClient(minecraft -> PretupidClient.get().cinematic().deleteShot());
		world.getServer().runCommand("gamemode survival @a");
		world.getServer().runCommand("tp @a " + feet.getX() + " " + feet.getY() + " " + feet.getZ() + " 160 8");
		world.getServer().runCommand("effect give @a minecraft:instant_health 1 10 true");
		context.waitTicks(10);
		context.setScreen(() -> {
			ClientMenuScreen menu = new ClientMenuScreen(null);
			menu.select(ClientMenuScreen.Tab.COSMETICS);
			showSection(menu, "NAMETAG");
			return menu;
		});
		context.waitTicks(30);
		calm(context);
		context.takeScreenshot("showcase-menu");
		context.setScreen(() -> null);
		world.getServer().runCommand("time set 1500");
		context.runOnClient(minecraft -> {
			WaypointsModule waypoints = PretupidClient.get().modules().get(WaypointsModule.class);
			waypoints.setEnabled(true);
			for (String name : List.copyOf(waypoints.names(minecraft))) {
				waypoints.remove(minecraft, name);
			}
			waypoints.add(minecraft, "Home");
		});
		world.getServer().runCommand("tp @a " + (feet.getX() - 18) + " " + (feet.getY() + 3) + " " + (feet.getZ() - 22));
		context.waitTicks(4);
		context.runOnClient(minecraft -> PretupidClient.get().modules().get(WaypointsModule.class).add(minecraft, "Diamond mine"));
		world.getServer().runCommand("tp @a " + (feet.getX() + 12) + " " + feet.getY() + " " + (feet.getZ() - 12));
		context.waitTicks(4);
		context.runOnClient(minecraft -> PretupidClient.get().modules().get(WaypointsModule.class).add(minecraft, "Villager trades"));
		world.getServer().runCommand("tp @a " + (feet.getX() + 4) + " " + (feet.getY() + 1) + " " + (feet.getZ() + 18) + " 175 12");
		context.waitTicks(20);
		calm(context);
		context.takeScreenshot("showcase-waypoints");
	}

	private static void calm(ClientGameTestContext context) {
		context.runOnClient(minecraft -> {
			clearToasts(minecraft);
			Platform.chat().clearMessages(false);
		});
		context.waitTicks(2);
	}

	private static void clearToasts(Minecraft minecraft) {
		for (Object[] route : new Object[][] {{minecraft.gui, "toastManager"}, {minecraft, "getToastManager"}}) {
			try {
				Object toasts = route[0].getClass().getMethod((String) route[1]).invoke(route[0]);
				toasts.getClass().getMethod("clear").invoke(toasts);
				return;
			} catch (ReflectiveOperationException ignored) {
			}
		}
	}

	private static void testMenuPages(ClientGameTestContext context) {
		for (String section : List.of("EMOTES", "NAMETAG")) {
			context.setScreen(() -> {
				ClientMenuScreen menu = new ClientMenuScreen(null);
				menu.select(ClientMenuScreen.Tab.COSMETICS);
				showSection(menu, section);
				return menu;
			});
			context.waitTicks(30);
			context.takeScreenshot("pretupid-cosmetics-" + section.toLowerCase());
		}
		context.setScreen(() -> {
			ClientMenuScreen menu = new ClientMenuScreen(null);
			menu.select(ClientMenuScreen.Tab.MODULES);
			return menu;
		});
		context.waitTicks(30);
		context.takeScreenshot("pretupid-modules");
		context.setScreen(() -> {
			ClientMenuScreen menu = new ClientMenuScreen(null);
			menu.select(ClientMenuScreen.Tab.SETTINGS);
			return menu;
		});
		context.waitTicks(30);
		context.takeScreenshot("pretupid-client-settings");
		context.setScreen(() -> null);
	}

	private static void testHudEditor(ClientGameTestContext context) {
		context.getInput().pressKey(InputConstants.KEY_H);
		context.waitForScreen(HudEditorScreen.class);
		context.waitTicks(5);
		context.takeScreenshot("pretupid-hud-editor");
		context.getInput().pressKey(InputConstants.KEY_H);
		context.waitForScreen(null);
	}

	private static void testHosting(ClientGameTestContext context) {
		LocalHosting.Share share = context.computeOnClient(minecraft -> PretupidClient.get().hosting().openToFriends()).join();
		check(share != null && !share.address().isBlank(), "Opening the world returned no address");
		int port = context.computeOnClient(minecraft -> minecraft.getSingleplayerServer().getPort());
		boolean published = context.computeOnClient(minecraft -> minecraft.getSingleplayerServer().isPublished());
		check(published && port > 0, "World was not published");
		try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), port)) {
			check(socket.isConnected(), "Hosted world is not reachable");
		} catch (java.io.IOException exception) {
			throw new AssertionError("Hosted world is not reachable on port " + port, exception);
		}
		UUID stranger = UUID.randomUUID();
		boolean strangerAllowed = context.computeOnClient(minecraft -> PretupidClient.get().hosting().mayJoin(stranger));
		check(!strangerAllowed, "A stranger could join a friends-only world");
		context.runOnClient(minecraft -> PretupidClient.get().hosting().invite(stranger));
		boolean invitedAllowed = context.computeOnClient(minecraft -> PretupidClient.get().hosting().mayJoin(stranger));
		check(invitedAllowed, "An invited player could not join");
		check(LocalHosting.tunnelHost(LocalHosting.TUNNEL_PREFIX + stranger).orElseThrow().equals(stranger), "Tunnel address did not round-trip");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}

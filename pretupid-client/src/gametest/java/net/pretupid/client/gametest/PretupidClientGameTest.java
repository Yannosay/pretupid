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
			testEmojiButtons(context);
			testFreecam(context, world, feet);
			testCinematic(context, world, feet);
			testWaypoints(context, world, feet);
			testNewModules(context, world, feet);
			testModuleMarqueeHover(context);
			testWaypointDeleteConfirm(context);
			testSpeedrunFlow(context, world, feet);
			testMusicFlow(context);
			testPresenceSwitch(context);
			testMinimapSprites(context, world, feet);
			testEditorControls(context, world, feet);
			testEditorMouse(context);
			testFreeMoveAndDefaults(context, world, feet);
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
		double[] hover = context.computeOnClient(minecraft -> {
			var widget = Platform.widgets(Platform.screen()).stream().filter(entry -> entry.getWidth() >= 150).min(java.util.Comparator.comparingInt(net.minecraft.client.gui.components.AbstractWidget::getY)).orElseThrow();
			double scale = minecraft.getWindow().getGuiScale();
			return new double[] {(widget.getX() + widget.getWidth() / 2.0) * scale, (widget.getY() + widget.getHeight() / 2.0) * scale};
		});
		context.getInput().setCursorPos(hover[0], hover[1]);
		context.waitTicks(15);
		context.takeScreenshot("pretupid-title-hover");
		context.getInput().setCursorPos(4, 4);
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

	private static EmojiPicker chatPicker() {
		try {
			for (Field field : ChatScreen.class.getDeclaredFields()) {
				if (field.getType() == EmojiPicker.class) {
					field.setAccessible(true);
					return (EmojiPicker) field.get(Platform.screen());
				}
			}
		} catch (ReflectiveOperationException exception) {
			throw new AssertionError(exception);
		}
		throw new AssertionError("Chat has no emoji picker");
	}

	private static Object pickerField(String name) {
		try {
			Field field = EmojiPicker.class.getDeclaredField(name);
			field.setAccessible(true);
			return field.get(chatPicker());
		} catch (ReflectiveOperationException exception) {
			throw new AssertionError(exception);
		}
	}

	private static int pickerPosition(String name, Object... args) {
		try {
			var method = args.length == 0 ? EmojiPicker.class.getDeclaredMethod(name) : EmojiPicker.class.getDeclaredMethod(name, int.class);
			method.setAccessible(true);
			return (int) method.invoke(chatPicker(), args);
		} catch (ReflectiveOperationException exception) {
			throw new AssertionError(exception);
		}
	}

	private static void clickGui(ClientGameTestContext context, double x, double y) {
		double scale = context.computeOnClient(minecraft -> (double) minecraft.getWindow().getGuiScale());
		context.getInput().setCursorPos(x * scale, y * scale);
		context.waitTicks(2);
		context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(2);
	}

	private static void testEmojiButtons(ClientGameTestContext context) {
		context.setScreen(() -> Platform.chatScreen("hi"));
		context.waitTicks(5);
		int[] button = context.computeOnClient(minecraft -> new int[] {pickerPosition("buttonX"), pickerPosition("buttonY")});
		clickGui(context, button[0] + 6, button[1] + 6);
		context.runOnClient(minecraft -> check((boolean) pickerField("open"), "Clicking the emoji button did not open the picker"));
		context.takeScreenshot("pretupid-emoji-open");
		int[] panel = context.computeOnClient(minecraft -> new int[] {pickerPosition("panelX", Platform.screen().width), pickerPosition("panelY")});
		String before = context.computeOnClient(minecraft -> (String) pickerField("category"));
		int tabs = context.computeOnClient(minecraft -> 1 + EmojiIndex.get().categories().size());
		int tabWidth = (9 * 14) / tabs;
		clickGui(context, panel[0] + 4 + 3 * tabWidth + tabWidth / 2.0, panel[1] + 4 + 7);
		context.runOnClient(minecraft -> check(!before.equals(pickerField("category")), "Clicking a category did not switch it"));
		clickGui(context, panel[0] + 4 + 7, panel[1] + 4 + 14 + 2 + 7);
		String inserted = context.computeOnClient(minecraft -> ((net.minecraft.client.gui.components.EditBox) pickerField("input")).getValue());
		check(inserted.matches("hi :[^: ]+: "), "Clicking an emoji did not insert it: \"" + inserted + "\"");
		context.takeScreenshot("pretupid-emoji-inserted");
		clickGui(context, 4, 20);
		context.runOnClient(minecraft -> check(!(boolean) pickerField("open"), "Clicking outside did not close the picker"));
		clickGui(context, button[0] + 6, button[1] + 6);
		context.runOnClient(minecraft -> check((boolean) pickerField("open"), "The emoji button did not reopen the picker"));
		clickGui(context, button[0] + 6, button[1] + 6);
		context.runOnClient(minecraft -> check(!(boolean) pickerField("open"), "The emoji button did not close the picker"));
		context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_RETURN);
		context.waitTicks(10);
		String shortcode = inserted.substring(inserted.indexOf(':') + 1, inserted.lastIndexOf(':'));
		String glyph = new String(Character.toChars(EmojiIndex.get().byName(shortcode).orElseThrow().codepoint()));
		context.runOnClient(minecraft -> {
			check(Platform.screen() == null, "Enter did not send the message");
			check(chatMessages(minecraft).getFirst().getString().contains(glyph), "The sent message has no emoji: \"" + chatMessages(minecraft).getFirst().getString() + "\"");
		});
		context.takeScreenshot("pretupid-emoji-sent");
		context.setScreen(() -> Platform.chatScreen(":smi"));
		context.waitTicks(5);
		int[] row = context.computeOnClient(minecraft -> {
			var input = (net.minecraft.client.gui.components.EditBox) pickerField("input");
			int count = ((List<?>) pickerField("suggestions")).size();
			check(count > 0, "No emoji suggestions");
			return new int[] {Math.max(2, input.getScreenX(0) - 2) + 20, input.getY() - 4 - count * 12 + 6};
		});
		clickGui(context, row[0], row[1]);
		context.runOnClient(minecraft -> {
			String value = ((net.minecraft.client.gui.components.EditBox) pickerField("input")).getValue();
			check(value.matches(":[^: ]+: "), "Clicking a suggestion did not complete it: \"" + value + "\"");
		});
		context.getInput().setCursorPos(4, 4);
		context.setScreen(() -> null);
		context.waitTicks(3);
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

	private static void testModuleMarqueeHover(ClientGameTestContext context) {
		context.setScreen(() -> {
			ClientMenuScreen menu = new ClientMenuScreen(null);
			menu.select(ClientMenuScreen.Tab.MODULES);
			return menu;
		});
		context.getInput().setCursorPos(4, 4);
		context.waitTicks(40);
		java.nio.file.Path idleA = context.takeScreenshot("pretupid-marquee-idle-a");
		context.waitTicks(70);
		java.nio.file.Path idleB = context.takeScreenshot("pretupid-marquee-idle-b");
		int idleChange = changedPixels(idleA, idleB, context.computeOnClient(minecraft -> minecraft.getWindow().getWidth()) * 0.44, context.computeOnClient(minecraft -> minecraft.getWindow().getHeight()) * 0.32, context.computeOnClient(minecraft -> minecraft.getWindow().getWidth()) * 0.77, context.computeOnClient(minecraft -> minecraft.getWindow().getHeight()) * 0.37);
		check(idleChange < 60, "Module description moved without hover: " + idleChange);
		int windowWidth = context.computeOnClient(minecraft -> minecraft.getWindow().getWidth());
		int windowHeight = context.computeOnClient(minecraft -> minecraft.getWindow().getHeight());
		context.getInput().setCursorPos(windowWidth * 0.62, windowHeight * 0.32);
		context.waitTicks(16);
		java.nio.file.Path hoverA = context.takeScreenshot("pretupid-marquee-hover-a");
		context.waitTicks(10);
		java.nio.file.Path hoverB = context.takeScreenshot("pretupid-marquee-hover-b");
		int hoverChange = changedPixels(hoverA, hoverB, windowWidth * 0.44, windowHeight * 0.32, windowWidth * 0.77, windowHeight * 0.37);
		check(hoverChange >= 60, "Module description did not scroll on hover: " + hoverChange);
		context.getInput().setCursorPos(4, 4);
		context.waitTicks(30);
		java.nio.file.Path leftA = context.takeScreenshot("pretupid-marquee-left-a");
		context.waitTicks(40);
		java.nio.file.Path leftB = context.takeScreenshot("pretupid-marquee-left-b");
		int leftChange = changedPixels(leftA, leftB, windowWidth * 0.44, windowHeight * 0.32, windowWidth * 0.77, windowHeight * 0.37);
		check(leftChange < 60, "Module description kept moving after hover: " + leftChange);
		context.setScreen(() -> null);
	}

	private static int changedPixels(java.nio.file.Path first, java.nio.file.Path second, double x1, double y1, double x2, double y2) {
		try {
			java.awt.image.BufferedImage a = javax.imageio.ImageIO.read(first.toFile());
			java.awt.image.BufferedImage b = javax.imageio.ImageIO.read(second.toFile());
			int left = Math.max(0, (int) x1);
			int top = Math.max(0, (int) y1);
			int right = Math.min(a.getWidth(), (int) x2);
			int bottom = Math.min(a.getHeight(), (int) y2);
			int changed = 0;
			for (int y = top; y < bottom; y++) {
				for (int x = left; x < right; x++) {
					int p = a.getRGB(x, y);
					int q = b.getRGB(x, y);
					int difference = Math.abs((p >> 16 & 255) - (q >> 16 & 255)) + Math.abs((p >> 8 & 255) - (q >> 8 & 255)) + Math.abs((p & 255) - (q & 255));
					if (difference > 120) {
						changed++;
					}
				}
			}
			return changed;
		} catch (java.io.IOException exception) {
			throw new AssertionError("Could not read screenshots", exception);
		}
	}

	private static void testWaypointDeleteConfirm(ClientGameTestContext context) {
		context.runOnClient(minecraft -> {
			WaypointsModule waypoints = PretupidClient.get().modules().get(WaypointsModule.class);
			waypoints.setEnabled(true);
			for (String name : List.copyOf(waypoints.names(minecraft))) {
				waypoints.remove(minecraft, name);
			}
			waypoints.add(minecraft, "Delete me");
		});
		context.setScreen(() -> new WaypointsScreen(null));
		context.waitTicks(10);
		double[] button = context.computeOnClient(minecraft -> {
			int width = minecraft.getWindow().getGuiScaledWidth();
			int height = minecraft.getWindow().getGuiScaledHeight();
			int w = Math.min(470, width - 12);
			int h = Math.min(300, height - 12);
			int x = (width - w) / 2;
			int y = (height - h) / 2;
			int listY = y + 34 + 24 + 22;
			int rowX = x + 10;
			int rowW = w - 26;
			double scale = minecraft.getWindow().getGuiScale();
			return new double[] {(rowX + rowW - 4 - 24) * scale, (listY + 12) * scale};
		});
		context.getInput().setCursorPos(button[0], button[1]);
		context.waitTicks(3);
		context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(3);
		context.runOnClient(minecraft -> check(PretupidClient.get().modules().get(WaypointsModule.class).names(minecraft).contains("Delete me"), "First click deleted the waypoint"));
		context.takeScreenshot("pretupid-waypoint-confirm");
		context.waitTicks(70);
		context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(3);
		context.runOnClient(minecraft -> check(PretupidClient.get().modules().get(WaypointsModule.class).names(minecraft).contains("Delete me"), "Expired confirmation deleted the waypoint"));
		context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(3);
		context.runOnClient(minecraft -> check(!PretupidClient.get().modules().get(WaypointsModule.class).names(minecraft).contains("Delete me"), "Confirmed click did not delete the waypoint"));
		context.takeScreenshot("pretupid-waypoint-deleted");
		context.getInput().setCursorPos(4, 4);
		context.setScreen(() -> null);
	}

	private static long speedrunMillis(net.minecraft.client.Minecraft minecraft) {
		try {
			var module = PretupidClient.get().modules().get(net.pretupid.client.module.hud.SpeedrunTimerModule.class);
			var method = net.pretupid.client.module.hud.SpeedrunTimerModule.class.getDeclaredMethod("displayMillis", net.minecraft.client.Minecraft.class, boolean.class);
			method.setAccessible(true);
			return (long) method.invoke(module, minecraft, false);
		} catch (ReflectiveOperationException exception) {
			throw new AssertionError(exception);
		}
	}

	private static com.google.gson.JsonObject speedrunWorld(net.minecraft.client.Minecraft minecraft) {
		java.nio.file.Path file = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("pretupid").resolve("speedrun.json");
		try {
			com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(file)).getAsJsonObject();
			return root.getAsJsonObject("worlds").getAsJsonObject(WaypointsModule.worldKey(minecraft));
		} catch (java.io.IOException exception) {
			throw new AssertionError(exception);
		}
	}

	private static void testSpeedrunFlow(ClientGameTestContext context, TestSingleplayerContext world, BlockPos feet) {
		world.getServer().runCommand("gamemode creative @a");
		world.getServer().runCommand("tp @a " + feet.getX() + " " + feet.getY() + " " + feet.getZ() + " 0 10");
		context.waitTicks(10);
		context.runOnClient(minecraft -> {
			var module = PretupidClient.get().modules().get(net.pretupid.client.module.hud.SpeedrunTimerModule.class);
			module.setEnabled(true);
		});
		context.waitTicks(2);
		context.runOnClient(minecraft -> PretupidClient.get().modules().get(net.pretupid.client.module.hud.SpeedrunTimerModule.class).commandReset(minecraft));
		context.waitTicks(10);
		context.runOnClient(minecraft -> check(speedrunMillis(minecraft) == 0L, "Timer ran before the first input"));
		context.getInput().holdKeyFor(options -> options.keyUp, 4);
		context.waitTicks(12);
		context.runOnClient(minecraft -> check(speedrunMillis(minecraft) > 0L, "Timer did not auto start on the first input"));
		context.setScreen(() -> new PauseScreen(true));
		context.waitTicks(8);
		long pausedA = context.computeOnClient(PretupidClientGameTest::speedrunMillis);
		context.waitTicks(20);
		long pausedB = context.computeOnClient(PretupidClientGameTest::speedrunMillis);
		check(pausedA == pausedB, "Timer kept running while paused: " + pausedA + " to " + pausedB);
		context.setScreen(() -> null);
		context.waitTicks(8);
		long resumed = context.computeOnClient(PretupidClientGameTest::speedrunMillis);
		check(resumed > pausedB, "Timer did not resume after pause");
		world.getServer().runCommand("execute in minecraft:the_nether run tp @a " + feet.getX() + " 70 " + feet.getZ());
		context.waitTicks(40);
		context.runOnClient(minecraft -> check(speedrunWorld(minecraft).get("nether").getAsLong() > 0L, "Nether split was not recorded"));
		world.getServer().runCommand("execute in minecraft:the_end run tp @a 0 70 0");
		context.waitTicks(40);
		context.runOnClient(minecraft -> check(speedrunWorld(minecraft).get("end").getAsLong() > 0L, "End split was not recorded"));
		context.setScreen(() -> new net.minecraft.client.gui.screens.WinScreen(true, () -> { }));
		context.waitTicks(10);
		context.takeScreenshot("pretupid-speedrun-credits");
		context.runOnClient(minecraft -> {
			com.google.gson.JsonObject run = speedrunWorld(minecraft);
			check(run.get("finished").getAsBoolean(), "Credits did not finish the run");
			check(!run.get("running").getAsBoolean(), "Finished run is still running");
		});
		long finishedA = context.computeOnClient(PretupidClientGameTest::speedrunMillis);
		context.waitTicks(20);
		long finishedB = context.computeOnClient(PretupidClientGameTest::speedrunMillis);
		check(finishedA == finishedB, "Finished timer kept counting");
		context.setScreen(() -> null);
		world.getServer().runCommand("execute in minecraft:overworld run tp @a " + feet.getX() + " " + feet.getY() + " " + feet.getZ() + " 180 10");
		context.waitTicks(20);
		context.takeScreenshot("pretupid-speedrun-finished");
		context.runOnClient(minecraft -> {
			var module = PretupidClient.get().modules().get(net.pretupid.client.module.hud.SpeedrunTimerModule.class);
			module.commandReset(minecraft);
			module.setEnabled(false);
		});
		world.getServer().runCommand("gamemode survival @a");
		context.waitTicks(5);
	}

	private static net.minecraft.client.resources.sounds.SoundInstance playingSound() {
		try {
			Field field = net.pretupid.client.music.MusicController.class.getDeclaredField("playing");
			field.setAccessible(true);
			return (net.minecraft.client.resources.sounds.SoundInstance) field.get(PretupidClient.get().music());
		} catch (ReflectiveOperationException exception) {
			throw new AssertionError(exception);
		}
	}

	private static void setVolumes(net.minecraft.client.Minecraft minecraft, double master, double music) {
		minecraft.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER).set(master);
		minecraft.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MUSIC).set(music);
	}

	private static void setNowPlayingFlag(String id, boolean value) {
		PretupidClient.get().modules().get(net.pretupid.client.module.hud.NowPlayingModule.class).settings().stream()
			.filter(setting -> setting.id().equals(id))
			.map(net.pretupid.client.setting.BooleanSetting.class::cast)
			.forEach(setting -> setting.set(value));
	}

	private static void checkPlaying(net.minecraft.client.Minecraft minecraft, net.pretupid.client.music.MusicTrack track, String step) {
		var sound = playingSound();
		check(sound != null, step + ": no controlled sound");
		check(minecraft.getSoundManager().isActive(sound), step + ": sound is not active");
		check(track.equals(PretupidClient.get().music().current(minecraft)), step + ": wrong current track");
	}

	private static void testMusicFlow(ClientGameTestContext context) {
		double[] volumes = context.computeOnClient(minecraft -> new double[] {
			minecraft.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER).get(),
			minecraft.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MUSIC).get()
		});
		var tracks = context.computeOnClient(minecraft -> {
			PretupidClient.get().modules().get(net.pretupid.client.module.hud.NowPlayingModule.class).setEnabled(true);
			setVolumes(minecraft, 1.0, 0.5);
			setNowPlayingFlag("always_show", true);
			setNowPlayingFlag("show_when_muted", false);
			var all = PretupidClient.get().music().catalog().tracks(minecraft);
			check(all.size() >= 3, "Music catalog is too small");
			return List.of(all.get(0), all.get(1), all.get(2));
		});
		context.runOnClient(minecraft -> PretupidClient.get().music().play(minecraft, tracks.get(0)));
		context.waitTicks(20);
		context.runOnClient(minecraft -> {
			checkPlaying(minecraft, tracks.get(0), "Play");
			check(!(minecraft.getMusicManager() == null), "No music manager");
			PretupidClient.get().music().enqueue(minecraft, tracks.get(1));
			check(PretupidClient.get().music().queue().size() == 1, "Queue did not take the track");
			PretupidClient.get().music().skip(minecraft);
			check(PretupidClient.get().music().queue().isEmpty(), "Skip did not take the next queued track");
		});
		context.waitTicks(20);
		context.runOnClient(minecraft -> {
			checkPlaying(minecraft, tracks.get(1), "Skip to queued");
			PretupidClient.get().music().enqueue(minecraft, tracks.get(2));
			minecraft.getSoundManager().stop(playingSound());
		});
		context.waitTicks(25);
		context.runOnClient(minecraft -> {
			checkPlaying(minecraft, tracks.get(2), "Queue after song end");
			check(PretupidClient.get().music().queue().isEmpty(), "Finished queue entry stayed queued");
			PretupidClient.get().music().loop(minecraft, tracks.get(0));
		});
		context.waitTicks(20);
		var firstLoop = context.computeOnClient(minecraft -> {
			checkPlaying(minecraft, tracks.get(0), "Loop");
			var sound = playingSound();
			minecraft.getSoundManager().stop(sound);
			return sound;
		});
		context.waitTicks(25);
		context.runOnClient(minecraft -> {
			checkPlaying(minecraft, tracks.get(0), "Loop restart");
			check(playingSound() != firstLoop, "Loop did not start a new sound");
			check(tracks.get(0).equals(PretupidClient.get().music().looping()), "Loop state was lost");
		});
		context.takeScreenshot("pretupid-music-loop");
		context.setScreen(() -> new net.pretupid.client.ui.screen.MusicScreen(null));
		context.waitTicks(5);
		context.takeScreenshot("pretupid-music-loop-screen");
		context.setScreen(() -> null);
		context.runOnClient(minecraft -> {
			var module = PretupidClient.get().modules().get(net.pretupid.client.module.hud.NowPlayingModule.class);
			check(module.visible(minecraft), "Now Playing hidden at audible volume");
			setVolumes(minecraft, 1.0, 0.0);
			check(!module.visible(minecraft), "Now Playing shown with music at 0%");
			setVolumes(minecraft, 0.0, 0.5);
			check(!module.visible(minecraft), "Now Playing shown with master at 0%");
			setNowPlayingFlag("show_when_muted", true);
			check(module.visible(minecraft), "show_when_muted did not show Now Playing");
			setNowPlayingFlag("show_when_muted", false);
			setVolumes(minecraft, 1.0, 0.5);
		});
		var previous = context.computeOnClient(minecraft -> {
			var sound = playingSound();
			PretupidClient.get().music().useDynamic(minecraft);
			check(!PretupidClient.get().music().overridesVanilla(), "Dynamic left the player in control");
			check(playingSound() == null, "Dynamic left a controlled sound");
			check(PretupidClient.get().music().looping() == null, "Dynamic kept the loop");
			return sound;
		});
		context.waitTicks(5);
		context.runOnClient(minecraft -> check(!minecraft.getSoundManager().isActive(previous), "Dynamic did not stop the track"));
		boolean vanillaStarted = false;
		for (int i = 0; i < 300 && !vanillaStarted; i++) {
			context.waitTick();
			vanillaStarted = context.computeOnClient(minecraft -> PretupidClient.get().music().current(minecraft) != null);
		}
		check(vanillaStarted, "Vanilla music did not resume in dynamic mode");
		context.runOnClient(minecraft -> {
			check(playingSound() == null, "Dynamic track is not vanilla");
			PretupidClient.get().music().skip(minecraft);
		});
		context.waitTicks(10);
		context.runOnClient(minecraft -> {
			check(PretupidClient.get().music().current(minecraft) == null, "Skip did not stop vanilla music");
			setNowPlayingFlag("always_show", false);
			setVolumes(minecraft, volumes[0], volumes[1]);
			PretupidClient.get().modules().get(net.pretupid.client.module.hud.NowPlayingModule.class).setEnabled(false);
		});
		context.waitTicks(5);
	}

	private static Object editorField(String name) {
		try {
			Field field = ScreenieScreen.class.getDeclaredField(name);
			field.setAccessible(true);
			return field.get(Platform.screen());
		} catch (ReflectiveOperationException exception) {
			throw new AssertionError(exception);
		}
	}

	private static java.awt.image.BufferedImage screenieSource() {
		return Platform.screen() instanceof ScreenieScreen ? (java.awt.image.BufferedImage) editorField("source") : null;
	}

	private static void invokeEditor(String method, Class<?> type, Object value) {
		try {
			var target = ScreenieScreen.class.getDeclaredMethod(method, type);
			target.setAccessible(true);
			target.invoke(Platform.screen(), value);
		} catch (ReflectiveOperationException exception) {
			throw new AssertionError(exception);
		}
	}

	private static int imageDifference(java.awt.image.BufferedImage a, java.awt.image.BufferedImage b) {
		int width = Math.min(a.getWidth(), b.getWidth());
		int height = Math.min(a.getHeight(), b.getHeight());
		int changed = 0;
		for (int y = 0; y < height; y += 2) {
			for (int x = 0; x < width; x += 2) {
				int p = a.getRGB(x, y);
				int q = b.getRGB(x, y);
				if (Math.abs((p >> 16 & 255) - (q >> 16 & 255)) + Math.abs((p >> 8 & 255) - (q >> 8 & 255)) + Math.abs((p & 255) - (q & 255)) > 60) {
					changed++;
				}
			}
		}
		return changed;
	}

	private static java.awt.image.BufferedImage retakeEditor(ClientGameTestContext context) {
		Object before = context.computeOnClient(minecraft -> screenieSource());
		context.runOnClient(minecraft -> invokeEditor("retake", net.pretupid.client.screenie.MemeShot.class, net.pretupid.client.screenie.MemeShot.NORMAL));
		for (int i = 0; i < 200 && context.computeOnClient(minecraft -> screenieSource() == before); i++) {
			context.waitTick();
		}
		context.waitTicks(3);
		java.awt.image.BufferedImage image = context.computeOnClient(minecraft -> screenieSource());
		check(image != null && image != before, "The editor did not retake the picture");
		return image;
	}

	private static void testEditorControls(ClientGameTestContext context, TestSingleplayerContext world, BlockPos feet) {
		world.getServer().runCommand("gamemode creative @a");
		world.getServer().runCommand("tp @a " + feet.getX() + " " + feet.getY() + " " + feet.getZ() + " 0 15");
		world.getServer().runCommand("summon minecraft:pig " + feet.getX() + " " + feet.getY() + " " + (feet.getZ() + 4) + " {NoAI:1b,Silent:1b,CustomName:\"Editor Pig\",CustomNameVisible:1b,Tags:[\"editor\"]}");
		context.waitTicks(30);
		calm(context);
		context.runOnClient(minecraft -> PretupidClient.get().screenies().requestMeme());
		for (int i = 0; i < 200 && !context.computeOnClient(minecraft -> Platform.screen() instanceof ScreenieScreen); i++) {
			context.waitTick();
		}
		context.waitTicks(10);
		context.takeScreenshot("pretupid-editor-placeholders");
		var module = PretupidClient.get().modules().get(net.pretupid.client.module.utility.ScreeniesModule.class);
		context.runOnClient(minecraft -> {
			@SuppressWarnings("unchecked")
			var layers = (List<net.pretupid.client.screenie.TextLayer>) editorField("layers");
			check(layers.size() == 2 && layers.stream().allMatch(layer -> layer.text().isBlank()), "The meme starts with typed text");
			java.awt.image.BufferedImage source = screenieSource();
			java.awt.image.BufferedImage plain = net.pretupid.client.screenie.ScreenieComposer.compose(source, net.pretupid.client.screenie.ScreenieLook.NATURAL, List.of(), net.pretupid.client.screenie.MemeTemplate.CLASSIC, 640);
			java.awt.image.BufferedImage exported = net.pretupid.client.screenie.ScreenieComposer.compose(source, net.pretupid.client.screenie.ScreenieLook.NATURAL, List.copyOf(layers), net.pretupid.client.screenie.MemeTemplate.CLASSIC, 640);
			check(imageDifference(plain, exported) == 0, "Placeholder text reached the export");
		});
		java.awt.image.BufferedImage shown = retakeEditor(context);
		context.runOnClient(minecraft -> module.showEntities().set(false));
		java.awt.image.BufferedImage hidden = retakeEditor(context);
		int entityChange = imageDifference(shown, hidden);
		check(entityChange > 40, "Hiding entities did not change the picture: " + entityChange);
		context.takeScreenshot("pretupid-editor-hidden-entities");
		context.runOnClient(minecraft -> {
			check(!PretupidClient.get().screenies().hidesEntity(minecraft.level.getEntitiesOfClass(net.minecraft.world.entity.animal.Animal.class, minecraft.player.getBoundingBox().inflate(12)).get(0)), "Entity filter stayed on after capture");
			module.showEntities().set(true);
			module.showNametags().set(false);
		});
		java.awt.image.BufferedImage noName = retakeEditor(context);
		int nameChange = imageDifference(shown, noName);
		check(nameChange > 8, "Hiding nametags did not change the picture: " + nameChange);
		context.runOnClient(minecraft -> {
			check(!PretupidClient.get().screenies().hidesNames(), "Name filter stayed on after capture");
			module.showNametags().set(true);
		});
		java.awt.image.BufferedImage restored = retakeEditor(context);
		int restoredChange = imageDifference(shown, restored);
		check(restoredChange < entityChange, "Hidden things did not come back: " + restoredChange);
		context.runOnClient(minecraft -> {
			var zoom = (net.pretupid.client.setting.NumberSetting) editorField("zoom");
			java.awt.image.BufferedImage source = screenieSource();
			zoom.set(2.0);
			try {
				var working = ScreenieScreen.class.getDeclaredMethod("working");
				working.setAccessible(true);
				java.awt.image.BufferedImage center = (java.awt.image.BufferedImage) working.invoke(Platform.screen());
				check(Math.abs(center.getWidth() - source.getWidth() / 2) <= 1, "Zoom did not crop the picture");
				Field focus = ScreenieScreen.class.getDeclaredField("focusX");
				focus.setAccessible(true);
				focus.setDouble(Platform.screen(), 0.0);
				Field cropped = ScreenieScreen.class.getDeclaredField("cropped");
				cropped.setAccessible(true);
				cropped.set(Platform.screen(), null);
				java.awt.image.BufferedImage left = (java.awt.image.BufferedImage) working.invoke(Platform.screen());
				check(left.getWidth() == center.getWidth() && left.getRGB(0, left.getHeight() / 2) == source.getRGB(0, (source.getHeight() - left.getHeight()) / 2 + left.getHeight() / 2), "Reposition did not move the crop");
			} catch (ReflectiveOperationException exception) {
				throw new AssertionError(exception);
			}
			invokeEditor("select", int.class, 0);
			((net.pretupid.client.setting.NumberSetting) editorField("textAngle")).set(-18.0);
			@SuppressWarnings("unchecked")
			var layers = (List<net.pretupid.client.screenie.TextLayer>) editorField("layers");
			check(Math.abs(layers.get(0).angle() + 18f) < 0.01f, "Text angle was not applied");
		});
		context.waitTicks(15);
		context.takeScreenshot("pretupid-editor-zoom-angle");
		context.setScreen(() -> null);
		world.getServer().runCommand("kill @e[tag=editor]");
		world.getServer().runCommand("gamemode survival @a");
		context.waitTicks(5);
	}

	private static long presenceField(String name) {
		try {
			Field field = net.pretupid.client.network.PresenceService.class.getDeclaredField(name);
			field.setAccessible(true);
			return field.getLong(PretupidClient.get().presence());
		} catch (ReflectiveOperationException exception) {
			throw new AssertionError(exception);
		}
	}

	private static void setNextLookup(long value) {
		try {
			Field field = net.pretupid.client.network.PresenceService.class.getDeclaredField("nextLookup");
			field.setAccessible(true);
			field.setLong(PretupidClient.get().presence(), value);
		} catch (ReflectiveOperationException exception) {
			throw new AssertionError(exception);
		}
	}

	private static void testPresenceSwitch(ClientGameTestContext context) {
		boolean share = context.computeOnClient(minecraft -> PretupidClient.get().settings().sharePresence.enabled());
		boolean badges = context.computeOnClient(minecraft -> PretupidClient.get().settings().showOtherBadges.enabled());
		context.runOnClient(minecraft -> {
			PretupidClient.get().settings().showOtherBadges.set(true);
			PretupidClient.get().settings().sharePresence.set(false);
			setNextLookup(1L);
		});
		context.waitTicks(40);
		context.runOnClient(minecraft -> check(presenceField("nextLookup") == 1L, "A player lookup ran with online services off"));
		context.runOnClient(minecraft -> PretupidClient.get().settings().sharePresence.set(true));
		context.waitTicks(10);
		context.runOnClient(minecraft -> {
			check(presenceField("nextLookup") > 1L, "Player lookups did not resume");
			PretupidClient.get().settings().sharePresence.set(share);
			PretupidClient.get().settings().showOtherBadges.set(badges);
		});
		context.waitTicks(3);
	}

	@SuppressWarnings("unchecked")
	private static void setMinimap(String id, Object value) {
		var minimap = PretupidClient.get().modules().get(net.pretupid.client.module.utility.MinimapModule.class);
		for (var setting : minimap.settings()) {
			if (setting.id().equals(id)) {
				((net.pretupid.client.setting.Setting<Object>) setting).set(value);
				return;
			}
		}
		throw new AssertionError("No minimap setting " + id);
	}

	private static void minimapDirections(ClientGameTestContext context, TestSingleplayerContext world, BlockPos feet, String block, String label) {
		int y = feet.getY() - 1;
		world.getServer().runCommand("tp @a " + feet.getX() + " " + feet.getY() + " " + feet.getZ());
		context.waitTicks(30);
		world.getServer().runCommand("fill " + (feet.getX() - 70) + " " + y + " " + (feet.getZ() - 70) + " " + (feet.getX() + 70) + " " + y + " " + (feet.getZ() + 70) + " minecraft:" + block);
		context.waitTicks(180);
		String[] names = {"s", "sw", "w", "nw", "n", "ne", "e", "se"};
		for (int i = 0; i < names.length; i++) {
			world.getServer().runCommand("tp @a " + feet.getX() + " " + feet.getY() + " " + feet.getZ() + " " + (i * 45) + " 20");
			context.waitTicks(4);
			context.takeScreenshot("pretupid-minimap-" + label + "-" + names[i]);
		}
	}

	private static void testMinimapSprites(ClientGameTestContext context, TestSingleplayerContext world, BlockPos feet) {
		world.getServer().runCommand("gamemode creative @a");
		world.getServer().runCommand("tp @a " + feet.getX() + " " + feet.getY() + " " + feet.getZ());
		context.waitTicks(5);
		context.runOnClient(minecraft -> {
			var modules = PretupidClient.get().modules();
			WaypointsModule waypoints = modules.get(WaypointsModule.class);
			waypoints.setEnabled(true);
			modules.get(net.pretupid.client.module.utility.MinimapModule.class).setEnabled(true);
			setMinimap("rotate", false);
			setMinimap("size", 112.0);
			setMinimap("shape", net.pretupid.client.module.utility.MinimapModule.MinimapShape.CIRCLE);
			Platform.chat().clearMessages(false);
		});
		for (String[] spot : new String[][] {{"Pin A", "18", "-14"}, {"Pin B", "-20", "12"}, {"Far", "160", "90"}}) {
			world.getServer().runCommand("tp @a " + (feet.getX() + Integer.parseInt(spot[1])) + " " + feet.getY() + " " + (feet.getZ() + Integer.parseInt(spot[2])));
			context.waitTicks(3);
			context.runOnClient(minecraft -> PretupidClient.get().modules().get(WaypointsModule.class).add(minecraft, spot[0]));
		}
		minimapDirections(context, world, feet, "white_concrete", "light-circle");
		context.runOnClient(minecraft -> setMinimap("shape", net.pretupid.client.module.utility.MinimapModule.MinimapShape.SQUARE));
		minimapDirections(context, world, feet, "black_concrete", "dark-square");
		context.runOnClient(minecraft -> {
			setMinimap("rotate", true);
			setMinimap("scale", 1.25);
		});
		context.waitTicks(5);
		context.takeScreenshot("pretupid-minimap-scale-125");
		context.runOnClient(minecraft -> setMinimap("scale", 2.0));
		context.waitTicks(5);
		context.takeScreenshot("pretupid-minimap-scale-200");
		int y = feet.getY() - 1;
		world.getServer().runCommand("fill " + (feet.getX() - 70) + " " + y + " " + (feet.getZ() - 70) + " " + (feet.getX() + 70) + " " + y + " " + (feet.getZ() + 70) + " minecraft:grass_block");
		context.runOnClient(minecraft -> {
			var modules = PretupidClient.get().modules();
			WaypointsModule waypoints = modules.get(WaypointsModule.class);
			for (String name : List.of("Pin A", "Pin B", "Far")) {
				waypoints.remove(minecraft, name);
			}
			setMinimap("scale", 1.0);
			setMinimap("size", 96.0);
			setMinimap("shape", net.pretupid.client.module.utility.MinimapModule.MinimapShape.CIRCLE);
			modules.get(net.pretupid.client.module.utility.MinimapModule.class).setEnabled(false);
		});
		world.getServer().runCommand("tp @a " + feet.getX() + " " + feet.getY() + " " + feet.getZ() + " 180 10");
		world.getServer().runCommand("gamemode survival @a");
		context.waitTicks(10);
	}

	private static void setFreecamFreeMove(boolean value) {
		PretupidClient.get().modules().get(FreecamModule.class).settings().stream()
			.filter(setting -> setting.id().equals("free_move"))
			.map(net.pretupid.client.setting.BooleanSetting.class::cast)
			.forEach(setting -> setting.set(value));
	}

	private static double freecamClimb(ClientGameTestContext context, boolean freeMove) {
		context.runOnClient(minecraft -> {
			FreecamModule freecam = PretupidClient.get().modules().get(FreecamModule.class);
			freecam.setEnabled(true);
			setFreecamFreeMove(freeMove);
			freecam.setHeld(minecraft, true);
		});
		context.waitTicks(4);
		double start = context.computeOnClient(minecraft -> PretupidClient.get().modules().get(FreecamModule.class).position(1f).y);
		context.getInput().holdKeyFor(options -> options.keyUp, 12);
		context.waitTicks(3);
		double end = context.computeOnClient(minecraft -> PretupidClient.get().modules().get(FreecamModule.class).position(1f).y);
		context.runOnClient(minecraft -> PretupidClient.get().modules().get(FreecamModule.class).setHeld(minecraft, false));
		context.waitTicks(2);
		return end - start;
	}

	private static void testFreeMoveAndDefaults(ClientGameTestContext context, TestSingleplayerContext world, BlockPos feet) {
		boolean wasEnabled = context.computeOnClient(minecraft -> PretupidClient.get().modules().get(FreecamModule.class).enabledSetting().get());
		world.getServer().runCommand("gamemode creative @a");
		world.getServer().runCommand("tp @a " + feet.getX() + " " + (feet.getY() + 2) + " " + feet.getZ() + " 0 -45");
		context.waitTicks(8);
		double level = freecamClimb(context, false);
		check(Math.abs(level) < 0.01, "Normal freecam moved vertically: " + level);
		double climb = freecamClimb(context, true);
		check(climb > 1.0, "Free move did not follow the camera pitch: " + climb);
		context.runOnClient(minecraft -> {
			setFreecamFreeMove(false);
			PretupidClient.get().modules().get(FreecamModule.class).setEnabled(wasEnabled);
			var cinematic = new net.pretupid.client.module.visual.CinematicModule();
			check(cinematic.enabledSetting().get(), "Cinematic is not on for new users");
			var screenies = new net.pretupid.client.module.utility.ScreeniesModule();
			check(screenies.enabledSetting().get(), "Screenies is not on for new users");
			com.google.gson.JsonObject saved = cinematic.save();
			saved.addProperty("enabled", false);
			cinematic.loadDefaults();
			cinematic.load(saved);
			check(!cinematic.enabledSetting().get(), "A saved off state for Cinematic was overwritten");
		});
		world.getServer().runCommand("tp @a " + feet.getX() + " " + feet.getY() + " " + feet.getZ() + " 180 10");
		world.getServer().runCommand("gamemode survival @a");
		context.waitTicks(5);
	}

	private static void testEditorMouse(ClientGameTestContext context) {
		calm(context);
		context.runOnClient(minecraft -> PretupidClient.get().screenies().request());
		for (int i = 0; i < 200 && !context.computeOnClient(minecraft -> Platform.screen() instanceof ScreenieScreen); i++) {
			context.waitTick();
		}
		context.waitTicks(10);
		double scale = context.computeOnClient(minecraft -> (double) minecraft.getWindow().getGuiScale());
		int guiWidth = context.computeOnClient(minecraft -> minecraft.getWindow().getGuiScaledWidth());
		int guiHeight = context.computeOnClient(minecraft -> minecraft.getWindow().getGuiScaledHeight());
		double centerX = (8 + (guiWidth - 176 - 24) / 2.0) * scale;
		double centerY = (24 + (guiHeight - 58) / 2.0) * scale;
		context.getInput().setCursorPos(centerX, centerY);
		context.waitTicks(3);
		for (int i = 0; i < 6; i++) {
			context.getInput().scroll(1);
			context.waitTick();
		}
		context.runOnClient(minecraft -> check(((net.pretupid.client.setting.NumberSetting) editorField("zoom")).get() > 1.4, "The mouse wheel did not zoom the picture"));
		double focusBefore = context.computeOnClient(minecraft -> (double) editorField("focusX"));
		context.getInput().holdMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(2);
		for (int i = 0; i < 6; i++) {
			context.getInput().moveCursor(14, 0);
			context.waitTick();
		}
		context.getInput().releaseMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(3);
		context.runOnClient(minecraft -> check((double) editorField("focusX") < focusBefore, "Dragging did not reposition the picture"));
		context.takeScreenshot("pretupid-editor-mouse-framed");
		int panelX = guiWidth - 176 - 8;
		clickGui(context, panelX + 4 + 2 * 56 + 27, 24 + 4 + 8);
		context.runOnClient(minecraft -> check("FRAME".equals(editorField("tab").toString()), "The Frame tab did not open"));
		context.waitTicks(3);
		context.takeScreenshot("pretupid-editor-frame-tab");
		Object before = context.computeOnClient(minecraft -> screenieSource());
		clickGui(context, panelX + 4 + (176 - 8 - 6) - 24 + 8, 24 + 26 + 48 + 5);
		context.runOnClient(minecraft -> check(!PretupidClient.get().modules().get(net.pretupid.client.module.utility.ScreeniesModule.class).showNametags().enabled(), "The nametag toggle did not switch"));
		for (int i = 0; i < 200 && context.computeOnClient(minecraft -> screenieSource() == before); i++) {
			context.waitTick();
		}
		context.runOnClient(minecraft -> {
			check(screenieSource() != before, "The hide toggle did not retake the picture");
			PretupidClient.get().modules().get(net.pretupid.client.module.utility.ScreeniesModule.class).showNametags().set(true);
		});
		clickGui(context, panelX + 4 + 56 + 27, 24 + 4 + 8);
		context.waitTicks(10);
		context.runOnClient(minecraft -> {
			check("TEXT".equals(editorField("tab").toString()), "The Text tab did not open");
			@SuppressWarnings("unchecked")
			var layers = (List<net.pretupid.client.screenie.TextLayer>) editorField("layers");
			check(layers.size() == 1 && layers.get(0).text().isBlank(), "The Text tab did not add a placeholder layer");
		});
		context.takeScreenshot("pretupid-editor-screenie-placeholder");
		context.getInput().setCursorPos(4, 4);
		context.setScreen(() -> null);
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
			Object before = context.computeOnClient(minecraft -> screenieSource());
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
			for (int i = 0; i < 200 && context.computeOnClient(minecraft -> screenieSource() == before); i++) {
				context.waitTick();
			}
			context.runOnClient(minecraft -> {
				check(Platform.screen() instanceof ScreenieScreen && screenieSource() != before, "The meme editor did not show the retaken picture");
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

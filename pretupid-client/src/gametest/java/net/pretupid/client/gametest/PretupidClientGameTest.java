package net.pretupid.client.gametest;

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
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.config.ClientSettings;
import net.pretupid.client.cosmetic.Catalog;
import net.pretupid.client.module.Module;
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
import net.pretupid.client.social.LocalHosting;
import net.pretupid.client.ui.screen.ClientMenuScreen;
import net.pretupid.client.ui.screen.HudEditorScreen;

public final class PretupidClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("weather clear");
			BlockPos feet = context.computeOnClient(minecraft -> minecraft.player.blockPosition());
			world.getServer().runCommand("setblock " + feet.getX() + " " + feet.getY() + " " + (feet.getZ() + 3) + " minecraft:diamond_block");
			context.waitTicks(10);

			testHudModules(context, feet);
			testAccentLabels(context);
			testChatModules(context);
			testWaypoints(context, world, feet);
			testTexturedWearable(context);
			testCosmetics(context);
			testEmotes(context);
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
		context.getInput().lookAt(feet.offset(0, 0, 3));
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

	@SuppressWarnings("unchecked")
	private static List<GuiMessage> chatMessages(Minecraft minecraft) {
		try {
			Field field = ChatComponent.class.getDeclaredField("allMessages");
			field.setAccessible(true);
			return (List<GuiMessage>) field.get(minecraft.gui.hud.getChat());
		} catch (ReflectiveOperationException exception) {
			throw new AssertionError("Chat messages are not readable", exception);
		}
	}

	private static void testChatModules(ClientGameTestContext context) {
		context.runOnClient(minecraft -> {
			enable(minecraft, CompactChatModule.class);
			ChatComponent chat = minecraft.gui.hud.getChat();
			for (int i = 0; i < 3; i++) {
				chat.addClientSystemMessage(Component.literal("Pretupid compact chat test"));
			}
			String latest = chatMessages(minecraft).getFirst().content().getString();
			check(latest.endsWith("(3×)"), "Compact Chat produced \"" + latest + "\"");
			long copies = chatMessages(minecraft).stream().filter(message -> message.content().getString().startsWith("Pretupid compact chat test")).count();
			check(copies == 1, "Compact Chat kept " + copies + " copies");

			NickHiderModule nickHider = PretupidClient.get().modules().get(NickHiderModule.class);
			nickHider.setEnabled(true);
			String name = minecraft.getUser().getName();
			chat.addClientSystemMessage(Component.literal("<" + name + "> hello from the game test"));
			String hidden = chatMessages(minecraft).getFirst().content().getString();
			check(!hidden.contains(name) && hidden.contains(nickHider.alias()), "Nick Hider produced \"" + hidden + "\"");
		});
		context.waitTicks(5);
		context.takeScreenshot("pretupid-chat-modules");
		context.runOnClient(minecraft -> PretupidClient.get().modules().get(NickHiderModule.class).setEnabled(false));
	}

	private static void testWaypoints(ClientGameTestContext context, TestSingleplayerContext world, BlockPos feet) {
		context.runOnClient(minecraft -> {
			WaypointsModule waypoints = PretupidClient.get().modules().get(WaypointsModule.class);
			waypoints.setEnabled(true);
			waypoints.add(minecraft, "Game test base");
			check(waypoints.names(minecraft).contains("Game test base"), "Waypoint was not saved");
		});
		world.getServer().runCommand("tp @a " + (feet.getX() + 40) + " " + feet.getY() + " " + (feet.getZ() + 25));
		context.waitTicks(20);
		context.takeScreenshot("pretupid-waypoints");
		world.getServer().runCommand("tp @a " + feet.getX() + " " + feet.getY() + " " + feet.getZ() + " 180 10");
		context.runOnClient(minecraft -> {
			WaypointsModule waypoints = PretupidClient.get().modules().get(WaypointsModule.class);
			waypoints.remove(minecraft, "Game test base");
			check(!waypoints.names(minecraft).contains("Game test base"), "Waypoint was not removed");
		});
		context.waitTicks(20);
	}

	private static void testCosmetics(ClientGameTestContext context) {
		context.runOnClient(minecraft -> {
			ClientSettings settings = PretupidClient.get().settings();
			enable(minecraft, OwnNametagModule.class);
			settings.nametagAnimation.set(NameAnimation.RAINBOW);
			settings.nametagEffect.set(NameEffect.WAVE);
			settings.hat.set("crown");
			settings.cape.set("catalog:pretupid");
			minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
		});
		context.getInput().lookAt(180f, 10f);
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

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static void showCapeSection(ClientMenuScreen menu) {
		try {
			Field pages = ClientMenuScreen.class.getDeclaredField("pages");
			pages.setAccessible(true);
			Object page = ((java.util.Map<?, ?>) pages.get(menu)).get(ClientMenuScreen.Tab.COSMETICS);
			Field section = page.getClass().getDeclaredField("section");
			section.setAccessible(true);
			section.set(page, Enum.valueOf((Class) section.getType(), "CAPE"));
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

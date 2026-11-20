package net.pretupid.client.platform;

import org.joml.Matrix4f;
import net.minecraft.world.level.Level;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.KeyMapping;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.services.ProfileResult;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;

public final class Platform {
	public static final String MINECRAFT = "26.3";
	public static final boolean MOTION_BLUR = true;
	public static final boolean SDL_INPUT = true;

	private Platform() {
	}

	public static Screen screen() {
		return Minecraft.getInstance().gui.screen();
	}

	public static void setScreen(Screen screen) {
		Minecraft.getInstance().gui.setScreen(screen);
	}

	public static ChatComponent chat() {
		return Minecraft.getInstance().gui.hud.getChat();
	}

	public static boolean hudHidden() {
		return Minecraft.getInstance().gui.hud.isHidden();
	}

	public static Camera camera() {
		return Minecraft.getInstance().gameRenderer.mainCamera();
	}

	public static InputConstants.Type keyboard() {
		return InputConstants.Type.KEYBOARD;
	}

	public static boolean isKeyDown(int key) {
		return InputConstants.isKeyDown(key);
	}

	public static void rotate(PoseStack poseStack, Axis axis, float radians) {
		poseStack.rotate(axis, radians);
	}

	public static void rotateDegrees(PoseStack poseStack, Axis axis, float degrees) {
		poseStack.rotateDegrees(axis, degrees);
	}

	public static boolean swinging(LivingEntity entity) {
		return entity.isSwinging();
	}

	public static boolean publishLan(IntegratedServer server, int port) {
		return server.publishServer(MinecraftServer.MultiplayerScope.LAN, false, port);
	}

	public static void startTextInput(Object owner) {
		Minecraft.getInstance().textInputManager().startTextInput(owner);
	}

	public static void stopTextInput(Object owner) {
		Minecraft.getInstance().textInputManager().stopTextInput(owner);
	}

	public static <S> void submitModel(SubmitNodeCollector collector, Model<? super S> model, S state, PoseStack poseStack, RenderType renderType, int light, int overlay, int outline) {
		collector.submitModel(model, state, poseStack, renderType, light, overlay, outline);
	}

	public static Identifier mobEffectSprite(Holder<MobEffect> effect) {
		return Hud.getMobEffectSprite(effect);
	}

	public static Collection<Property> profileProperties(Minecraft minecraft, UUID uuid, String name) {
		ProfileResult result = minecraft.services().sessionService().fetchProfile(uuid, true);
		return result == null ? List.of() : result.profile().properties().get(name);
	}

	public static KeyMapping registerKey(KeyMapping mapping) {
		return KeyMappingHelper.registerKeyMapping(mapping);
	}

	public static InputConstants.Key boundKey(KeyMapping mapping) {
		return KeyMappingHelper.getBoundKeyOf(mapping);
	}

	public static LiteralArgumentBuilder<FabricClientCommandSource> literal(String name) {
		return ClientCommands.literal(name);
	}

	public static <T> RequiredArgumentBuilder<FabricClientCommandSource, T> argument(String name, ArgumentType<T> type) {
		return ClientCommands.argument(name, type);
	}

	public static <T extends CustomPacketPayload> void registerClientboundPayload(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
		PayloadTypeRegistry.clientboundPlay().register(type, codec);
	}

	public static List<AbstractWidget> widgets(Screen screen) {
		return Screens.getWidgets(screen);
	}

	public static void showMessage(Component message) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player != null) {
			minecraft.player.sendSystemMessage(message);
		}
	}

	public static void addChatMessage(Component message) {
		chat().addClientSystemMessage(message);
	}

	public static long overworldTime(Level level) {
		return level.getOverworldClockTime();
	}

	public static long dayTime(Level level) {
		return level.getDefaultClockTime();
	}

	public static boolean windowFocused() {
		return Minecraft.getInstance().getWindow().isFocused();
	}

	public static Matrix4f viewProjection(Camera camera) {
		return camera.getViewRotationProjectionMatrix(new Matrix4f());
	}

	public static void registerAvatarLayers(java.util.function.Function<AvatarRenderer<?>, RenderLayer<AvatarRenderState, PlayerModel>> factory) {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatarRenderer) {
				helper.register(factory.apply(avatarRenderer));
			}
		});
	}

	public static float cameraYaw(Camera camera) {
		return camera.yRot();
	}

	public static float cameraPitch(Camera camera) {
		return camera.xRot();
	}

	public static final int MAX_SCREENIE_SIZE = 7680;

	public static void captureScreenie(int scale, java.util.function.Consumer<com.mojang.blaze3d.platform.NativeImage> done) {
		Minecraft minecraft = Minecraft.getInstance();
		com.mojang.blaze3d.pipeline.RenderTarget target = minecraft.gameRenderer.mainRenderTarget();
		com.mojang.blaze3d.platform.Window window = minecraft.getWindow();
		int width = window.getWidth();
		int height = window.getHeight();
		int factor = Math.max(1, Math.min(scale, Math.min(MAX_SCREENIE_SIZE / Math.max(1, width), MAX_SCREENIE_SIZE / Math.max(1, height))));
		if (minecraft.level == null) {
			net.minecraft.client.Screenshot.takeScreenshot(target, done);
			return;
		}
		minecraft.gameRenderer.setRenderBlockOutline(false);
		try {
			window.setWidth(width * factor);
			window.setHeight(height * factor);
			target.resize(width * factor, height * factor);
			minecraft.gameRenderer.update(DeltaTracker.ONE);
			minecraft.gameRenderer.extract(DeltaTracker.ONE, true);
			minecraft.gameRenderer.renderLevel();
			net.minecraft.client.Screenshot.takeScreenshot(target, done);
		} finally {
			minecraft.gameRenderer.setRenderBlockOutline(true);
			window.setWidth(width);
			window.setHeight(height);
			target.resize(width, height);
		}
	}

	public static Screen chatScreen(String text) {
		return new net.minecraft.client.gui.screens.ChatScreen(text, false);
	}

	public static net.minecraft.world.phys.Vec3 cameraPosition() {
		return camera().position();
	}
}

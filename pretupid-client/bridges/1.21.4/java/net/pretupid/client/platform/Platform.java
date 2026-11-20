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
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.pretupid.client.mixin.platform.GameRendererAccess;
import org.joml.Quaternionf;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.yggdrasil.ProfileResult;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameType;

public final class Platform {
	public static final String MINECRAFT = "1.21.4";
	public static final boolean MOTION_BLUR = false;
	public static final boolean SDL_INPUT = false;

	private Platform() {
	}

	public static Screen screen() {
		return Minecraft.getInstance().screen;
	}

	public static void setScreen(Screen screen) {
		Minecraft.getInstance().setScreen(screen);
	}

	public static ChatComponent chat() {
		return Minecraft.getInstance().gui.getChat();
	}

	public static boolean hudHidden() {
		return Minecraft.getInstance().options.hideGui;
	}

	public static Camera camera() {
		return Minecraft.getInstance().gameRenderer.getMainCamera();
	}

	public static InputConstants.Type keyboard() {
		return InputConstants.Type.KEYSYM;
	}

	public static boolean isKeyDown(int key) {
		return InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), key);
	}

	public static void rotate(PoseStack poseStack, Axis axis, float radians) {
		poseStack.mulPose(axis.rotation(radians));
	}

	public static void rotateDegrees(PoseStack poseStack, Axis axis, float degrees) {
		poseStack.mulPose(axis.rotationDegrees(degrees));
	}

	public static boolean swinging(LivingEntity entity) {
		return entity.swinging;
	}

	public static boolean publishLan(IntegratedServer server, int port) {
		return server.publishServer(gameType(), false, port);
	}

	private static GameType gameType() {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.gameMode == null ? GameType.SURVIVAL : minecraft.gameMode.getPlayerMode();
	}

	public static void startTextInput(Object owner) {

	}

	public static void stopTextInput(Object owner) {

	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static <S> void submitModel(MultiBufferSource buffers, Model model, S state, PoseStack poseStack, RenderType renderType, int light, int overlay, int outline) {
		if (model instanceof EntityModel<?> entityModel) {
			((EntityModel) entityModel).setupAnim((net.minecraft.client.renderer.entity.state.EntityRenderState) state);
		}
		model.renderToBuffer(poseStack, buffers.getBuffer(renderType), light, overlay);
	}

	public static Collection<Property> profileProperties(Minecraft minecraft, UUID uuid, String name) {
		ProfileResult result = minecraft.getMinecraftSessionService().fetchProfile(uuid, true);
		return result == null ? List.of() : result.profile().getProperties().get(name);
	}

	public static KeyMapping registerKey(KeyMapping mapping) {
		return KeyBindingHelper.registerKeyBinding(mapping);
	}

	public static InputConstants.Key boundKey(KeyMapping mapping) {
		return KeyBindingHelper.getBoundKeyOf(mapping);
	}

	public static LiteralArgumentBuilder<FabricClientCommandSource> literal(String name) {
		return ClientCommandManager.literal(name);
	}

	public static <T> RequiredArgumentBuilder<FabricClientCommandSource, T> argument(String name, ArgumentType<T> type) {
		return ClientCommandManager.argument(name, type);
	}

	public static <T extends CustomPacketPayload> void registerClientboundPayload(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
		PayloadTypeRegistry.playS2C().register(type, codec);
	}

	public static List<AbstractWidget> widgets(Screen screen) {
		return Screens.getButtons(screen);
	}

	public static void showMessage(Component message) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player != null) {
			minecraft.player.displayClientMessage(message, false);
		}
	}

	public static void addChatMessage(Component message) {
		chat().addMessage(message);
	}

	public static long overworldTime(Level level) {
		return level.getDayTime();
	}

	public static long dayTime(Level level) {
		return level.getDayTime();
	}

	public static boolean windowFocused() {
		return Minecraft.getInstance().isWindowActive();
	}

	public static Matrix4f viewProjection(Camera camera) {
		Minecraft minecraft = Minecraft.getInstance();
		float fov = ((GameRendererAccess) minecraft.gameRenderer).pretupid$fov(camera, minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(true), true);
		Matrix4f projection = new Matrix4f(minecraft.gameRenderer.getProjectionMatrix(fov));
		return projection.mul(new Matrix4f().rotation(new Quaternionf(camera.rotation()).conjugate()));
	}

	public static void registerAvatarLayers(java.util.function.Function<AvatarRenderer, RenderLayer<AvatarRenderState, PlayerModel>> factory) {
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer avatarRenderer) {
				helper.register(factory.apply(avatarRenderer));
			}
		});
	}

	public static float cameraYaw(Camera camera) {
		return camera.getYRot();
	}

	public static float cameraPitch(Camera camera) {
		return camera.getXRot();
	}

	public static final int MAX_SCREENIE_SIZE = 7680;

	public static void captureScreenie(int scale, java.util.function.Consumer<com.mojang.blaze3d.platform.NativeImage> done) {
		Minecraft minecraft = Minecraft.getInstance();
		com.mojang.blaze3d.pipeline.RenderTarget target = minecraft.getMainRenderTarget();
		com.mojang.blaze3d.platform.Window window = minecraft.getWindow();
		int width = window.getWidth();
		int height = window.getHeight();
		int factor = Math.max(1, Math.min(scale, Math.min(MAX_SCREENIE_SIZE / Math.max(1, width), MAX_SCREENIE_SIZE / Math.max(1, height))));
		if (minecraft.level == null) {
			done.accept(net.minecraft.client.Screenshot.takeScreenshot(target));
			return;
		}
		minecraft.gameRenderer.setRenderBlockOutline(false);
		try {
			window.setWidth(width * factor);
			window.setHeight(height * factor);
			target.resize(width * factor, height * factor);
			minecraft.gameRenderer.renderLevel(DeltaTracker.ONE);
			done.accept(net.minecraft.client.Screenshot.takeScreenshot(target));
		} finally {
			minecraft.gameRenderer.setRenderBlockOutline(true);
			window.setWidth(width);
			window.setHeight(height);
			target.resize(width, height);
		}
	}

	public static Screen chatScreen(String text) {
		return new net.minecraft.client.gui.screens.ChatScreen(text);
	}

	public static net.minecraft.world.phys.Vec3 cameraPosition() {
		return camera().getPosition();
	}
}

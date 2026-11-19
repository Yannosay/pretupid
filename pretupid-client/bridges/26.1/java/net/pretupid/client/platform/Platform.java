package net.pretupid.client.platform;

import com.mojang.authlib.properties.Property;
import com.mojang.authlib.yggdrasil.ProfileResult;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameType;

public final class Platform {
	public static final String MINECRAFT = "26.1";
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
		return InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), key);
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
		Minecraft.getInstance().textInputManager().startTextInput();
	}

	public static void stopTextInput(Object owner) {
		Minecraft.getInstance().textInputManager().stopTextInput();
	}

	public static <S> void submitModel(SubmitNodeCollector collector, Model<? super S> model, S state, PoseStack poseStack, RenderType renderType, int light, int overlay, int outline) {
		collector.submitModel(model, state, poseStack, renderType, light, overlay, outline, null);
	}

	public static Identifier mobEffectSprite(Holder<MobEffect> effect) {
		return Gui.getMobEffectSprite(effect);
	}

	public static Collection<Property> profileProperties(Minecraft minecraft, UUID uuid, String name) {
		ProfileResult result = minecraft.services().sessionService().fetchProfile(uuid, true);
		return result == null ? List.of() : result.profile().properties().get(name);
	}
}

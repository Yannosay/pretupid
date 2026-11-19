package net.pretupid.client.platform;

import com.mojang.authlib.properties.Property;
import com.mojang.authlib.services.ProfileResult;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Camera;
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
}

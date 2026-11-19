package net.pretupid.client.module.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.module.Category;
import net.pretupid.client.module.Module;
import net.pretupid.client.setting.BooleanSetting;
import net.pretupid.client.setting.NumberSetting;
import net.pretupid.client.ui.Icon;
import net.pretupid.client.ui.Theme;

public final class FreecamModule extends Module {
	private static final double CAMERA_RADIUS = 0.15;
	private static final double MULTIPLAYER_REACH = 64.0;

	private final BooleanSetting hold = add(new BooleanSetting("hold", false));
	private final NumberSetting speed = add(new NumberSetting("speed", 1.0, 0.1, 5.0, 0.1, "x"));
	private boolean active;
	private Vec3 position = Vec3.ZERO;
	private Vec3 previousPosition = Vec3.ZERO;
	private Vec3 velocity = Vec3.ZERO;
	private float yaw;
	private float pitch;
	private Input heldInput = Input.EMPTY;
	private boolean solidCamera;

	public FreecamModule() {
		super("freecam", Category.VISUAL, "ender_eye", false);
	}

	public boolean active() {
		return active;
	}

	public boolean holdMode() {
		return hold.enabled();
	}

	public float yaw() {
		return yaw;
	}

	public float pitch() {
		return pitch;
	}

	public Vec3 position(float partialTick) {
		return previousPosition.lerp(position, partialTick);
	}

	public void setHeld(Minecraft minecraft, boolean down) {
		if (down && !active) {
			start(minecraft);
		} else if (!down && active) {
			stop();
		}
	}

	public void toggleActive(Minecraft minecraft) {
		setHeld(minecraft, !active);
	}

	public void turn(double dx, double dy) {
		yaw += (float) dx * 0.15F;
		pitch = Mth.clamp(pitch + (float) dy * 0.15F, -90F, 90F);
	}

	public void captureInput(Input input) {
		heldInput = input;
	}

	public void explainUnavailable(Minecraft minecraft) {
		if (minecraft.player == null) {
			return;
		}
		Component reason = isPaused() ? pauseReason() : Component.translatable("pretupid.module.freecam.unavailable");
		PretupidClient.get().notifications().push(Component.translatable("pretupid.module.freecam.name"), reason, Icon.item("ender_eye"), null,
			isPaused() ? Theme.WARNING : Theme.accent(), 3500L);
	}

	private void start(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (!isEnabled() || player == null || minecraft.level == null) {
			return;
		}
		FreelookModule freelook = PretupidClient.get().modules().get(FreelookModule.class);
		if (freelook.active()) {
			freelook.setHeld(minecraft, false);
		}
		solidCamera = !minecraft.hasSingleplayerServer();
		position = player.getEyePosition();
		previousPosition = position;
		velocity = Vec3.ZERO;
		yaw = player.getYRot();
		pitch = player.getXRot();
		heldInput = Input.EMPTY;
		active = true;
		if (solidCamera) {
			PretupidClient.get().notifications().push(Component.translatable("pretupid.module.freecam.name"),
				Component.translatable("pretupid.module.freecam.multiplayer"), Icon.item("ender_eye"), null, Theme.accent(), 3500L);
		}
	}

	private void stop() {
		active = false;
		heldInput = Input.EMPTY;
		velocity = Vec3.ZERO;
	}

	@Override
	public void onTick(Minecraft minecraft) {
		if (!active) {
			return;
		}
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null || !player.isAlive() || player.level() != minecraft.level) {
			stop();
			return;
		}
		previousPosition = position;
		Input input = minecraft.gui.screen() == null ? heldInput : Input.EMPTY;
		float forward = (input.forward() ? 1f : 0f) - (input.backward() ? 1f : 0f);
		float strafe = (input.left() ? 1f : 0f) - (input.right() ? 1f : 0f);
		float vertical = (input.jump() ? 1f : 0f) - (input.shift() ? 1f : 0f);
		double radians = Math.toRadians(yaw);
		double sin = Math.sin(radians);
		double cos = Math.cos(radians);
		Vec3 wish = new Vec3(strafe * cos - forward * sin, vertical, forward * cos + strafe * sin);
		if (wish.lengthSqr() > 1.0) {
			wish = wish.normalize();
		}
		double top = 0.55 * speed.get() * (input.sprint() ? 2.5 : 1.0);
		velocity = velocity.scale(0.6).add(wish.scale(top * 0.4));
		if (velocity.lengthSqr() < 1.0E-5) {
			velocity = Vec3.ZERO;
			return;
		}
		position = solidCamera ? slide(minecraft, position, velocity, player.getEyePosition()) : position.add(velocity);
	}

	private Vec3 slide(Minecraft minecraft, Vec3 from, Vec3 motion, Vec3 anchor) {
		Vec3 result = from;
		for (int axis = 0; axis < 3; axis++) {
			Vec3 step = switch (axis) {
				case 0 -> new Vec3(motion.x, 0, 0);
				case 1 -> new Vec3(0, motion.y, 0);
				default -> new Vec3(0, 0, motion.z);
			};
			if (step.lengthSqr() == 0) {
				continue;
			}
			Vec3 candidate = result.add(step);
			if (candidate.distanceToSqr(anchor) > MULTIPLAYER_REACH * MULTIPLAYER_REACH) {
				continue;
			}
			AABB box = new AABB(candidate.x - CAMERA_RADIUS, candidate.y - CAMERA_RADIUS, candidate.z - CAMERA_RADIUS,
				candidate.x + CAMERA_RADIUS, candidate.y + CAMERA_RADIUS, candidate.z + CAMERA_RADIUS);
			if (minecraft.level.noBlockCollision(null, box)) {
				result = candidate;
			} else {
				velocity = switch (axis) {
					case 0 -> new Vec3(0, velocity.y, velocity.z);
					case 1 -> new Vec3(velocity.x, 0, velocity.z);
					default -> new Vec3(velocity.x, velocity.y, 0);
				};
			}
		}
		return result;
	}

	@Override
	protected void onDisable() {
		if (active) {
			stop();
		}
	}
}

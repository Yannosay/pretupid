package net.pretupid.client.module.performance;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.TheEndPortalBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.pretupid.client.module.Category;
import net.pretupid.client.module.Module;
import net.pretupid.client.setting.BooleanSetting;
import net.pretupid.client.setting.NumberSetting;

public final class EntityCullingModule extends Module {
	private final NumberSetting entityDistance = add(new NumberSetting("entity_distance", 96, 16, 256, 8, " blocks"));
	private final NumberSetting playerDistance = add(new NumberSetting("player_distance", 192, 16, 512, 8, " blocks"));
	private final NumberSetting decorationDistance = add(new NumberSetting("decoration_distance", 48, 8, 160, 4, " blocks"));
	private final NumberSetting itemDistance = add(new NumberSetting("item_distance", 48, 8, 160, 4, " blocks"));
	private final NumberSetting blockEntityDistance = add(new NumberSetting("block_entity_distance", 64, 16, 256, 8, " blocks"));
	private final BooleanSetting keepGlowing = add(new BooleanSetting("keep_glowing", true));
	private final BooleanSetting occlusion = add(new BooleanSetting("occlusion", true));
	private final OcclusionCuller culler = new OcclusionCuller();

	public EntityCullingModule() {
		super("entity_culling", Category.PERFORMANCE, "armor_stand", false);
	}

	public OcclusionCuller culler() {
		return culler;
	}

	@Override
	protected void onDisable() {
		culler.clear();
	}

	public boolean shouldCullBlockEntity(BlockEntity blockEntity) {
		Minecraft minecraft = Minecraft.getInstance();
		Vec3 camera = minecraft.gameRenderer.mainCamera().position();
		double limit = blockEntityDistance.get();
		if (blockEntity.getBlockPos().distToCenterSqr(camera) > limit * limit) {
			return true;
		}
		if (!occlusionActive(minecraft) || blockEntity instanceof BeaconBlockEntity || blockEntity instanceof TheEndPortalBlockEntity) {
			return false;
		}
		Level level = blockEntity.getLevel();
		return level != null && !culler.blockEntityVisible(level, blockEntity.getBlockPos(), camera.x, camera.y, camera.z);
	}

	public boolean shouldOcclude(Entity entity, AABB box, double cameraX, double cameraY, double cameraZ) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!occlusionActive(minecraft) || entity instanceof Player || entity.hasCustomName() || exempt(minecraft, entity)) {
			return false;
		}
		return !culler.entityVisible(entity.level(), entity.getId(), box.inflate(0.25), cameraX, cameraY, cameraZ);
	}

	private boolean occlusionActive(Minecraft minecraft) {
		return occlusion.enabled() && minecraft.player != null && !minecraft.player.isSpectator();
	}

	private boolean exempt(Minecraft minecraft, Entity entity) {
		Entity camera = minecraft.getCameraEntity();
		if (entity == camera || (camera != null && (entity.hasPassenger(camera) || camera.hasPassenger(entity)))) {
			return true;
		}
		return entity instanceof EnderDragon || entity instanceof WitherBoss || minecraft.shouldEntityAppearGlowing(entity);
	}

	public boolean shouldCull(Entity entity, double cameraX, double cameraY, double cameraZ) {
		Minecraft minecraft = Minecraft.getInstance();
		Entity camera = minecraft.getCameraEntity();
		if (entity == camera || (camera != null && (entity.hasPassenger(camera) || camera.hasPassenger(entity)))) {
			return false;
		}
		if (entity instanceof EnderDragon || entity instanceof WitherBoss) {
			return false;
		}
		if (keepGlowing.enabled() && minecraft.shouldEntityAppearGlowing(entity)) {
			return false;
		}
		double limit;
		if (entity instanceof Player) {
			limit = playerDistance.get();
		} else if (entity instanceof HangingEntity || entity instanceof ArmorStand) {
			limit = decorationDistance.get();
		} else if (entity instanceof ItemEntity) {
			limit = itemDistance.get();
		} else {
			limit = entityDistance.get();
		}
		return entity.distanceToSqr(cameraX, cameraY, cameraZ) > limit * limit;
	}
}

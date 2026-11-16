package net.pretupid.client.module.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.pretupid.client.hud.HudPosition;
import net.pretupid.client.hud.TextHudModule;
import net.pretupid.client.setting.BooleanSetting;

public final class LookAtHud extends TextHudModule {
	private final BooleanSetting showPosition = add(new BooleanSetting("show_position", false));
	private final BooleanSetting hideWhenEmpty = add(new BooleanSetting("hide_when_empty", true));

	public LookAtHud() {
		super("look_at", "spyglass", false, new HudPosition(HudPosition.Anchor.CENTER, 0, HudPosition.Anchor.START, 32));
	}

	@Override
	public boolean visible(Minecraft minecraft) {
		return super.visible(minecraft) && (!hideWhenEmpty.enabled() || target(minecraft) != null);
	}

	private static HitResult target(Minecraft minecraft) {
		HitResult hit = minecraft.hitResult;
		return hit == null || hit.getType() == HitResult.Type.MISS ? null : hit;
	}

	@Override
	protected String value(Minecraft minecraft, boolean preview) {
		HitResult hit = target(minecraft);
		if (hit instanceof BlockHitResult block && minecraft.level != null) {
			String name = minecraft.level.getBlockState(block.getBlockPos()).getBlock().getName().getString();
			if (!showPosition.enabled()) {
				return name;
			}
			return name + " (" + block.getBlockPos().getX() + ", " + block.getBlockPos().getY() + ", " + block.getBlockPos().getZ() + ")";
		}
		if (hit instanceof EntityHitResult entity) {
			return entity.getEntity().getDisplayName().getString();
		}
		return I18n.get("pretupid.hud.look_at.nothing");
	}
}

package net.pretupid.client.input;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;

public final class CombatTracker {
	private static final long COMBO_TIMEOUT_MS = 2500L;

	private double lastReach = -1.0;
	private long lastReachAt;
	private int combo;
	private long lastHitAt;
	private int lastTargetId = Integer.MIN_VALUE;
	private int lastHurtTime;

	public void onAttack(LocalPlayer player, Entity target) {
		lastReach = Math.sqrt(target.getBoundingBox().distanceToSqr(player.getEyePosition()));
		lastReachAt = System.currentTimeMillis();
		long now = lastReachAt;
		if (target.getId() == lastTargetId && now - lastHitAt <= COMBO_TIMEOUT_MS) {
			combo++;
		} else {
			combo = 1;
		}
		lastTargetId = target.getId();
		lastHitAt = now;
	}

	public void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null) {
			combo = 0;
			return;
		}
		int hurtTime = player.hurtTime;
		if (hurtTime > lastHurtTime) {
			combo = 0;
		}
		lastHurtTime = hurtTime;
		if (combo > 0 && System.currentTimeMillis() - lastHitAt > COMBO_TIMEOUT_MS) {
			combo = 0;
		}
	}

	public double lastReach() {
		return lastReach;
	}

	public long lastReachAt() {
		return lastReachAt;
	}

	public int combo() {
		return combo;
	}
}

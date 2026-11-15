package net.pretupid.client.module.performance;

import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.Minecraft;
import net.pretupid.client.module.Category;
import net.pretupid.client.module.Module;
import net.pretupid.client.setting.NumberSetting;

public final class ParticleLimiterModule extends Module {
	private final NumberSetting reduction = add(new NumberSetting("reduction", 25, 0, 95, 5, "%"));
	private final NumberSetting maxPerTick = add(new NumberSetting("max_per_tick", 400, 32, 4096, 32));
	private int spawnedThisTick;

	public ParticleLimiterModule() {
		super("particle_limiter", Category.PERFORMANCE, "blaze_powder", false);
	}

	@Override
	public void onTick(Minecraft minecraft) {
		spawnedThisTick = 0;
	}

	public boolean allowSpawn() {
		if (spawnedThisTick >= maxPerTick.intValue()) {
			return false;
		}
		int percent = reduction.intValue();
		if (percent > 0 && ThreadLocalRandom.current().nextInt(100) < percent) {
			return false;
		}
		spawnedThisTick++;
		return true;
	}
}

package net.pretupid.client.cosmetic;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;

public final class CapePhysics {
	public static final int SEGMENTS = 8;
	public static final int SEGMENT_HEIGHT = 16 / SEGMENTS;
	private static final float MAX_STEP = 1f / 120f;
	private static final float MAX_FRAME = 0.1f;
	private static final long EXPIRY_NANOS = 10_000_000_000L;
	private static final int MAX_TRACKED = 256;

	private static final Int2ObjectOpenHashMap<Simulation> SIMULATIONS = new Int2ObjectOpenHashMap<>();
	private static long lastSweep;

	private CapePhysics() {
	}

	private static final class Simulation {
		final float[] angle = new float[SEGMENTS];
		final float[] velocity = new float[SEGMENTS];
		long updatedAt;
		float ageInTicks = Float.NaN;
	}

	public static float[] update(AvatarRenderState state) {
		long now = System.nanoTime();
		sweep(now);
		Simulation simulation = SIMULATIONS.get(state.id);
		float drive = 6f + state.capeLean / 2f + state.capeFlap;
		float motion = Mth.clamp(state.capeLean / 60f, 0f, 1f);
		float time = state.ageInTicks / 20f;
		if (simulation == null) {
			if (SIMULATIONS.size() >= MAX_TRACKED) {
				return null;
			}
			simulation = new Simulation();
			for (int i = 0; i < SEGMENTS; i++) {
				simulation.angle[i] = target(i, drive, motion, time);
			}
			SIMULATIONS.put(state.id, simulation);
		} else if (state.ageInTicks != simulation.ageInTicks) {
			float elapsed = Math.min(MAX_FRAME, (now - simulation.updatedAt) / 1_000_000_000f);
			int steps = Math.max(1, Mth.ceil(elapsed / MAX_STEP));
			float step = elapsed / steps;
			for (int s = 0; s < steps; s++) {
				integrate(simulation, drive, motion, time, step);
			}
		}
		simulation.updatedAt = now;
		simulation.ageInTicks = state.ageInTicks;
		return simulation.angle.clone();
	}

	private static float target(int index, float drive, float motion, float time) {
		float depth = index / (float) (SEGMENTS - 1);
		float curve = drive * (0.55f + 0.9f * depth);
		float ripple = Mth.sin(time * (3f + 7f * motion) - index * 0.85f) * (0.6f + 7f * motion) * depth;
		return curve + ripple;
	}

	private static void integrate(Simulation simulation, float drive, float motion, float time, float step) {
		for (int i = 0; i < SEGMENTS; i++) {
			float depth = i / (float) (SEGMENTS - 1);
			float stiffness = 170f - 110f * depth;
			float damping = 2f * Mth.sqrt(stiffness) * 0.55f;
			float goal = target(i, drive, motion, time);
			if (i > 0) {
				goal = Mth.lerp(0.35f, goal, simulation.angle[i - 1] + (goal - target(i - 1, drive, motion, time)));
			}
			float acceleration = stiffness * (goal - simulation.angle[i]) - damping * simulation.velocity[i];
			simulation.velocity[i] += acceleration * step;
			simulation.angle[i] = Mth.clamp(simulation.angle[i] + simulation.velocity[i] * step, -10f, 160f);
		}
	}

	private static void sweep(long now) {
		if (now - lastSweep < 2_000_000_000L) {
			return;
		}
		lastSweep = now;
		SIMULATIONS.values().removeIf(simulation -> now - simulation.updatedAt > EXPIRY_NANOS);
	}

	public static void clear() {
		SIMULATIONS.clear();
	}
}

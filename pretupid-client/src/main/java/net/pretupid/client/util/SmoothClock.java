package net.pretupid.client.util;

public final class SmoothClock {
	private static final long WALL_BASE = System.currentTimeMillis();
	private static final long NANO_BASE = System.nanoTime();

	private SmoothClock() {
	}

	public static double millis() {
		return WALL_BASE + (System.nanoTime() - NANO_BASE) / 1_000_000.0;
	}

	public static long millisLong() {
		return (long) millis();
	}
}

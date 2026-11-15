package net.pretupid.client.ui;

public final class Easing {
	private Easing() {
	}

	public static float outCubic(float t) {
		float k = 1f - Math.clamp(t, 0f, 1f);
		return 1f - k * k * k;
	}

	public static float inOutCubic(float t) {
		float k = Math.clamp(t, 0f, 1f);
		return k < 0.5f ? 4f * k * k * k : 1f - (float) Math.pow(-2f * k + 2f, 3) / 2f;
	}

	public static float outBack(float t) {
		float k = Math.clamp(t, 0f, 1f);
		float c1 = 1.70158f;
		float c3 = c1 + 1f;
		return 1f + c3 * (float) Math.pow(k - 1f, 3) + c1 * (float) Math.pow(k - 1f, 2);
	}

	public static float approach(float current, float target, float speed, float deltaSeconds) {
		if (!Float.isFinite(current)) {
			return target;
		}
		float factor = 1f - (float) Math.exp(-speed * deltaSeconds);
		float next = current + (target - current) * factor;
		return Math.abs(target - next) < 0.001f ? target : next;
	}
}

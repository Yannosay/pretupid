package net.pretupid.client.nametag;

public enum NameEffect {
	NONE,
	WAVE,
	BOUNCE,
	FLOAT,
	HOP,
	SHAKE;

	public static final int FONT_COUNT = 7;
	public static final int REST_INDEX = 1;

	public boolean moves() {
		return this != NONE;
	}

	public int fontIndex(int index, int length, float seconds, float speed) {
		float time = seconds * speed;
		int offset = switch (this) {
			case NONE -> 0;
			case WAVE -> Math.round(1.5f * (float) Math.sin(time * 4.0 - index * 0.65));
			case BOUNCE -> Math.round(2f - 3f * (float) Math.abs(Math.sin(time * 3.2)));
			case FLOAT -> Math.round(1.4f * (float) Math.sin(time * 1.6));
			case HOP -> {
				double phase = Math.sin(time * 4.5 - index * 0.75);
				yield 1 - (phase > 0 ? Math.round((float) Math.pow(phase, 4) * 2f) : 0);
			}
			case SHAKE -> {
				long bucket = (long) Math.floor(time * 14f);
				long hash = (bucket * 31L + index * 17L) * 0x9E3779B97F4A7C15L;
				yield (int) Math.floorMod(hash >>> 33, 3L) - 1;
			}
		};
		return Math.clamp(REST_INDEX + offset, 0, FONT_COUNT - 1);
	}
}

package net.pretupid.client.nametag;

public enum NameEffect {
	NONE,
	WAVE,
	BOUNCE,
	FLOAT,
	HOP,
	SHAKE;

	private static final String MARKER = "\u0000pretupid-fx:";
	private static final NameEffect[] VALUES = values();

	public boolean moves() {
		return this != NONE;
	}

	public String marker(int index, float speed) {
		return MARKER + ordinal() + ':' + index + ':' + Float.floatToRawIntBits(speed);
	}

	public float offsetX(int index, float seconds, float speed) {
		if (this != SHAKE) {
			return 0f;
		}
		float time = seconds * speed;
		return 0.45f * (float) (Math.sin(time * 29.0 + index * 2.3) * 0.6 + Math.sin(time * 17.0 + index * 5.1) * 0.4);
	}

	public float offsetY(int index, float seconds, float speed) {
		float time = seconds * speed;
		return switch (this) {
			case NONE -> 0f;
			case WAVE -> -1.6f * (float) Math.sin(time * 3.6 - index * 0.55);
			case BOUNCE -> -2.6f * (float) Math.abs(Math.sin(time * 3.2)) + 1f;
			case FLOAT -> -1.4f * (float) Math.sin(time * 1.6);
			case HOP -> {
				double phase = Math.sin(time * 4.5 - index * 0.75);
				yield phase > 0 ? -2.4f * (float) (phase * phase * phase) + 0.5f : 0.5f;
			}
			case SHAKE -> 0.45f * (float) (Math.sin(time * 23.0 + index * 3.7) * 0.6 + Math.sin(time * 13.0 + index * 1.9) * 0.4);
		};
	}

	public static Offset decode(String insertion, float seconds) {
		if (insertion == null || !insertion.startsWith(MARKER)) {
			return null;
		}
		int first = insertion.indexOf(':', MARKER.length());
		int second = first < 0 ? -1 : insertion.indexOf(':', first + 1);
		if (second < 0) {
			return null;
		}
		try {
			int ordinal = Integer.parseInt(insertion, MARKER.length(), first, 10);
			int index = Integer.parseInt(insertion, first + 1, second, 10);
			float speed = Float.intBitsToFloat(Integer.parseInt(insertion, second + 1, insertion.length(), 10));
			if (ordinal < 0 || ordinal >= VALUES.length || !Float.isFinite(speed)) {
				return null;
			}
			NameEffect effect = VALUES[ordinal];
			return new Offset(effect.offsetX(index, seconds, speed), effect.offsetY(index, seconds, speed));
		} catch (NumberFormatException exception) {
			return null;
		}
	}

	public record Offset(float x, float y) {
	}
}

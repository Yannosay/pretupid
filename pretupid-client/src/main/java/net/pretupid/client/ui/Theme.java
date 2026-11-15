package net.pretupid.client.ui;

import net.pretupid.client.PretupidClient;
import net.pretupid.client.util.Colors;

public final class Theme {
	public static final int BACKDROP_TOP = 0x66050810;
	public static final int BACKDROP_BOTTOM = 0xAA020306;
	public static final int WINDOW = 0xD00E1117;
	public static final int WINDOW_BORDER = 0x40FFFFFF;
	public static final int WINDOW_HIGHLIGHT = 0x1CFFFFFF;
	public static final int SIDEBAR = 0x40000000;
	public static final int SURFACE = 0x14FFFFFF;
	public static final int SURFACE_HOVER = 0x24FFFFFF;
	public static final int SURFACE_ACTIVE = 0x30FFFFFF;
	public static final int SURFACE_BORDER = 0x1EFFFFFF;
	public static final int INPUT = 0x60000000;
	public static final int DIVIDER = 0x18FFFFFF;
	public static final int TEXT = 0xFFF2F4F8;
	public static final int TEXT_MUTED = 0xFFA3ABB9;
	public static final int TEXT_DIM = 0xFF6C7584;
	public static final int SUCCESS = 0xFF4ADE80;
	public static final int WARNING = 0xFFFBBF24;
	public static final int DANGER = 0xFFF87171;
	public static final int TOGGLE_OFF = 0x50FFFFFF;
	public static final int POPUP = 0xF0141821;

	private Theme() {
	}

	public static int accent() {
		PretupidClient client = PretupidClient.getOrNull();
		if (client == null) {
			return 0xFF4A9EFF;
		}
		return Colors.withAlpha(client.settings().accent.resolve(), 255);
	}

	public static int accent(int alpha) {
		return Colors.withAlpha(accent(), alpha);
	}

	public static int accentText() {
		return Colors.brighten(accent(), 0.35f);
	}
}

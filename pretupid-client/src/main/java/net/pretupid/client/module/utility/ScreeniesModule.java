package net.pretupid.client.module.utility;

import net.pretupid.client.module.Category;
import net.pretupid.client.module.Module;
import net.pretupid.client.setting.BooleanSetting;
import net.pretupid.client.setting.EnumSetting;

public final class ScreeniesModule extends Module {
	public enum Resolution {
		WINDOW(1),
		DOUBLE(2),
		TRIPLE(3);

		private final int scale;

		Resolution(int scale) {
			this.scale = scale;
		}

		public int scale() {
			return scale;
		}
	}

	private final EnumSetting<Resolution> resolution = add(new EnumSetting<>("resolution", Resolution.DOUBLE));
	private final BooleanSetting openEditor = add(new BooleanSetting("open_editor", true));

	public ScreeniesModule() {
		super("screenies", Category.UTILITY, "painting", true);
	}

	public int scale() {
		return resolution.get().scale();
	}

	public boolean opensEditor() {
		return openEditor.enabled();
	}
}

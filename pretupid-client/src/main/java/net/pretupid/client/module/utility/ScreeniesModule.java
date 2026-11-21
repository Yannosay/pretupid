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

	public enum ExportSize {
		ORIGINAL(0),
		P720(720),
		K1(1080),
		K2(1440),
		K4(2160);

		private final int shortEdge;

		ExportSize(int shortEdge) {
			this.shortEdge = shortEdge;
		}

		public java.awt.image.BufferedImage apply(java.awt.image.BufferedImage image) {
			int current = Math.min(image.getWidth(), image.getHeight());
			if (shortEdge <= 0 || current == shortEdge) {
				return image;
			}
			double scale = shortEdge / (double) current;
			int width = Math.max(1, (int) Math.round(image.getWidth() * scale));
			int height = Math.max(1, (int) Math.round(image.getHeight() * scale));
			java.awt.image.BufferedImage scaled = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
			var g = scaled.createGraphics();
			g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			g.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY);
			g.drawImage(image, 0, 0, width, height, null);
			g.dispose();
			return scaled;
		}
	}

	public enum ExportAspect {
		ORIGINAL(0, 0),
		WIDE(16, 9),
		STANDARD(4, 3),
		SQUARE(1, 1),
		TALL(4, 5),
		PORTRAIT(9, 16);

		private final int width;
		private final int height;

		ExportAspect(int width, int height) {
			this.width = width;
			this.height = height;
		}

		public java.awt.image.BufferedImage crop(java.awt.image.BufferedImage image, double zoom, double focusX, double focusY) {
			double current = image.getWidth() / (double) image.getHeight();
			double target = width > 0 ? width / (double) height : current;
			double baseWidth = current > target ? image.getHeight() * target : image.getWidth();
			double baseHeight = current > target ? image.getHeight() : image.getWidth() / target;
			int cropWidth = Math.clamp(Math.round(baseWidth / zoom), 1, image.getWidth());
			int cropHeight = Math.clamp(Math.round(baseHeight / zoom), 1, image.getHeight());
			if (cropWidth == image.getWidth() && cropHeight == image.getHeight()) {
				return image;
			}
			int x = (int) Math.round((image.getWidth() - cropWidth) * Math.clamp(focusX, 0.0, 1.0));
			int y = (int) Math.round((image.getHeight() - cropHeight) * Math.clamp(focusY, 0.0, 1.0));
			return image.getSubimage(x, y, cropWidth, cropHeight);
		}
	}

	private final EnumSetting<Resolution> resolution = add(new EnumSetting<>("resolution", Resolution.DOUBLE));
	private final EnumSetting<ExportSize> exportSize = add(new EnumSetting<>("export_size", ExportSize.K1));
	private final EnumSetting<ExportAspect> exportAspect = add(new EnumSetting<>("export_aspect", ExportAspect.ORIGINAL));
	private final BooleanSetting openEditor = add(new BooleanSetting("open_editor", true));
	private final BooleanSetting showNametags = add(new BooleanSetting("show_nametags", true));
	private final BooleanSetting showPlayers = add(new BooleanSetting("show_players", true));
	private final BooleanSetting showEntities = add(new BooleanSetting("show_entities", true));

	public ScreeniesModule() {
		super("screenies", Category.UTILITY, "painting", true);
	}

	public int scale() {
		return resolution.get().scale();
	}

	public EnumSetting<ExportSize> exportSize() {
		return exportSize;
	}

	public EnumSetting<ExportAspect> exportAspect() {
		return exportAspect;
	}

	public BooleanSetting showNametags() {
		return showNametags;
	}

	public BooleanSetting showPlayers() {
		return showPlayers;
	}

	public BooleanSetting showEntities() {
		return showEntities;
	}

	public boolean opensEditor() {
		return openEditor.enabled();
	}
}

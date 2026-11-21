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

		public java.awt.image.BufferedImage apply(java.awt.image.BufferedImage image) {
			if (width <= 0) {
				return image;
			}
			double target = width / (double) height;
			double current = image.getWidth() / (double) image.getHeight();
			if (Math.abs(target - current) < 1e-3) {
				return image;
			}
			int cropWidth = current > target ? (int) Math.round(image.getHeight() * target) : image.getWidth();
			int cropHeight = current > target ? image.getHeight() : (int) Math.round(image.getWidth() / target);
			int x = (image.getWidth() - cropWidth) / 2;
			int y = (image.getHeight() - cropHeight) / 2;
			java.awt.image.BufferedImage cropped = new java.awt.image.BufferedImage(cropWidth, cropHeight, java.awt.image.BufferedImage.TYPE_INT_ARGB);
			var g = cropped.createGraphics();
			g.drawImage(image, -x, -y, null);
			g.dispose();
			return cropped;
		}
	}

	private final EnumSetting<Resolution> resolution = add(new EnumSetting<>("resolution", Resolution.DOUBLE));
	private final EnumSetting<ExportSize> exportSize = add(new EnumSetting<>("export_size", ExportSize.K1));
	private final EnumSetting<ExportAspect> exportAspect = add(new EnumSetting<>("export_aspect", ExportAspect.ORIGINAL));
	private final BooleanSetting openEditor = add(new BooleanSetting("open_editor", true));

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

	public boolean opensEditor() {
		return openEditor.enabled();
	}
}

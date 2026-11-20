package net.pretupid.client.screenie;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ScreenieComposer {
	private static final List<String> MEME_FONTS = List.of("Impact", "Anton", "Arial Black", "Haettenschweiler");
	private static String memeFamily;

	private ScreenieComposer() {
	}

	public record Frame(int canvasWidth, int canvasHeight, int imageX, int imageY, int imageWidth, int imageHeight) {
	}

	public static Frame frame(int sourceWidth, int sourceHeight, int maxWidth, MemeTemplate template) {
		float side = template.side() * sourceHeight;
		float canvasWidthUnscaled = sourceWidth + 2 * side;
		float scale = maxWidth > 0 && canvasWidthUnscaled > maxWidth ? maxWidth / canvasWidthUnscaled : 1f;
		int imageWidth = Math.max(1, Math.round(sourceWidth * scale));
		int imageHeight = Math.max(1, Math.round(sourceHeight * scale));
		int sidePx = Math.round(side * scale);
		int topPx = Math.round(template.top() * sourceHeight * scale);
		int bottomPx = Math.round(template.bottom() * sourceHeight * scale);
		return new Frame(imageWidth + 2 * sidePx, imageHeight + topPx + bottomPx, sidePx, topPx, imageWidth, imageHeight);
	}

	public static BufferedImage compose(BufferedImage source, ScreenieLook look, List<TextLayer> layers, MemeTemplate template, int maxWidth) {
		Frame frame = frame(source.getWidth(), source.getHeight(), maxWidth, template);
		BufferedImage image = new BufferedImage(frame.imageWidth(), frame.imageHeight(), BufferedImage.TYPE_INT_ARGB);
		var imageGraphics = image.createGraphics();
		imageGraphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		imageGraphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		imageGraphics.drawImage(source, 0, 0, frame.imageWidth(), frame.imageHeight(), null);
		imageGraphics.dispose();
		look.apply(image);
		BufferedImage canvas = new BufferedImage(frame.canvasWidth(), frame.canvasHeight(), BufferedImage.TYPE_INT_ARGB);
		var g = canvas.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		if (template != MemeTemplate.NONE && template != MemeTemplate.CLASSIC) {
			g.setColor(new Color(template.background(), true));
			g.fillRect(0, 0, frame.canvasWidth(), frame.canvasHeight());
		}
		g.drawImage(image, frame.imageX(), frame.imageY(), null);
		if (template == MemeTemplate.POSTER) {
			int border = Math.max(1, frame.imageWidth() / 300);
			g.setColor(Color.WHITE);
			g.setStroke(new BasicStroke(border));
			int gap = border * 3;
			g.drawRect(frame.imageX() - gap, frame.imageY() - gap, frame.imageWidth() + gap * 2 - 1, frame.imageHeight() + gap * 2 - 1);
		}
		for (TextLayer layer : layers) {
			if (!layer.text().isBlank()) {
				drawText(g, layer, frame.canvasWidth(), frame.canvasHeight());
			}
		}
		g.dispose();
		return canvas;
	}

	public static Rectangle textBounds(TextLayer layer, int canvasWidth, int canvasHeight) {
		BufferedImage scratch = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		var g = scratch.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		List<TextLayout> lines = layout(g.getFontRenderContext(), layer, canvasWidth, canvasHeight);
		g.dispose();
		return bounds(lines, layer, canvasWidth, canvasHeight);
	}

	private static Rectangle bounds(List<TextLayout> lines, TextLayer layer, int canvasWidth, int canvasHeight) {
		float lineHeight = lineHeight(layer, canvasHeight);
		int width = 0;
		for (TextLayout line : lines) {
			width = Math.max(width, Math.round(line.getAdvance()));
		}
		int height = Math.round(lineHeight * lines.size());
		int cx = Math.round(layer.x() * canvasWidth);
		int cy = Math.round(layer.y() * canvasHeight);
		return new Rectangle(cx - width / 2, cy - height / 2, width, height);
	}

	private static float lineHeight(TextLayer layer, int canvasHeight) {
		return layer.size() * canvasHeight * 1.12f;
	}

	private static Font font(TextLayer layer, int canvasHeight) {
		float size = Math.max(6f, layer.size() * canvasHeight);
		String family = layer.style() == TextLayer.Style.MEME ? memeFamily() : Font.SANS_SERIF;
		return new Font(family, Font.BOLD, 1).deriveFont(size);
	}

	private static synchronized String memeFamily() {
		if (memeFamily == null) {
			Set<String> installed = Set.of(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames(Locale.ROOT));
			memeFamily = MEME_FONTS.stream().filter(installed::contains).findFirst().orElse(Font.SANS_SERIF);
		}
		return memeFamily;
	}

	private static List<TextLayout> layout(FontRenderContext context, TextLayer layer, int canvasWidth, int canvasHeight) {
		Font font = font(layer, canvasHeight);
		String text = layer.style() == TextLayer.Style.MEME ? layer.text().toUpperCase(Locale.ROOT) : layer.text();
		float maxWidth = canvasWidth * 0.92f;
		List<TextLayout> lines = new ArrayList<>();
		for (String paragraph : text.split("\n")) {
			List<String> words = new ArrayList<>(Arrays.asList(paragraph.trim().split("\\s+")));
			StringBuilder current = new StringBuilder();
			for (String word : words) {
				String candidate = current.isEmpty() ? word : current + " " + word;
				if (!current.isEmpty() && new TextLayout(candidate, font, context).getAdvance() > maxWidth) {
					lines.add(new TextLayout(current.toString(), font, context));
					current = new StringBuilder(word);
				} else {
					current = new StringBuilder(candidate);
				}
			}
			if (!current.isEmpty()) {
				lines.add(new TextLayout(current.toString(), font, context));
			}
		}
		return lines;
	}

	private static void drawText(java.awt.Graphics2D g, TextLayer layer, int canvasWidth, int canvasHeight) {
		List<TextLayout> lines = layout(g.getFontRenderContext(), layer, canvasWidth, canvasHeight);
		if (lines.isEmpty()) {
			return;
		}
		Rectangle box = bounds(lines, layer, canvasWidth, canvasHeight);
		float lineHeight = lineHeight(layer, canvasHeight);
		float y = box.y;
		Color fill = new Color(layer.color(), true);
		for (TextLayout line : lines) {
			float x = layer.x() * canvasWidth - line.getAdvance() / 2f;
			float baseline = y + (lineHeight + line.getAscent() - line.getDescent()) / 2f;
			Shape outline = line.getOutline(AffineTransform.getTranslateInstance(x, baseline));
			switch (layer.style()) {
				case MEME -> {
					g.setColor(Color.BLACK);
					g.setStroke(new BasicStroke(Math.max(2f, layer.size() * canvasHeight * 0.14f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
					g.draw(outline);
					g.setColor(fill);
					g.fill(outline);
				}
				case CLEAN -> {
					float offset = Math.max(1f, layer.size() * canvasHeight * 0.06f);
					g.setColor(new Color(0, 0, 0, 150));
					g.fill(line.getOutline(AffineTransform.getTranslateInstance(x + offset, baseline + offset)));
					g.setColor(fill);
					g.fill(outline);
				}
				case LABEL -> {
					g.setColor(fill);
					g.fill(outline);
				}
			}
			y += lineHeight;
		}
	}
}

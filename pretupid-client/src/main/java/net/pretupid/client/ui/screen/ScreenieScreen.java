package net.pretupid.client.ui.screen;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.screenie.MemeShot;
import net.pretupid.client.screenie.MemeTemplate;
import net.pretupid.client.screenie.ScreenieComposer;
import net.pretupid.client.screenie.ScreenieLook;
import net.pretupid.client.screenie.Screenies;
import net.pretupid.client.screenie.TextLayer;
import net.pretupid.client.setting.NumberSetting;
import net.pretupid.client.social.SocialService;
import net.pretupid.client.ui.Draw;
import net.pretupid.client.ui.Popup;
import net.pretupid.client.ui.PretupidScreen;
import net.pretupid.client.ui.ScrollState;
import net.pretupid.client.ui.TextInput;
import net.pretupid.client.ui.Theme;
import net.pretupid.client.ui.UiContext;
import net.pretupid.client.ui.Widgets;
import net.pretupid.client.platform.Platform;
import net.pretupid.client.util.PlatformOpener;

public final class ScreenieScreen extends PretupidScreen {
	private enum Tab {
		LOOK,
		TEXT,
		FRAME
	}

	private static final int PANEL_WIDTH = 176;
	private static final int FRAME_HEIGHT = 112;
	private static final int[] TEXT_COLORS = {0xFFFFFFFF, 0xFF111111, 0xFFFACC15, 0xFFF43F5E, 0xFF4ADE80, 0xFF5AB0FF, 0xFFC084FC, 0xFFF97316};

	private BufferedImage source;
	private final Screenies screenies = PretupidClient.get().screenies();
	private final ScrollState panelScroll = new ScrollState();
	private final TextInput textInput = new TextInput(TextLayer.MAX_LENGTH);
	private final NumberSetting brightness = new NumberSetting("brightness", 0, -1, 1, 0.05, "");
	private final NumberSetting contrast = new NumberSetting("contrast", 0, -0.8, 1, 0.05, "");
	private final NumberSetting saturation = new NumberSetting("saturation", 0, -1, 1, 0.05, "");
	private final NumberSetting warmth = new NumberSetting("warmth", 0, -1, 1, 0.05, "");
	private final NumberSetting vignette = new NumberSetting("vignette", 0, 0, 1, 0.05, "");
	private final NumberSetting textSize = new NumberSetting("text_size", 0.08, 0.03, 0.25, 0.01, "");
	private final NumberSetting textAngle = new NumberSetting("text_angle", 0, -180, 180, 1, "°");
	private final NumberSetting zoom = new NumberSetting("zoom", 1, 1, 4, 0.05, "x");
	private final List<TextLayer> layers = new ArrayList<>();
	private final List<TextInput> slotInputs = new ArrayList<>();
	private final boolean meme;
	private final net.pretupid.client.module.utility.ScreeniesModule module = PretupidClient.get().modules().get(net.pretupid.client.module.utility.ScreeniesModule.class);
	private net.pretupid.client.module.utility.ScreeniesModule.ExportAspect appliedAspect;
	private double focusX = 0.5;
	private double focusY = 0.5;
	private boolean retakeQueued;
	private BufferedImage cropped;
	private boolean retaking;
	private MemeShot shot = MemeShot.NORMAL;
	private ScreenieLook look = ScreenieLook.NATURAL;
	private MemeTemplate template = MemeTemplate.NONE;
	private Tab tab = Tab.LOOK;
	private int selected = -1;
	private Identifier texture;
	private boolean dirty = true;
	private boolean rendering;
	private boolean syncing;
	private boolean working;
	private Component status;
	private boolean statusError;
	private long statusAt;
	private int previewX;
	private int previewY;
	private int previewW;
	private int previewH;

	public ScreenieScreen(Screen parent, BufferedImage source) {
		this(parent, source, false);
	}

	public ScreenieScreen(Screen parent, BufferedImage source, MemeShot shot, MemeTemplate template, List<String> texts) {
		this(parent, source, true);
		this.shot = shot;
		slotInputs.clear();
		for (int i = 0; i < texts.size(); i++) {
			slotInputs.add(new TextInput(TextLayer.MAX_LENGTH));
			slotInputs.get(i).setValueSilently(texts.get(i));
		}
		applyTemplate(template);
	}

	public ScreenieScreen(Screen parent, BufferedImage source, boolean meme) {
		super(Component.translatable(meme ? "pretupid.meme.title" : "pretupid.screenie.title"), parent);
		this.source = source;
		this.meme = meme;
		if (meme) {
			applyTemplate(MemeTemplate.CLASSIC);
		}
		textInput.onSubmit(() -> {
			if (selected >= 0 && selected < layers.size()) {
				select(-1);
			} else {
				addText();
			}
		});
		textInput.onChange(value -> {
			if (!syncing && selected >= 0 && selected < layers.size()) {
				layers.set(selected, layers.get(selected).withText(value));
				dirty = true;
			}
		});
		textAngle.onChange(value -> {
			if (!syncing && selected >= 0 && selected < layers.size()) {
				layers.set(selected, layers.get(selected).withAngle(value.floatValue()));
				dirty = true;
			}
		});
		zoom.onChange(value -> invalidateCrop());
		brightness.onChange(value -> updateLook(look.withBrightness(value.floatValue())));
		contrast.onChange(value -> updateLook(look.withContrast(value.floatValue())));
		saturation.onChange(value -> updateLook(look.withSaturation(value.floatValue())));
		warmth.onChange(value -> updateLook(look.withWarmth(value.floatValue())));
		vignette.onChange(value -> updateLook(look.withVignette(value.floatValue())));
		textSize.onChange(value -> {
			if (!syncing && selected >= 0 && selected < layers.size()) {
				layers.set(selected, layers.get(selected).withSize(value.floatValue()));
				dirty = true;
			}
		});
	}

	private void invalidateCrop() {
		cropped = null;
		dirty = true;
	}

	private TextLayer displayLayer(int index) {
		TextLayer layer = layers.get(index);
		if (!layer.text().isBlank()) {
			return layer;
		}
		String placeholder = Component.translatable("pretupid.screenie.placeholder", index + 1).getString();
		return layer.withText(layer.style() == TextLayer.Style.MEME ? placeholder.toUpperCase(Locale.ROOT) : placeholder).withColor(layer.color() & 0xFFFFFF | 0xB0000000);
	}

	private List<TextLayer> displayLayers() {
		List<TextLayer> result = new ArrayList<>(layers.size());
		for (int i = 0; i < layers.size(); i++) {
			result.add(displayLayer(i));
		}
		return result;
	}

	public void onRetaken(BufferedImage image, MemeShot next) {
		retaking = false;
		if (image != null) {
			source = image;
			shot = next;
			invalidateCrop();
		}
	}

	private void updateLook(ScreenieLook next) {
		if (syncing) {
			return;
		}
		look = next;
		dirty = true;
	}

	private void show(Component text, boolean error) {
		status = text;
		statusError = error;
		statusAt = System.currentTimeMillis();
	}

	private void select(int index) {
		selected = index;
		if (index >= 0 && index < layers.size()) {
			syncing = true;
			try {
				textSize.set((double) Math.round(layers.get(index).size() * 100f) / 100.0);
				textAngle.set((double) Math.round(layers.get(index).angle()));
				if (!meme) {
					textInput.setValueSilently(layers.get(index).text());
				}
			} finally {
				syncing = false;
			}
		} else if (!meme) {
			textInput.setValueSilently("");
		}
	}

	private void addText() {
		String text = selected >= 0 && selected < layers.size() ? "" : textInput.value().strip();
		float y = layers.isEmpty() ? 0.12f : Math.min(0.88f, 0.12f + layers.size() * 0.12f);
		layers.add(new TextLayer(text, 0.5f, y, 0.08f, 0xFFFFFFFF, template == MemeTemplate.NONE ? TextLayer.Style.CLEAN : TextLayer.Style.MEME));
		select(layers.size() - 1);
		dirty = true;
	}

	private void applyTemplate(MemeTemplate next) {
		List<String> texts = slotInputs.stream().map(TextInput::value).toList();
		template = next;
		layers.clear();
		layers.addAll(next.layers(texts));
		slotInputs.clear();
		for (int i = 0; i < next.slots().size(); i++) {
			int index = i;
			TextInput input = new TextInput(TextLayer.MAX_LENGTH);
			input.setValueSilently(i < texts.size() ? texts.get(i) : "");
			input.onChange(value -> updateSlot(index));
			slotInputs.add(input);
		}
		select(-1);
		dirty = true;
	}

	private void updateSlot(int index) {
		List<TextLayer> fresh = template.layers(slotInputs.stream().map(TextInput::value).toList());
		if (index < layers.size() && index < fresh.size()) {
			layers.set(index, layers.get(index).withText(fresh.get(index).text()));
			dirty = true;
		}
	}

	public Screen parentScreen() {
		return parent;
	}

	private BufferedImage working() {
		var aspect = module.exportAspect().get();
		if (cropped == null || aspect != appliedAspect) {
			appliedAspect = aspect;
			cropped = aspect.crop(source, zoom.get(), focusX, focusY);
			dirty = true;
		}
		return cropped;
	}

	private BufferedImage exported() {
		return module.exportSize().get().apply(working());
	}

	private String exportLabel() {
		BufferedImage image = working();
		int shortEdge = Math.min(image.getWidth(), image.getHeight());
		var size = module.exportSize().get();
		double scale = size == net.pretupid.client.module.utility.ScreeniesModule.ExportSize.ORIGINAL ? 1.0 : (double) switch (size) {
			case P720 -> 720;
			case K1 -> 1080;
			case K2 -> 1440;
			case K4 -> 2160;
			default -> shortEdge;
		} / shortEdge;
		return Math.round(image.getWidth() * scale) + " × " + Math.round(image.getHeight() * scale);
	}

	@Override
	public void removed() {
		screenies.releasePreview();
		super.removed();
	}

	@Override
	protected void drawContent(UiContext ui) {
		var g = ui.g();
		BufferedImage current = working();
		if (retakeQueued && !retaking && !screenies.busy()) {
			retakeQueued = false;
			retake(shot);
		}
		if (dirty && !rendering) {
			dirty = false;
			rendering = true;
			screenies.preview(current, look, displayLayers(), template).whenComplete((id, error) -> {
				rendering = false;
				if (id != null) {
					texture = id;
				}
			});
		}
		Draw.rect(g, 0, 0, width, height, 0xF00B0D12);
		Draw.textShadow(g, font, title, 10, 8, Theme.TEXT);
		Draw.text(g, font, Component.literal(exportLabel()), 14 + font.width(title), 8, Theme.TEXT_DIM);
		Component aspectLabel = Component.translatable("pretupid.screenie.export_aspect");
		Component sizeLabel = Component.translatable("pretupid.screenie.export_size");
		int aspectX = width - 8 - 64;
		Widgets.cycler(ui, "screenie.export.aspect", aspectX, 4, 64, module.exportAspect());
		Draw.text(g, font, aspectLabel, aspectX - 4 - font.width(aspectLabel), 7, Theme.TEXT_MUTED);
		int sizeX = aspectX - 12 - font.width(aspectLabel) - 64;
		Widgets.cycler(ui, "screenie.export.size", sizeX, 4, 64, module.exportSize());
		Draw.text(g, font, sizeLabel, sizeX - 4 - font.width(sizeLabel), 7, Theme.TEXT_MUTED);
		int areaX = 8;
		int areaY = 24;
		int areaW = width - PANEL_WIDTH - 24;
		int areaH = height - 24 - 34;
		drawPreview(ui, areaX, areaY, areaW, areaH);
		drawPanel(ui, width - PANEL_WIDTH - 8, areaY, PANEL_WIDTH, areaH);
		drawActions(ui, areaY + areaH + 8);
	}

	private void drawPreview(UiContext ui, int x, int y, int w, int h) {
		var g = ui.g();
		Draw.rect(g, x, y, w, h, 0xFF05070A);
		ScreenieComposer.Frame frame = ScreenieComposer.frame(working().getWidth(), working().getHeight(), Screenies.PREVIEW_WIDTH, template);
		float scale = Math.min(w / (float) frame.canvasWidth(), h / (float) frame.canvasHeight());
		previewW = Math.max(1, Math.round(frame.canvasWidth() * scale));
		previewH = Math.max(1, Math.round(frame.canvasHeight() * scale));
		previewX = x + (w - previewW) / 2;
		previewY = y + (h - previewH) / 2;
		if (retaking) {
			Draw.centered(g, font, Component.translatable("pretupid.screenie.rendering"), x + w / 2, y + h / 2 - 4, Theme.TEXT_DIM);
		} else if (texture != null) {
			g.blit(texture, previewX, previewY, 0f, 0f, previewW, previewH, frame.canvasWidth(), frame.canvasHeight(), frame.canvasWidth(), frame.canvasHeight());
		} else {
			Draw.centered(g, font, Component.translatable("pretupid.screenie.rendering"), x + w / 2, y + h / 2 - 4, Theme.TEXT_DIM);
		}
		for (int i = 0; i < layers.size(); i++) {
			Rectangle bounds = ScreenieComposer.rotatedBounds(displayLayer(i), previewW, previewH);
			if (i == selected) {
				Draw.border(g, previewX + bounds.x - 2, previewY + bounds.y - 2, bounds.width + 4, bounds.height + 4, Theme.accent());
			}
		}
		ui.interactive(previewX, previewY, previewW, previewH, new UiContext.Handler() {
			private int dragging = -1;
			private boolean panning;
			private double lastX;
			private double lastY;

			@Override
			public boolean click(double mouseX, double mouseY, int button) {
				if (button != 0) {
					return false;
				}
				dragging = -1;
				panning = false;
				for (int i = layers.size() - 1; i >= 0; i--) {
					if (ScreenieComposer.hits(displayLayer(i), previewW, previewH, mouseX - previewX, mouseY - previewY, 4)) {
						dragging = i;
						select(i);
						tab = Tab.TEXT;
						return true;
					}
				}
				select(-1);
				panning = true;
				lastX = mouseX;
				lastY = mouseY;
				return true;
			}

			@Override
			public boolean scroll(double amount) {
				zoom.set(zoom.get() + Math.signum(amount) * 0.1);
				return true;
			}

			@Override
			public void drag(double mouseX, double mouseY) {
				if (dragging >= 0 && dragging < layers.size()) {
					layers.set(dragging, layers.get(dragging).withPosition((float) ((mouseX - previewX) / previewW), (float) ((mouseY - previewY) / previewH)));
					dirty = true;
				} else if (panning) {
					BufferedImage view = working();
					int spareX = source.getWidth() - view.getWidth();
					int spareY = source.getHeight() - view.getHeight();
					if (spareX > 0) {
						focusX = Math.clamp(focusX - (mouseX - lastX) / previewW * view.getWidth() / spareX, 0.0, 1.0);
					}
					if (spareY > 0) {
						focusY = Math.clamp(focusY - (mouseY - lastY) / previewH * view.getHeight() / spareY, 0.0, 1.0);
					}
					lastX = mouseX;
					lastY = mouseY;
					if (spareX > 0 || spareY > 0) {
						invalidateCrop();
					}
				}
			}

			@Override
			public boolean clickSound() {
				return false;
			}
		});
	}

	private void drawPanel(UiContext ui, int x, int y, int w, int h) {
		var g = ui.g();
		Draw.chamfer(g, x, y, w, h, Theme.SURFACE);
		if (meme) {
			int innerY = y + 4;
			int innerH = h - 8;
			int innerW = w - 8;
			panelScroll.begin(ui, "screenie.panel", x + 4, innerY, innerW, innerH, 16 + 2 * 20 + 8 + slotInputs.size() * 22 + 16 + 4 * 20 + 96 + FRAME_HEIGHT);
			drawMeme(ui, x + 4, innerY - panelScroll.offset(), innerW - 6);
			panelScroll.end(ui, x + 4, innerY, innerW, innerH);
			return;
		}
		Tab[] tabs = Tab.values();
		int tabW = (w - 8 - (tabs.length - 1) * 2) / tabs.length;
		for (int i = 0; i < tabs.length; i++) {
			Tab entry = tabs[i];
			Widgets.button(ui, "screenie.tab." + entry, x + 4 + i * (tabW + 2), y + 4, tabW, 16,
				Component.translatable("pretupid.screenie.tab." + entry.name().toLowerCase(Locale.ROOT)),
				entry == tab ? Widgets.ButtonStyle.PRIMARY : Widgets.ButtonStyle.GHOST, () -> {
					tab = entry;
					panelScroll.reset();
					if (entry == Tab.TEXT && layers.isEmpty()) {
						addText();
					}
				});
		}
		int innerY = y + 26;
		int innerH = h - 30;
		int innerW = w - 8;
		int content = switch (tab) {
			case LOOK -> 4 * 20 + 6 + 5 * 26 + 24;
			case TEXT -> 22 + layers.size() * 20 + 96;
			case FRAME -> FRAME_HEIGHT;
		};
		panelScroll.begin(ui, "screenie.panel", x + 4, innerY, innerW, innerH, content);
		int top = innerY - panelScroll.offset();
		switch (tab) {
			case LOOK -> drawLook(ui, x + 4, top, innerW - 6);
			case TEXT -> drawText(ui, x + 4, top, innerW - 6);
			case FRAME -> drawFrame(ui, x + 4, top, innerW - 6);
		}
		panelScroll.end(ui, x + 4, innerY, innerW, innerH);
	}

	private void drawLook(UiContext ui, int x, int y, int w) {
		ScreenieLook.Filter[] filters = ScreenieLook.Filter.values();
		int columnW = (w - 2) / 2;
		for (int i = 0; i < filters.length; i++) {
			ScreenieLook.Filter filter = filters[i];
			Widgets.button(ui, "screenie.filter." + filter, x + (i % 2) * (columnW + 2), y + (i / 2) * 20, columnW, 18,
				Component.translatable("pretupid.screenie.filter." + filter.name().toLowerCase(Locale.ROOT)),
				look.filter() == filter ? Widgets.ButtonStyle.PRIMARY : Widgets.ButtonStyle.SECONDARY, () -> {
					look = look.withFilter(filter);
					dirty = true;
				});
		}
		int rowY = y + ((filters.length + 1) / 2) * 20 + 6;
		for (NumberSetting setting : List.of(brightness, contrast, saturation, warmth, vignette)) {
			Draw.text(ui.g(), font, Component.translatable("pretupid.screenie.look." + setting.id()), x, rowY, Theme.TEXT_MUTED);
			Widgets.slider(ui, "screenie.look." + setting.id(), x, rowY + 10, w, setting);
			rowY += 26;
		}
		Widgets.button(ui, "screenie.look.reset", x, rowY, w, 16, Component.translatable("pretupid.ui.reset"), Widgets.ButtonStyle.GHOST, () -> {
			syncing = true;
			try {
				for (NumberSetting setting : List.of(brightness, contrast, saturation, warmth, vignette)) {
					setting.reset();
				}
			} finally {
				syncing = false;
			}
			look = ScreenieLook.NATURAL;
			dirty = true;
		});
	}

	private void drawFrame(UiContext ui, int x, int y, int w) {
		var g = ui.g();
		Draw.text(g, font, Component.translatable("pretupid.screenie.zoom"), x, y, Theme.TEXT_MUTED);
		Widgets.slider(ui, "screenie.frame.zoom", x, y + 10, w, zoom);
		int rowY = y + 26;
		Widgets.button(ui, "screenie.frame.reset", x, rowY, w, 16, Component.translatable("pretupid.ui.reset"), Widgets.ButtonStyle.GHOST, () -> {
			focusX = 0.5;
			focusY = 0.5;
			zoom.set(1.0);
			invalidateCrop();
		});
		rowY += 22;
		for (var setting : List.of(module.showNametags(), module.showPlayers(), module.showEntities())) {
			Draw.text(g, font, Draw.ellipsize(font, Component.translatable("pretupid.module.screenies." + setting.id()), w - 30), x, rowY + 4, Theme.TEXT_MUTED);
			Widgets.toggle(ui, "screenie.frame." + setting.id(), x + w - 24, rowY, setting.enabled(), () -> {
				setting.toggle();
				retakeQueued = true;
			});
			rowY += 20;
		}
	}

	private void drawText(UiContext ui, int x, int y, int w) {
		var g = ui.g();
		textInput.draw(ui, x, y, w - 36, 18, Component.translatable("pretupid.screenie.text_placeholder"));
		Widgets.button(ui, "screenie.text.add", x + w - 32, y + 1, 32, 16, Component.translatable("pretupid.screenie.add"), Widgets.ButtonStyle.PRIMARY, this::addText);
		int rowY = y + 22;
		for (int i = 0; i < layers.size(); i++) {
			int index = i;
			TextLayer layer = layers.get(i);
			boolean active = i == selected;
			boolean hover = ui.interactive(x, rowY, w - 22, 18, UiContext.Handler.onClick(() -> select(index)));
			Draw.chamfer(g, x, rowY, w - 22, 18, active ? Theme.accent(90) : hover ? Theme.SURFACE_HOVER : Theme.SURFACE_ACTIVE);
			Draw.rect(g, x + 3, rowY + 5, 8, 8, layer.color());
			Draw.text(g, font, Draw.ellipsize(font, Component.literal(displayLayer(index).text()), w - 40), x + 14, rowY + 5, layer.text().isBlank() ? Theme.TEXT_DIM : Theme.TEXT);
			Widgets.button(ui, "screenie.text.remove." + i, x + w - 20, rowY + 1, 20, 16, Component.literal("×"), Widgets.ButtonStyle.DANGER, () -> {
				layers.remove(index);
				select(Math.min(selected, layers.size() - 1));
				dirty = true;
			});
			rowY += 20;
		}
		if (selected < 0 || selected >= layers.size()) {
			return;
		}
		rowY += 4;
		TextLayer layer = layers.get(selected);
		Draw.text(g, font, Component.translatable("pretupid.screenie.size"), x, rowY, Theme.TEXT_MUTED);
		Widgets.slider(ui, "screenie.text.size", x, rowY + 10, w, textSize);
		rowY += 26;
		Draw.text(g, font, Component.translatable("pretupid.screenie.angle"), x, rowY, Theme.TEXT_MUTED);
		Widgets.slider(ui, "screenie.text.angle", x, rowY + 10, w, textAngle);
		rowY += 26;
		int swatch = (w - (TEXT_COLORS.length - 1) * 2) / TEXT_COLORS.length;
		for (int i = 0; i < TEXT_COLORS.length; i++) {
			int color = TEXT_COLORS[i];
			int sx = x + i * (swatch + 2);
			boolean hover = ui.interactive(sx, rowY, swatch, 12, UiContext.Handler.onClick(() -> {
				layers.set(selected, layers.get(selected).withColor(color));
				dirty = true;
			}));
			Draw.rect(g, sx, rowY, swatch, 12, color);
			Draw.border(g, sx, rowY, swatch, 12, layer.color() == color ? Theme.accent() : hover ? 0xFFFFFFFF : 0x60000000);
		}
		rowY += 16;
		Widgets.button(ui, "screenie.text.style", x, rowY, w, 16,
			Component.translatable("pretupid.screenie.style." + layer.style().name().toLowerCase(Locale.ROOT)), Widgets.ButtonStyle.SECONDARY, () -> {
				TextLayer.Style[] styles = TextLayer.Style.values();
				layers.set(selected, layers.get(selected).withStyle(styles[(layer.style().ordinal() + 1) % styles.length]));
				dirty = true;
			});
	}

	private void drawMeme(UiContext ui, int x, int y, int w) {
		var g = ui.g();
		Draw.text(g, font, Component.translatable("pretupid.screenie.templates"), x, y + 2, Theme.TEXT_MUTED);
		MemeTemplate[] templates = {MemeTemplate.CLASSIC, MemeTemplate.CAPTION, MemeTemplate.POSTER};
		int columnW = (w - 2) / 2;
		for (int i = 0; i < templates.length; i++) {
			MemeTemplate entry = templates[i];
			Widgets.button(ui, "screenie.template." + entry, x + (i % 2) * (columnW + 2), y + 14 + (i / 2) * 20, columnW, 18,
				Component.translatable("pretupid.screenie.template." + entry.name().toLowerCase(Locale.ROOT)),
				template == entry ? Widgets.ButtonStyle.PRIMARY : Widgets.ButtonStyle.SECONDARY, () -> {
					if (template != entry) {
						applyTemplate(entry);
					}
				});
		}
		int rowY = y + 14 + ((templates.length + 1) / 2) * 20 + 8;
		List<String> slots = template.slots();
		for (int i = 0; i < slotInputs.size() && i < slots.size(); i++) {
			slotInputs.get(i).draw(ui, x, rowY, w, 18, Component.translatable("pretupid.meme.slot." + slots.get(i)));
			rowY += 22;
		}
		rowY += 4;
		Draw.text(g, font, Component.translatable("pretupid.meme.shot"), x, rowY + 2, Theme.TEXT_MUTED);
		rowY += 14;
		MemeShot[] shots = MemeShot.values();
		for (int i = 0; i < shots.length; i++) {
			MemeShot entry = shots[i];
			Widgets.button(ui, "meme.shot." + entry, x + (i % 2) * (columnW + 2), rowY + (i / 2) * 20, columnW, 18,
				Component.translatable("pretupid.meme.shot." + entry.name().toLowerCase(Locale.ROOT)),
				shot == entry ? Widgets.ButtonStyle.PRIMARY : Widgets.ButtonStyle.SECONDARY, () -> retake(entry));
		}
		rowY += ((shots.length + 1) / 2) * 20 + 4;
		if (selected >= 0 && selected < layers.size()) {
			TextLayer layer = layers.get(selected);
			Draw.text(g, font, Component.translatable("pretupid.screenie.size"), x, rowY, Theme.TEXT_MUTED);
			Widgets.slider(ui, "screenie.text.size", x, rowY + 10, w, textSize);
			rowY += 26;
			Draw.text(g, font, Component.translatable("pretupid.screenie.angle"), x, rowY, Theme.TEXT_MUTED);
			Widgets.slider(ui, "screenie.text.angle", x, rowY + 10, w, textAngle);
			rowY += 26;
			int swatch = (w - (TEXT_COLORS.length - 1) * 2) / TEXT_COLORS.length;
			for (int i = 0; i < TEXT_COLORS.length; i++) {
				int color = TEXT_COLORS[i];
				int sx = x + i * (swatch + 2);
				boolean hover = ui.interactive(sx, rowY, swatch, 12, UiContext.Handler.onClick(() -> {
					layers.set(selected, layers.get(selected).withColor(color));
					dirty = true;
				}));
				Draw.rect(g, sx, rowY, swatch, 12, color);
				Draw.border(g, sx, rowY, swatch, 12, layer.color() == color ? Theme.accent() : hover ? 0xFFFFFFFF : 0x60000000);
			}
			rowY += 16;
		}
		drawFrame(ui, x, rowY + 2, w);
	}

	private void retake(MemeShot next) {
		if (minecraft == null || minecraft.level == null || screenies.busy()) {
			return;
		}
		screenies.requestRetake(next);
		retaking = true;
	}

	private void drawActions(UiContext ui, int y) {
		var g = ui.g();
		int x = width - 8;
		x -= 60;
		Widgets.button(ui, "screenie.close", x, y, 60, 18, Component.translatable("pretupid.ui.done"), Widgets.ButtonStyle.GHOST, this::onClose);
		x -= 84;
		Widgets.button(ui, "screenie.folder", x, y, 80, 18, Component.translatable("pretupid.screenie.open_folder"), Widgets.ButtonStyle.GHOST, () -> {
			try {
				java.nio.file.Files.createDirectories(Screenies.folder());
			} catch (java.io.IOException exception) {
				show(Component.literal(exception.getMessage()), true);
				return;
			}
			PlatformOpener.openFolder(Screenies.folder());
		});
		x -= 94;
		Widgets.button(ui, "screenie.share", x, y, 90, 18, Component.translatable("pretupid.screenie.share"), Widgets.ButtonStyle.SECONDARY, this::openSharePopup);
		x -= 74;
		Widgets.button(ui, "screenie.save", x, y, 70, 18, Component.translatable(working ? "pretupid.screenie.saving" : "pretupid.screenie.save"),
			Widgets.ButtonStyle.PRIMARY, () -> {
				if (working) {
					return;
				}
				working = true;
				screenies.save(exported(), look, List.copyOf(layers), template).whenComplete((path, error) -> minecraft.execute(() -> {
					working = false;
					show(error == null ? Component.translatable("pretupid.screenie.saved", path.getFileName().toString())
						: Component.translatable("pretupid.screenie.save_failed"), error != null);
				}));
			});
		if (status != null && System.currentTimeMillis() - statusAt < 6000L) {
			Draw.text(g, font, Draw.ellipsize(font, status, x - 16), 8, y + 5, statusError ? Theme.DANGER : Theme.TEXT_DIM);
		}
	}

	private void openSharePopup() {
		SocialService social = PretupidClient.get().social();
		if (!PretupidClient.get().session().online()) {
			show(Component.translatable("pretupid.screenie.share_offline"), true);
			return;
		}
		List<SocialService.Friend> friends = social.friends();
		if (friends.isEmpty()) {
			show(Component.translatable("pretupid.screenie.no_friends"), true);
			return;
		}
		openPopup(new Popup() {
			private final ScrollState scroll = new ScrollState();

			@Override
			public void draw(UiContext ui, int screenWidth, int screenHeight) {
				var g = ui.g();
				int w = 200;
				int h = Math.min(220, 40 + friends.size() * 22);
				int x = (screenWidth - w) / 2;
				int y = (screenHeight - h) / 2;
				Draw.shadow(g, x, y, w, h, 4, 1f);
				Draw.glass(g, x, y, w, h, Theme.POPUP, Theme.WINDOW_BORDER);
				ui.hit(x, y, w, h, UiContext.Handler.onClick(() -> { }));
				Draw.textShadow(g, font, Component.translatable("pretupid.screenie.share_title"), x + 8, y + 8, Theme.TEXT);
				int listY = y + 24;
				int listH = h - 30;
				scroll.begin(ui, "screenie.share.list", x + 6, listY, w - 12, listH, friends.size() * 22);
				int rowY = listY - scroll.offset();
				for (SocialService.Friend friend : friends) {
					boolean hover = ui.interactive(x + 6, rowY, w - 18, 20, UiContext.Handler.onClick(() -> share(friend)));
					Draw.chamfer(g, x + 6, rowY, w - 18, 20, hover ? Theme.SURFACE_HOVER : Theme.SURFACE);
					Draw.rect(g, x + 10, rowY + 8, 4, 4, friend.online() ? Theme.SUCCESS : Theme.TEXT_DIM);
					Draw.text(g, font, Draw.ellipsize(font, Component.literal(friend.name()), w - 40), x + 18, rowY + 6, Theme.TEXT);
					rowY += 22;
				}
				scroll.end(ui, x + 6, listY, w - 12, listH);
			}
		});
	}

	private void share(SocialService.Friend friend) {
		closePopup();
		if (working) {
			return;
		}
		working = true;
		show(Component.translatable("pretupid.screenie.sending", friend.name()), false);
		screenies.render(exported(), look, List.copyOf(layers), template, 2560)
			.thenCompose(bytes -> PretupidClient.get().social().sendScreenie(friend.uuid(), bytes))
			.whenComplete((ignored, error) -> minecraft.execute(() -> {
				working = false;
				show(error == null ? Component.translatable("pretupid.screenie.sent_to", friend.name())
					: Component.translatable("pretupid.screenie.send_failed", net.pretupid.client.network.ApiClient.describe(error)), error != null);
			}));
	}
}

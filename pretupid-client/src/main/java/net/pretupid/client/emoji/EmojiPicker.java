package net.pretupid.client.emoji;

import net.pretupid.client.platform.Gfx;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.setting.TextSetting;
import net.pretupid.client.ui.Draw;
import net.pretupid.client.ui.Theme;

public final class EmojiPicker {
	public static final int BUTTON_SIZE = 12;
	private static final int COLUMNS = 9;
	private static final int VISIBLE_ROWS = 6;
	private static final int CELL = 14;
	private static final int PADDING = 4;
	private static final int TAB_HEIGHT = 14;
	private static final int FOOTER_HEIGHT = 12;
	private static final int MAX_SUGGESTIONS = 6;
	private static final int MAX_RECENT = 18;
	private static final String RECENT = "recent";
	private static final float GLYPH_SCALE = 1.25f;

	private final EditBox input;
	private boolean open;
	private String category = RECENT;
	private int scrollRow;
	private List<EmojiIndex.Emoji> suggestions = List.of();
	private int suggestionStart = -1;
	private int selected;
	private String lastQuery = "";

	public EmojiPicker(EditBox input) {
		this.input = input;
		if (recent().isEmpty()) {
			category = EmojiIndex.get().categories().keySet().stream().findFirst().orElse(RECENT);
		}
	}

	public static boolean enabled() {
		PretupidClient client = PretupidClient.getOrNull();
		return client != null && client.settings().chatEmojis.enabled();
	}

	private int buttonX() {
		return input.getX() + input.getWidth() + 2;
	}

	private int buttonY() {
		return input.getY() - 2;
	}

	private int panelWidth() {
		return COLUMNS * CELL + PADDING * 2;
	}

	private int panelHeight() {
		return TAB_HEIGHT + 2 + VISIBLE_ROWS * CELL + FOOTER_HEIGHT + PADDING * 2;
	}

	private int panelX(int screenWidth) {
		return Math.max(2, Math.min(screenWidth - panelWidth() - 2, buttonX() + BUTTON_SIZE - panelWidth()));
	}

	private int panelY() {
		return Math.max(2, buttonY() - panelHeight() - 3);
	}

	private List<String> tabs() {
		List<String> tabs = new ArrayList<>();
		tabs.add(RECENT);
		tabs.addAll(EmojiIndex.get().categories().keySet());
		return tabs;
	}

	private List<EmojiIndex.Emoji> visibleEmojis() {
		if (RECENT.equals(category)) {
			return recent();
		}
		return EmojiIndex.get().categories().getOrDefault(category, List.of());
	}

	private static TextSetting recentSetting() {
		return PretupidClient.get().settings().recentEmojis;
	}

	private static List<EmojiIndex.Emoji> recent() {
		List<EmojiIndex.Emoji> result = new ArrayList<>();
		for (String name : recentSetting().get().split(",")) {
			if (!name.isBlank()) {
				EmojiIndex.get().byName(name.strip()).ifPresent(result::add);
			}
		}
		return result;
	}

	private static void remember(EmojiIndex.Emoji emoji) {
		Set<String> names = new LinkedHashSet<>();
		names.add(emoji.name());
		for (EmojiIndex.Emoji existing : recent()) {
			if (names.size() >= MAX_RECENT) {
				break;
			}
			names.add(existing.name());
		}
		String joined = String.join(",", names);
		while (joined.length() > recentSetting().maxLength() && joined.contains(",")) {
			joined = joined.substring(0, joined.lastIndexOf(','));
		}
		recentSetting().set(joined);
	}

	private void updateSuggestions() {
		String value = input.getValue();
		int cursor = Math.min(input.getCursorPosition(), value.length());
		int colon = value.lastIndexOf(':', cursor - 1);
		String query = "";
		if (colon >= 0 && (colon == 0 || value.charAt(colon - 1) == ' ' || value.charAt(colon - 1) == ':')) {
			String candidate = value.substring(colon + 1, cursor);
			if (candidate.length() >= 2 && candidate.length() <= 40 && candidate.chars().allMatch(c -> Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '+')) {
				query = candidate.toLowerCase(java.util.Locale.ROOT);
			}
		}
		if (query.equals(lastQuery) && (query.isEmpty() || suggestionStart == colon)) {
			return;
		}
		lastQuery = query;
		selected = 0;
		if (query.isEmpty()) {
			suggestions = List.of();
			suggestionStart = -1;
			return;
		}
		suggestions = EmojiIndex.get().search(query, MAX_SUGGESTIONS);
		suggestionStart = suggestions.isEmpty() ? -1 : colon;
	}

	private void accept(EmojiIndex.Emoji emoji) {
		String value = input.getValue();
		int cursor = Math.min(input.getCursorPosition(), value.length());
		String replacement = emoji.shortcode() + " ";
		String next = value.substring(0, suggestionStart) + replacement + value.substring(cursor);
		if (next.length() > 256) {
			return;
		}
		input.setValue(next);
		input.setCursorPosition(suggestionStart + replacement.length());
		remember(emoji);
		suggestions = List.of();
		suggestionStart = -1;
		lastQuery = "";
	}

	private void insert(EmojiIndex.Emoji emoji) {
		String value = input.getValue();
		int cursor = Math.min(input.getCursorPosition(), value.length());
		boolean needsSpace = cursor > 0 && value.charAt(cursor - 1) != ' ';
		String text = (needsSpace ? " " : "") + emoji.shortcode() + " ";
		if (value.length() + text.length() > 256) {
			return;
		}
		input.insertText(text);
		remember(emoji);
	}

	public void render(Gfx g, Font font, int mouseX, int mouseY, int screenWidth) {
		updateSuggestions();
		int bx = buttonX();
		int by = buttonY();
		boolean hoverButton = inside(mouseX, mouseY, bx, by, BUTTON_SIZE, BUTTON_SIZE);
		Draw.rect(g, bx, by, BUTTON_SIZE, BUTTON_SIZE, open || hoverButton ? 0xC0303848 : 0x80000000);
		drawGlyph(g, font, EmojiIndex.get().byName(open ? "grinning" : "slightly_smiling_face").orElse(null), bx + 1, by + 1, 1.1f);
		if (open) {
			renderPanel(g, font, mouseX, mouseY, screenWidth);
		} else if (hoverButton) {
			drawTooltip(g, font, Component.translatable("pretupid.emoji.button"), mouseX, mouseY - 14, screenWidth);
		}
		if (!suggestions.isEmpty()) {
			renderSuggestions(g, font, mouseX, mouseY);
		}
	}

	private void renderSuggestions(Gfx g, Font font, int mouseX, int mouseY) {
		int width = 0;
		for (EmojiIndex.Emoji emoji : suggestions) {
			width = Math.max(width, font.width(emoji.shortcode()) + 20);
		}
		int x = Math.max(2, input.getScreenX(Math.max(0, suggestionStart)) - 2);
		int rowHeight = 12;
		int y = input.getY() - 4 - suggestions.size() * rowHeight;
		Draw.rect(g, x - 1, y - 1, width + 2, suggestions.size() * rowHeight + 2, 0xF0101318);
		for (int i = 0; i < suggestions.size(); i++) {
			EmojiIndex.Emoji emoji = suggestions.get(i);
			int rowY = y + i * rowHeight;
			if (inside(mouseX, mouseY, x, rowY, width, rowHeight)) {
				selected = i;
			}
			if (i == selected) {
				Draw.rect(g, x, rowY, width, rowHeight, 0xFF2A3342);
			}
			drawGlyph(g, font, emoji, x + 2, rowY + 1, 1f);
			Draw.text(g, font, emoji.shortcode(), x + 16, rowY + 2, i == selected ? Theme.accentText() : 0xFFDDE3EC);
		}
	}

	private void renderPanel(Gfx g, Font font, int mouseX, int mouseY, int screenWidth) {
		int px = panelX(screenWidth);
		int py = panelY();
		int pw = panelWidth();
		int ph = panelHeight();
		Draw.rect(g, px, py, pw, ph, 0xF0101318);
		Draw.border(g, px, py, pw, ph, 0xFF2A3342);
		List<String> tabs = tabs();
		int tabWidth = (pw - PADDING * 2) / tabs.size();
		for (int i = 0; i < tabs.size(); i++) {
			String tab = tabs.get(i);
			int tx = px + PADDING + i * tabWidth;
			int ty = py + PADDING;
			boolean active = tab.equals(category);
			boolean hover = inside(mouseX, mouseY, tx, ty, tabWidth, TAB_HEIGHT);
			if (active || hover) {
				Draw.rect(g, tx, ty, tabWidth, TAB_HEIGHT, active ? Theme.accent(90) : 0x40FFFFFF);
			}
			drawGlyph(g, font, tabIcon(tab), tx + (tabWidth - 10) / 2, ty + 2, 1f);
		}
		List<EmojiIndex.Emoji> emojis = visibleEmojis();
		int rows = (emojis.size() + COLUMNS - 1) / COLUMNS;
		scrollRow = Math.max(0, Math.min(scrollRow, Math.max(0, rows - VISIBLE_ROWS)));
		int gridY = py + PADDING + TAB_HEIGHT + 2;
		EmojiIndex.Emoji hovered = null;
		for (int row = 0; row < VISIBLE_ROWS; row++) {
			for (int column = 0; column < COLUMNS; column++) {
				int index = (scrollRow + row) * COLUMNS + column;
				if (index >= emojis.size()) {
					break;
				}
				EmojiIndex.Emoji emoji = emojis.get(index);
				int cx = px + PADDING + column * CELL;
				int cy = gridY + row * CELL;
				if (inside(mouseX, mouseY, cx, cy, CELL, CELL)) {
					hovered = emoji;
					Draw.rect(g, cx, cy, CELL, CELL, 0x50FFFFFF);
				}
				drawGlyph(g, font, emoji, cx + 1, cy + 1, GLYPH_SCALE);
			}
		}
		if (emojis.isEmpty()) {
			Draw.centered(g, font, Component.translatable("pretupid.emoji.no_recent"), px + pw / 2, gridY + VISIBLE_ROWS * CELL / 2 - 4, Theme.TEXT_DIM);
		}
		if (rows > VISIBLE_ROWS) {
			int trackHeight = VISIBLE_ROWS * CELL;
			int thumb = Math.max(8, trackHeight * VISIBLE_ROWS / rows);
			int thumbY = gridY + (trackHeight - thumb) * scrollRow / Math.max(1, rows - VISIBLE_ROWS);
			Draw.rect(g, px + pw - 3, thumbY, 2, thumb, 0x80FFFFFF);
		}
		int footerY = py + ph - PADDING - FOOTER_HEIGHT + 2;
		Component footer = hovered != null ? Component.literal(hovered.shortcode()) : Component.translatable(categoryKey(category));
		Draw.text(g, font, Draw.ellipsize(font, footer, pw - PADDING * 2), px + PADDING, footerY, hovered != null ? 0xFFFFFFFF : Theme.TEXT_DIM);
	}

	private static String categoryKey(String category) {
		return "pretupid.emoji.category." + category.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z]+", "_").replaceAll("_+$", "");
	}

	private EmojiIndex.Emoji tabIcon(String tab) {
		if (RECENT.equals(tab)) {
			return EmojiIndex.get().byName("clock3").orElse(null);
		}
		List<EmojiIndex.Emoji> list = EmojiIndex.get().categories().getOrDefault(tab, List.of());
		return list.isEmpty() ? null : list.getFirst();
	}

	private static void drawGlyph(Gfx g, Font font, EmojiIndex.Emoji emoji, int x, int y, float scale) {
		if (emoji != null) {
			Draw.scaled(g, font, EmojiText.glyph(emoji), x, y, scale, 0xFFFFFFFF, false);
		}
	}

	private static void drawTooltip(Gfx g, Font font, Component text, int x, int y, int screenWidth) {
		int width = font.width(text) + 6;
		int left = Math.max(2, Math.min(screenWidth - width - 2, x - width / 2));
		Draw.rect(g, left, y, width, 11, 0xF0101318);
		Draw.text(g, font, text, left + 3, y + 2, 0xFFFFFFFF);
	}

	public boolean mouseClicked(double mouseX, double mouseY, int button, int screenWidth) {
		if (button != InputConstants.MOUSE_BUTTON_LEFT) {
			return open && insidePanel(mouseX, mouseY, screenWidth);
		}
		if (!suggestions.isEmpty()) {
			int rowHeight = 12;
			int x = Math.max(2, input.getScreenX(Math.max(0, suggestionStart)) - 2);
			int y = input.getY() - 4 - suggestions.size() * rowHeight;
			for (int i = 0; i < suggestions.size(); i++) {
				if (inside(mouseX, mouseY, x, y + i * rowHeight, 160, rowHeight)) {
					accept(suggestions.get(i));
					return true;
				}
			}
		}
		if (inside(mouseX, mouseY, buttonX(), buttonY(), BUTTON_SIZE, BUTTON_SIZE)) {
			open = !open;
			return true;
		}
		if (!open) {
			return false;
		}
		if (!insidePanel(mouseX, mouseY, screenWidth)) {
			open = false;
			return false;
		}
		int px = panelX(screenWidth);
		int py = panelY();
		List<String> tabs = tabs();
		int tabWidth = (panelWidth() - PADDING * 2) / tabs.size();
		for (int i = 0; i < tabs.size(); i++) {
			if (inside(mouseX, mouseY, px + PADDING + i * tabWidth, py + PADDING, tabWidth, TAB_HEIGHT)) {
				category = tabs.get(i);
				scrollRow = 0;
				return true;
			}
		}
		int gridY = py + PADDING + TAB_HEIGHT + 2;
		List<EmojiIndex.Emoji> emojis = visibleEmojis();
		int column = (int) Math.floor((mouseX - px - PADDING) / CELL);
		int row = (int) Math.floor((mouseY - gridY) / CELL);
		if (column >= 0 && column < COLUMNS && row >= 0 && row < VISIBLE_ROWS) {
			int index = (scrollRow + row) * COLUMNS + column;
			if (index < emojis.size()) {
				insert(emojis.get(index));
			}
		}
		return true;
	}

	private boolean insidePanel(double mouseX, double mouseY, int screenWidth) {
		return inside(mouseX, mouseY, panelX(screenWidth), panelY(), panelWidth(), panelHeight());
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double amount, int screenWidth) {
		if (!open || !insidePanel(mouseX, mouseY, screenWidth)) {
			return false;
		}
		scrollRow -= (int) Math.signum(amount);
		return true;
	}

	public boolean keyPressed(int key) {
		if (!suggestions.isEmpty()) {
			if (key == InputConstants.KEY_TAB) {
				accept(suggestions.get(Math.min(selected, suggestions.size() - 1)));
				return true;
			}
			if (key == InputConstants.KEY_UP) {
				selected = (selected - 1 + suggestions.size()) % suggestions.size();
				return true;
			}
			if (key == InputConstants.KEY_DOWN) {
				selected = (selected + 1) % suggestions.size();
				return true;
			}
		}
		if (open && key == InputConstants.KEY_ESCAPE) {
			open = false;
			return true;
		}
		return false;
	}

	private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
		return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
	}
}

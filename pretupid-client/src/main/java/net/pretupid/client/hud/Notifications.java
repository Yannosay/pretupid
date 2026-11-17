package net.pretupid.client.hud;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.module.Module;
import net.pretupid.client.ui.Draw;
import net.pretupid.client.ui.Easing;
import net.pretupid.client.ui.Icon;
import net.pretupid.client.ui.Theme;
import net.pretupid.client.util.Colors;

public final class Notifications {
	private static final int WIDTH = 150;
	private static final int HEIGHT = 30;
	private static final int MAX_VISIBLE = 4;
	private static final long ENTER_MS = 260L;
	private static final long EXIT_MS = 320L;
	private static final long STACK_LINGER_MS = 1600L;

	private final List<Entry> entries = new ArrayList<>();

	public void push(Component title, Component body, Icon icon, ItemStack stack, int color, long durationMs) {
		Minecraft.getInstance().execute(() -> {
			long now = System.currentTimeMillis();
			entries.removeIf(entry -> entry.title.getString().equals(title.getString()) && entry.body.getString().equals(body.getString()));
			int newest = entries.size() - 1;
			for (int i = Math.max(0, entries.size() - (MAX_VISIBLE - 1)); i <= newest; i++) {
				Entry entry = entries.get(i);
				long age = now - entry.createdAt;
				if (age < entry.duration) {
					entries.set(i, entry.lingering(age + STACK_LINGER_MS));
				}
			}
			entries.add(new Entry(title, body, icon, stack, color, now, durationMs));
			while (entries.size() > 8) {
				entries.removeFirst();
			}
		});
	}

	public void info(Component title, Component body, Icon icon) {
		push(title, body, icon, null, Theme.accent(), 2600L);
	}

	public void warning(Component title, Component body, ItemStack stack) {
		push(title, body, null, stack, Theme.WARNING, 4200L);
	}

	public void moduleToggled(Module module) {
		if (!PretupidClient.get().settings().toggleToasts.enabled()) {
			return;
		}
		boolean enabled = module.isEnabled();
		push(module.name(),
			Component.translatable(enabled ? "pretupid.notification.enabled" : "pretupid.notification.disabled"),
			module.icon(),
			null,
			enabled ? Theme.SUCCESS : Theme.DANGER,
			2400L);
	}

	public void profileSwitched(String profile) {
		info(Component.translatable("pretupid.notification.profile"), Component.literal(profile), Icon.item("writable_book"));
	}

	public void render(GuiGraphicsExtractor graphics, Minecraft minecraft) {
		if (entries.isEmpty()) {
			return;
		}
		long now = System.currentTimeMillis();
		Font font = minecraft.font;
		int screenWidth = graphics.guiWidth();
		float y = 6f;
		int visible = 0;
		Iterator<Entry> iterator = entries.iterator();
		List<Entry> active = new ArrayList<>();
		while (iterator.hasNext()) {
			Entry entry = iterator.next();
			if (now - entry.createdAt > entry.duration + EXIT_MS) {
				iterator.remove();
			} else {
				active.add(entry);
			}
		}
		for (int i = active.size() - 1; i >= 0 && visible < MAX_VISIBLE; i--, visible++) {
			Entry entry = active.get(i);
			long age = now - entry.createdAt;
			float enter = Easing.outBack(Math.min(1f, age / (float) ENTER_MS));
			float exit = age > entry.duration ? Easing.inOutCubic((age - entry.duration) / (float) EXIT_MS) : 0f;
			float slide = (1f - enter) + exit;
			float x = screenWidth - WIDTH - 6 + slide * (WIDTH + 12);
			float alpha = Math.clamp(1f - exit, 0f, 1f);
			graphics.pose().pushMatrix();
			graphics.pose().translate(x, y);
			drawEntry(graphics, font, entry, alpha, Math.min(1f, age / (float) entry.duration));
			graphics.pose().popMatrix();
			y += (HEIGHT + 4) * (1f - exit);
		}
	}

	private void drawEntry(GuiGraphicsExtractor graphics, Font font, Entry entry, float alpha, float progress) {
		Draw.shadow(graphics, 0, 0, WIDTH, HEIGHT, 3, alpha);
		Draw.glass(graphics, 0, 0, WIDTH, HEIGHT, Colors.multiplyAlpha(Theme.WINDOW, alpha), Colors.multiplyAlpha(Theme.WINDOW_BORDER, alpha));
		Draw.rect(graphics, 1, 2, 2, HEIGHT - 4, Colors.multiplyAlpha(entry.color, alpha));
		boolean hasIcon = entry.icon != null || (entry.stack != null && !entry.stack.isEmpty());
		if (entry.stack != null && !entry.stack.isEmpty()) {
			graphics.item(entry.stack, 7, 7);
		} else if (entry.icon != null) {
			entry.icon.draw(graphics, 7, 7, Colors.withAlpha(0xFFFFFF, Math.round(alpha * 255)));
		}
		int textX = hasIcon ? 28 : 8;
		Draw.text(graphics, font, Draw.ellipsize(font, entry.title, WIDTH - textX - 6), textX, 6, Colors.multiplyAlpha(Theme.TEXT, alpha));
		Draw.text(graphics, font, Draw.ellipsize(font, entry.body, WIDTH - textX - 6), textX, 17, Colors.multiplyAlpha(Colors.withAlpha(entry.color, 255), alpha));
		int barWidth = Math.round((WIDTH - 4) * (1f - Math.min(1f, progress)));
		Draw.rect(graphics, 2, HEIGHT - 2, barWidth, 1, Colors.multiplyAlpha(Colors.withAlpha(entry.color, 140), alpha));
	}

	private record Entry(Component title, Component body, Icon icon, ItemStack stack, int color, long createdAt, long duration) {
		Entry lingering(long minimumDuration) {
			return minimumDuration <= duration ? this : new Entry(title, body, icon, stack, color, createdAt, minimumDuration);
		}
	}
}

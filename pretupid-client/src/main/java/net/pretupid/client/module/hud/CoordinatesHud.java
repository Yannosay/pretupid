package net.pretupid.client.module.hud;

import net.pretupid.client.platform.Gfx;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.pretupid.client.hud.HudModule;
import net.pretupid.client.hud.HudPosition;
import net.pretupid.client.setting.BooleanSetting;
import net.pretupid.client.setting.ColorSetting;
import net.pretupid.client.setting.EnumSetting;
import net.pretupid.client.setting.NumberSetting;

public final class CoordinatesHud extends HudModule {
	public enum CoordinateLayout {
		LINES,
		COMPACT
	}

	private final EnumSetting<CoordinateLayout> layout = add(new EnumSetting<>("layout", CoordinateLayout.LINES));
	private final NumberSetting decimals = add(new NumberSetting("decimals", 0, 0, 2, 1));
	private final BooleanSetting showDirection = add(new BooleanSetting("show_direction", true));
	private final BooleanSetting showBiome = add(new BooleanSetting("show_biome", true));
	private final BooleanSetting showOtherDimension = add(new BooleanSetting("show_other_dimension", true));
	private final ColorSetting labelColor = shared(new ColorSetting("label_color", 0xFF8FC4FF).followingModuleText());
	private final ColorSetting valueColor = shared(new ColorSetting("value_color", 0xFFFFFFFF));

	public CoordinatesHud() {
		super("coordinates", "compass_00", false, new HudPosition(HudPosition.Anchor.START, 4, HudPosition.Anchor.START, 58));
	}

	private List<String[]> lines(Minecraft minecraft) {
		List<String[]> lines = new ArrayList<>();
		LocalPlayer player = minecraft.player;
		double x = player == null ? 128.5 : player.getX();
		double y = player == null ? 64.0 : player.getY();
		double z = player == null ? -256.5 : player.getZ();
		if (layout.get() == CoordinateLayout.COMPACT) {
			lines.add(new String[] {I18n.get("pretupid.hud.coords.xyz"), format(x) + ", " + format(y) + ", " + format(z)});
		} else {
			lines.add(new String[] {"X", format(x)});
			lines.add(new String[] {"Y", format(y)});
			lines.add(new String[] {"Z", format(z)});
		}
		if (showDirection.enabled()) {
			Direction direction = player == null ? Direction.NORTH : player.getDirection();
			String axis = switch (direction) {
				case NORTH -> "-Z";
				case SOUTH -> "+Z";
				case EAST -> "+X";
				case WEST -> "-X";
				default -> "";
			};
			lines.add(new String[] {I18n.get("pretupid.hud.coords.facing"), I18n.get("pretupid.direction." + direction.getSerializedName()) + " (" + axis + ")"});
		}
		if (showBiome.enabled() && player != null && minecraft.level != null) {
			BlockPos pos = player.blockPosition();
			String biome = minecraft.level.getBiome(pos).unwrapKey()
				.map(key -> I18n.get("biome." + key.identifier().getNamespace() + "." + key.identifier().getPath()))
				.orElse("?");
			lines.add(new String[] {I18n.get("pretupid.hud.coords.biome"), biome});
		}
		if (showOtherDimension.enabled() && player != null && minecraft.level != null) {
			if (minecraft.level.dimension() == Level.OVERWORLD) {
				lines.add(new String[] {I18n.get("pretupid.hud.coords.nether"), format(x / 8.0) + ", " + format(z / 8.0)});
			} else if (minecraft.level.dimension() == Level.NETHER) {
				lines.add(new String[] {I18n.get("pretupid.hud.coords.overworld"), format(x * 8.0) + ", " + format(z * 8.0)});
			}
		}
		return lines;
	}

	private String format(double value) {
		int places = decimals.intValue();
		if (places == 0) {
			return Long.toString((long) Math.floor(value));
		}
		return String.format(Locale.ROOT, "%." + places + "f", value);
	}

	@Override
	public int width(Font font, Minecraft minecraft, boolean preview) {
		int max = 0;
		for (String[] line : lines(minecraft)) {
			max = Math.max(max, font.width(line[0] + ": ") + font.width(line[1]));
		}
		return max + padding() * 2;
	}

	@Override
	public int height(Font font, Minecraft minecraft, boolean preview) {
		return lines(minecraft).size() * (font.lineHeight + 1) - 1 + padding() * 2;
	}

	@Override
	public void render(Gfx graphics, Font font, Minecraft minecraft, boolean preview) {
		List<String[]> lines = lines(minecraft);
		int width = width(font, minecraft, preview);
		int height = lines.size() * (font.lineHeight + 1) - 1 + padding() * 2;
		drawBackground(graphics, width, height);
		int y = padding();
		boolean shadow = textShadow.enabled();
		int index = 0;
		for (String[] line : lines) {
			String label = line[0] + ": ";
			graphics.text(font, label, padding(), y, labelColor.resolve(index * 0.08f), shadow);
			graphics.text(font, line[1], padding() + font.width(label), y, valueColor.resolve(index * 0.08f + 0.04f), shadow);
			y += font.lineHeight + 1;
			index++;
		}
	}
}

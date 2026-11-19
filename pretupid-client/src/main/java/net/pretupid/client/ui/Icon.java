package net.pretupid.client.ui;

import net.pretupid.client.platform.Gfx;
import net.minecraft.resources.Identifier;

public record Icon(Identifier texture) {
	public static Icon item(String name) {
		return new Icon(Identifier.withDefaultNamespace("textures/item/" + name + ".png"));
	}

	public void draw(Gfx graphics, int x, int y) {
		graphics.blit(texture, x, y, 0f, 0f, 16, 16, 16, 16);
	}

	public void draw(Gfx graphics, int x, int y, int tint) {
		graphics.blit(texture, x, y, 0f, 0f, 16, 16, 16, 16, tint);
	}
}

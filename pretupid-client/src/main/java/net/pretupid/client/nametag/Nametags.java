package net.pretupid.client.nametag;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.network.RemoteProfile;

public final class Nametags {
	private final PretupidClient client;

	public Nametags(PretupidClient client) {
		this.client = client;
	}

	public NametagStyle ownStyle() {
		return client.settings().nametagStyle();
	}

	public boolean isSelf(UUID uuid) {
		return uuid != null && uuid.equals(Minecraft.getInstance().getUser().getProfileId());
	}

	public Optional<NametagStyle> styleFor(UUID uuid) {
		if (uuid == null) {
			return Optional.empty();
		}
		if (isSelf(uuid)) {
			return Optional.of(ownStyle());
		}
		if (!client.settings().showOtherBadges.enabled()) {
			return Optional.empty();
		}
		return client.presence().profileOf(uuid).map(RemoteProfile::style);
	}

	public boolean isPretupidUser(UUID uuid) {
		return isSelf(uuid) || client.presence().profileOf(uuid).isPresent();
	}

	public Component decorateNametag(UUID uuid, String name, Component original) {
		if (original == null) {
			return null;
		}
		Optional<NametagStyle> style = styleFor(uuid);
		if (style.isEmpty()) {
			return original;
		}
		return NametagFormatter.format(original, name, style.get(), true, true);
	}

	public Component decorateTabName(UUID uuid, String name, Component original) {
		if (original == null || !client.settings().tabListBadges.enabled()) {
			return original;
		}
		Optional<NametagStyle> style = styleFor(uuid);
		if (style.isEmpty() || !style.get().badge()) {
			return original;
		}
		return NametagFormatter.format(original, name, style.get(), true, false);
	}
}

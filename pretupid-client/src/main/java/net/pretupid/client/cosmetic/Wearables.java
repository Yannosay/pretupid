package net.pretupid.client.cosmetic;

import java.util.regex.Pattern;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.player.Player;
import net.pretupid.client.PretupidClient;

public final class Wearables {
	private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9_-]{0,31}");

	public record Loadout(String hat, String back, String pet) {
		public static final Loadout NONE = new Loadout(null, null, null);

		public boolean empty() {
			return hat == null && back == null && pet == null;
		}
	}

	private Wearables() {
	}

	public static String validId(String id) {
		return id != null && ID.matcher(id).matches() ? id : null;
	}

	public static Loadout own() {
		var settings = PretupidClient.get().settings();
		return new Loadout(validId(settings.hat.get()), validId(settings.back.get()), validId(settings.pet.get()));
	}

	public static void apply(Player player, AvatarRenderState state, Loadout loadout) {
		if (loadout == null || loadout.empty() || player.isInvisible() || !(state instanceof CosmeticStateHolder holder)) {
			return;
		}
		boolean headFree = state.headEquipment.isEmpty() && state.headItem.isEmpty() && state.wornHeadType == null;
		holder.pretupid$setCosmetics(headFree ? loadout.hat() : null, loadout.back(), loadout.pet());
	}
}

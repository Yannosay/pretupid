package net.pretupid.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import java.net.SocketAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;
import net.pretupid.client.PretupidClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerList.class)
public abstract class PlayerListMixin {
	@Inject(method = "canPlayerLogin", at = @At("HEAD"), cancellable = true)
	private void pretupid$friendsOnly(CallbackInfoReturnable<Component> cir, @Local(argsOnly = true) NameAndId player) {
		PretupidClient client = PretupidClient.getOrNull();
		if (client != null && !client.hosting().mayJoin(player.id())) {
			cir.setReturnValue(Component.translatable("pretupid.friends.host_rejected"));
		}
	}
}

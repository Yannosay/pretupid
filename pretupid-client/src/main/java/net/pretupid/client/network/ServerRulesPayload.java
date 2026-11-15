package net.pretupid.client.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.pretupid.client.PretupidClient;

public record ServerRulesPayload(String json) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<ServerRulesPayload> TYPE = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(PretupidClient.MOD_ID, "rules"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ServerRulesPayload> CODEC = StreamCodec.composite(ByteBufCodecs.stringUtf8(8192), ServerRulesPayload::json, ServerRulesPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

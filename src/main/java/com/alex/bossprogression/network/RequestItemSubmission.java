package com.alex.bossprogression.network;

import com.alex.bossprogression.boss.BossProgressManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public record RequestItemSubmission(ResourceLocation bossId) implements CustomPacketPayload {
    public static final Type<RequestItemSubmission> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("bossprogression", "submit_items"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestItemSubmission> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, RequestItemSubmission::bossId, RequestItemSubmission::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, STREAM_CODEC, (packet, context) -> {
            if (context.player() instanceof ServerPlayer player) BossProgressManager.submitItems(player, packet.bossId());
        });
    }
}

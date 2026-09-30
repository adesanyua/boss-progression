package com.alex.bossprogression.network;

import com.alex.bossprogression.BossProgressionMod;
import com.alex.bossprogression.boss.BossProgressManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Only the boss id crosses the trust boundary; owner and cycle are resolved server-side. */
public record RequestBossReset(ResourceLocation bossId) implements CustomPacketPayload {
    public static final Type<RequestBossReset> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(BossProgressionMod.MODID, "request_boss_reset"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestBossReset> STREAM_CODEC =
            StreamCodec.composite(ResourceLocation.STREAM_CODEC, RequestBossReset::bossId, RequestBossReset::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, STREAM_CODEC, (packet, context) -> {
            if (context.player() instanceof ServerPlayer player) BossProgressManager.requestReset(player, packet.bossId());
        });
    }
}

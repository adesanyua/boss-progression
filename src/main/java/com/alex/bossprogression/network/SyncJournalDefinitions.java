package com.alex.bossprogression.network;

import com.alex.bossprogression.BossProgressionMod;
import com.alex.bossprogression.boss.BossDefinition;
import com.alex.bossprogression.boss.BossMobConfig;
import com.alex.bossprogression.boss.BossRegistry;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Server-to-client only. A begin/entry/end batch atomically replaces the Journal cache. */
public record SyncJournalDefinitions(int action, Optional<BossDefinition> definition) implements CustomPacketPayload {
    public static final Type<SyncJournalDefinitions> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(BossProgressionMod.MODID, "sync_journal_definitions"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncJournalDefinitions> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SyncJournalDefinitions::action,
            ByteBufCodecs.optional(ByteBufCodecs.fromCodecWithRegistries(BossDefinition.CODEC)), SyncJournalDefinitions::definition,
            SyncJournalDefinitions::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, STREAM_CODEC, (packet, context) -> packet.applyClient());
    }
    public void applyClient() {
        switch (action) {
            case 0 -> BossRegistry.beginClientSync();
            case 1 -> definition.ifPresent(BossRegistry::addClientSync);
            case 2 -> BossRegistry.finishClientSync();
            default -> BossProgressionMod.LOGGER.warn("Ignoring unknown Journal sync action {}", action);
        }
    }
    public static BossDefinition journalView(BossDefinition boss) {
        // Journal needs names/conditions/rewards, not arbitrary entity NBT or server placement settings.
        return new BossDefinition(boss.id(), boss.displayName(), boss.bossEntity(), boss.conditions(), null,
                boss.repeatable(), false, boss.rewards(), BossMobConfig.DEFAULT);
    }
    public static void sendTo(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new SyncJournalDefinitions(0, Optional.empty()));
        for (var boss : BossRegistry.all()) PacketDistributor.sendToPlayer(player,
                new SyncJournalDefinitions(1, Optional.of(journalView(boss))));
        PacketDistributor.sendToPlayer(player, new SyncJournalDefinitions(2, Optional.empty()));
    }
}

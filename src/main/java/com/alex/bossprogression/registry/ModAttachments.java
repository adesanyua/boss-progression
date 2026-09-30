package com.alex.bossprogression.registry;

import com.alex.bossprogression.BossProgressionMod;
import com.alex.bossprogression.boss.BossProgressData;
import com.alex.bossprogression.dungeon.DungeonInstanceStore;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Registers the persistent player-data attachment that stores boss progression.
 *
 * <p>Uses the NeoForge 1.21.1 attachments API: a codec-serialized {@link AttachmentType} registered
 * to {@link NeoForgeRegistries.Keys#ATTACHMENT_TYPES}. {@code copyOnDeath()} is required so the data
 * survives player death/respawn (entity attachments are otherwise dropped on death by default).
 */
public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, BossProgressionMod.MODID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<BossProgressData>> BOSS_PROGRESS =
            ATTACHMENT_TYPES.register("boss_progress",
                    () -> AttachmentType.builder(() -> new BossProgressData())
                            .serialize(BossProgressData.CODEC)
                            .copyOnDeath()
                            // Sync to the owning client only (the journal GUI). Server stays authoritative;
                            // other players never receive someone else's progression (Stage 11 ownership).
                            .sync((holder, to) -> holder == to, BossProgressData.STREAM_CODEC)
                            .build());

    private ModAttachments() {
    }

    /**
     * Per-level store of generated {@link com.alex.bossprogression.dungeon.DungeonInstance}s.
     * Applied to {@link net.minecraft.server.level.ServerLevel}; persisted with the world, per-dimension,
     * never synced to clients (dungeon coordinates reach players through their own progress data).
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<DungeonInstanceStore>> DUNGEON_STORE =
            ATTACHMENT_TYPES.register("dungeon_store",
                    () -> AttachmentType.builder(() -> new DungeonInstanceStore())
                            .serialize(DungeonInstanceStore.CODEC)
                            .build());

    public static void register(IEventBus modEventBus) {
        ATTACHMENT_TYPES.register(modEventBus);
    }
}

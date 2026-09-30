package com.alex.bossprogression.dungeon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;

/**
 * Serializable collection of {@link DungeonInstance}s stored on a {@link net.minecraft.server.level.ServerLevel}
 * via a NeoForge level data attachment (see {@code ModAttachments.DUNGEON_STORE}). Because the
 * attachment lives on the level, it is saved with the world and is naturally per-dimension.
 */
public final class DungeonInstanceStore {
    public static final Codec<DungeonInstanceStore> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.unboundedMap(Codec.STRING, DungeonInstance.CODEC)
                    .fieldOf("instances").orElse(Map.of())
                    .forGetter(DungeonInstanceStore::instances)
    ).apply(inst, DungeonInstanceStore::new));

    private final Map<String, DungeonInstance> instances;

    public DungeonInstanceStore(Map<String, DungeonInstance> instances) {
        this.instances = new HashMap<>(instances);
    }

    public DungeonInstanceStore() {
        this(Map.of());
    }

    public Map<String, DungeonInstance> instances() {
        return instances;
    }

    public void put(DungeonInstance instance) {
        instances.put(instance.key(), instance);
    }

    public DungeonInstance at(BlockPos pos) {
        return instances.get(keyOf(pos));
    }

    public DungeonInstance byBossUuid(UUID bossUuid) {
        for (DungeonInstance instance : instances.values()) {
            if (bossUuid.equals(instance.bossUuid())) {
                return instance;
            }
        }
        return null;
    }

    private static String keyOf(BlockPos pos) {
        return pos.getX() + "_" + pos.getY() + "_" + pos.getZ();
    }
}

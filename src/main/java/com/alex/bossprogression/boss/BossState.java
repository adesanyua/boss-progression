package com.alex.bossprogression.boss;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import com.alex.bossprogression.dungeon.DungeonInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Per-player, per-boss mutable progress state. Serialized inside {@link BossProgressData}
 * (a persistent player data attachment), so every field must round-trip through {@link #CODEC}.
 *
 * <p>Version-friendly by construction: all non-legacy fields are optional with defaults, so saves
 * written before a field existed still load. New fields added in later MVPs (invitation) follow the
 * same {@code optionalFieldOf} pattern.
 */
public final class BossState {
    public static final Codec<BossState> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.unboundedMap(Codec.STRING, Codec.INT)
                    .fieldOf("condition_progress").orElse(Map.of())
                    .forGetter(BossState::conditionProgress),
            Codec.BOOL.fieldOf("unlocked").orElse(false).forGetter(BossState::unlocked),
            Codec.BOOL.fieldOf("defeated").orElse(false).forGetter(BossState::defeated),
            Codec.BOOL.fieldOf("dungeon_generated").orElse(false).forGetter(BossState::dungeonGenerated),
            ResourceKey.codec(Registries.DIMENSION)
                    .optionalFieldOf("dungeon_dimension")
                    .forGetter(s -> Optional.ofNullable(s.dungeonDimension)),
            BlockPos.CODEC
                    .optionalFieldOf("dungeon_pos")
                    .forGetter(s -> Optional.ofNullable(s.dungeonPos)),
            DungeonInstance.UUID_CODEC.optionalFieldOf("encounter_id").forGetter(s -> Optional.ofNullable(s.encounterId)),
            Codec.BOOL.optionalFieldOf("reward_granted", false).forGetter(BossState::rewardGranted)
    ).apply(inst, BossState::new));

    private final Map<String, Integer> conditionProgress;
    private boolean unlocked;
    private boolean defeated;
    private boolean dungeonGenerated;
    private ResourceKey<Level> dungeonDimension;
    private BlockPos dungeonPos;
    private UUID encounterId;
    private boolean rewardGranted;

    public BossState(Map<String, Integer> conditionProgress, boolean unlocked, boolean defeated,
                     boolean dungeonGenerated,
                     Optional<ResourceKey<Level>> dungeonDimension,
                     Optional<BlockPos> dungeonPos, Optional<UUID> encounterId, boolean rewardGranted) {
        this.conditionProgress = new HashMap<>(conditionProgress);
        this.unlocked = unlocked;
        this.defeated = defeated;
        this.dungeonGenerated = dungeonGenerated;
        this.dungeonDimension = dungeonDimension.orElse(null);
        this.dungeonPos = dungeonPos.orElse(null);
        this.encounterId = encounterId.orElse(null);
        this.rewardGranted = rewardGranted;
    }

    public BossState() {
        this(Map.of(), false, false, false, Optional.empty(), Optional.empty(), Optional.empty(), false);
    }

    // --- codec accessors ---
    public Map<String, Integer> conditionProgress() {
        return conditionProgress;
    }

    public boolean unlocked() {
        return unlocked;
    }

    public boolean defeated() {
        return defeated;
    }

    public boolean dungeonGenerated() {
        return dungeonGenerated;
    }

    /** Dimension the dungeon was generated in, or {@code null} if not generated yet. */
    public ResourceKey<Level> dungeonDimension() {
        return dungeonDimension;
    }

    /** Dungeon origin position, or {@code null} if not generated yet. */
    public BlockPos dungeonPos() {
        return dungeonPos;
    }

    public UUID encounterId() { return encounterId; }
    public boolean rewardGranted() { return rewardGranted; }
    void setRewardGranted(boolean value) { rewardGranted = value; }
    void setEncounterId(UUID id) { encounterId = id; }

    // --- progress accessors ---
    public int progress(String conditionId) {
        return conditionProgress.getOrDefault(conditionId, 0);
    }

    void setProgress(String conditionId, int value) {
        conditionProgress.put(conditionId, value);
    }

    void setUnlocked(boolean unlocked) {
        this.unlocked = unlocked;
    }

    void setDefeated(boolean defeated) {
        this.defeated = defeated;
    }

    /** Records a successfully generated dungeon. Only called after generation actually succeeded. */
    void setDungeon(ResourceKey<Level> dimension, BlockPos pos) {
        this.dungeonGenerated = true;
        this.dungeonDimension = dimension;
        this.dungeonPos = pos;
    }
}

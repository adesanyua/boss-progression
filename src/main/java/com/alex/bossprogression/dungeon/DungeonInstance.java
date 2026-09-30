package com.alex.bossprogression.dungeon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * A physically generated boss dungeon in the shared world, plus its lifecycle flags.
 *
 * <p>Ownership (Stage 11): each instance records the {@code owner} that unlocked it. The physical
 * structure is shared, but progression is only advanced for the owner; other players merely finding
 * the dungeon does not complete their own encounter. The instance is persisted in a level data
 * attachment ({@link DungeonInstanceStore}) so relogging and server restart do not lose it, and so a
 * second boss is never spawned after a chunk reload (guarded by {@code activated}/{@code bossId}).
 */
public final class DungeonInstance {
    public static final Codec<UUID> UUID_CODEC =
            Codec.STRING.xmap(UUID::fromString, UUID::toString);

    public static final Codec<DungeonInstance> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            UUID_CODEC.fieldOf("owner").forGetter(DungeonInstance::owner),
            ResourceLocation.CODEC.fieldOf("boss_id").forGetter(DungeonInstance::bossId),
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(DungeonInstance::dimension),
            BlockPos.CODEC.fieldOf("pos").forGetter(DungeonInstance::pos),
            Codec.BOOL.optionalFieldOf("activated", false).forGetter(DungeonInstance::activated),
            UUID_CODEC.optionalFieldOf("boss_uuid").forGetter(i -> Optional.ofNullable(i.bossUuid)),
            Codec.BOOL.optionalFieldOf("defeated", false).forGetter(DungeonInstance::defeated),
            UUID_CODEC.optionalFieldOf("encounter_id").forGetter(i -> Optional.ofNullable(i.encounterId)),
            BlockPos.CODEC.optionalFieldOf("spawn_pos").forGetter(i -> Optional.of(i.spawnPos)),
            Codec.intRange(4, 128).optionalFieldOf("activation_radius", 24).forGetter(DungeonInstance::activationRadius),
            Codec.STRING.optionalFieldOf("site_key", "").forGetter(DungeonInstance::siteKey),
            DungeonDestruction.CODEC.optionalFieldOf("destruction").forGetter(i -> Optional.ofNullable(i.destruction))
    ).apply(inst, DungeonInstance::new));

    private final UUID owner;
    private final ResourceLocation bossId;
    private final ResourceKey<Level> dimension;
    private final BlockPos pos;

    private boolean activated;
    private UUID bossUuid;
    private boolean defeated;
    private final UUID encounterId;
    private final BlockPos spawnPos;
    private final int activationRadius;
    private final String siteKey;
    private final DungeonDestruction destruction;

    public DungeonInstance(UUID owner, ResourceLocation bossId, ResourceKey<Level> dimension, BlockPos pos,
                           boolean activated, Optional<UUID> bossUuid, boolean defeated, Optional<UUID> encounterId,
                           Optional<BlockPos> spawnPos, int activationRadius, String siteKey, Optional<DungeonDestruction> destruction) {
        this.owner = owner;
        this.bossId = bossId;
        this.dimension = dimension;
        this.pos = pos;
        this.activated = activated;
        this.bossUuid = bossUuid.orElse(null);
        this.defeated = defeated;
        this.encounterId = encounterId.orElse(null);
        this.spawnPos = spawnPos.orElse(pos.above());
        this.activationRadius = activationRadius;
        this.siteKey = siteKey.isEmpty() ? "legacy:" + pos.asLong() : siteKey;
        this.destruction = destruction.orElse(null);
    }

    public DungeonInstance(UUID owner, ResourceLocation bossId, ResourceKey<Level> dimension, BlockPos pos, UUID encounterId,
                           BlockPos spawnPos, int activationRadius, String siteKey, DungeonDestruction destruction) {
        this(owner, bossId, dimension, pos, false, Optional.empty(), false, Optional.of(encounterId), Optional.of(spawnPos), activationRadius, siteKey, Optional.ofNullable(destruction));
    }

    // --- codec accessors ---
    public UUID owner() {
        return owner;
    }

    public ResourceLocation bossId() {
        return bossId;
    }

    public ResourceKey<Level> dimension() {
        return dimension;
    }

    public BlockPos pos() {
        return pos;
    }

    public UUID encounterId() { return encounterId; }
    public BlockPos spawnPos() { return spawnPos; }
    public int activationRadius() { return activationRadius; }
    public String siteKey() { return siteKey; }
    public DungeonDestruction destruction() { return destruction; }

    public boolean activated() {
        return activated;
    }

    public UUID bossUuid() {
        return bossUuid;
    }

    public boolean defeated() {
        return defeated;
    }

    // --- lifecycle mutators (used from MVP5 onward) ---
    void setActivated(boolean activated) {
        this.activated = activated;
    }

    void setBossUuid(UUID bossUuid) {
        this.bossUuid = bossUuid;
    }

    void setDefeated(boolean defeated) {
        this.defeated = defeated;
    }

    /** Map key within a single dimension's store. */
    public String key() {
        return pos.getX() + "_" + pos.getY() + "_" + pos.getZ();
    }
}

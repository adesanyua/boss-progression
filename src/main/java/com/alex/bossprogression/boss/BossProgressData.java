package com.alex.bossprogression.boss;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * Root of the persistent per-player boss progression. Stored as a NeoForge data attachment
 * (see {@link com.alex.bossprogression.registry.ModAttachments}).
 *
 * <p>Holds one {@link BossState} per boss id, keyed by {@link ResourceLocation} so adding/removing
 * boss definitions never shifts indices in existing saves. Also carries the {@code journalReceived}
 * flag so the Boss Journal is handed out exactly once (survives relog and, via copy-on-death, respawn).
 *
 * <p>{@link #STREAM_CODEC} is used only to sync this to the owning client for the journal GUI; the
 * authoritative copy always lives on the server.
 */
public final class BossProgressData {
    /** Hard cap on remembered chest locations, so the set cannot grow without bound (bounded storage). */
    public static final int LOOTED_CHEST_BOUND = 8192;

    public static final Codec<BossProgressData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.unboundedMap(ResourceLocation.CODEC, BossState.CODEC)
                    .fieldOf("bosses").orElse(Map.of())
                    .forGetter(BossProgressData::bosses),
            Codec.BOOL.fieldOf("journal_received").orElse(false)
                    .forGetter(BossProgressData::journalReceived),
            Codec.STRING.listOf().fieldOf("looted_chests").orElse(List.of())
                    .forGetter(d -> List.copyOf(d.lootedChests()))
    ).apply(inst, (bosses, journalReceived, chests) -> new BossProgressData(bosses, journalReceived, chests)));

    /** Network codec used for client sync of the journal GUI (no registry elements involved). */
    public static final StreamCodec<ByteBuf, BossProgressData> STREAM_CODEC =
            ByteBufCodecs.fromCodec(CODEC);

    private final Map<ResourceLocation, BossState> bosses;
    private final boolean journalReceived;
    private final Set<String> lootedChests;

    public BossProgressData(Map<ResourceLocation, BossState> bosses, boolean journalReceived, List<String> lootedChests) {
        this.bosses = new HashMap<>(bosses);
        this.journalReceived = journalReceived;
        this.lootedChests = new HashSet<>(lootedChests);
    }

    public BossProgressData() {
        this(Map.of(), false, List.of());
    }

    public Map<ResourceLocation, BossState> bosses() {
        return bosses;
    }

    public boolean journalReceived() {
        return journalReceived;
    }

    /** Locations of chests already counted towards a loot-chest condition (first-open semantics). */
    public Set<String> lootedChests() {
        return lootedChests;
    }

    /** Returns the existing state for a boss, creating an empty one if absent. */
    public BossState getOrCreate(ResourceLocation bossId) {
        return bosses.computeIfAbsent(bossId, id -> new BossState());
    }

    public BossState get(ResourceLocation bossId) {
        return bosses.get(bossId);
    }

    public void remove(ResourceLocation bossId) {
        bosses.remove(bossId);
    }

    /**
     * The {@code journal_received} flag is immutable (frozen at construction) so that {@link #CODEC}
     * round-trips it. Progress mutations happen in place on the live {@link BossState} objects, but
     * the flag itself must be flipped by replacing the whole attachment value
     * ({@link com.alex.bossprogression.boss.BossProgressManager#markJournalReceived}).
     */
    public BossProgressData withJournalReceived(boolean value) {
        return value == this.journalReceived
                ? this
                : new BossProgressData(this.bosses, value, List.copyOf(this.lootedChests));
    }
}

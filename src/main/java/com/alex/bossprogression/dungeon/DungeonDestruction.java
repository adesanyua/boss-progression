package com.alex.bossprogression.dungeon;

import com.alex.bossprogression.BossProgressionMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Incremental, persisted snapshot and removal queue. Never removes a whole bounding box blindly. */
public final class DungeonDestruction {
    public static final int MAX_BLOCKS = 262144;
    private static final long MAX_SCAN = 2_000_000;
    public record SavedBlock(BlockPos pos, BlockState state) {
        public static final Codec<SavedBlock> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockPos.CODEC.fieldOf("pos").forGetter(SavedBlock::pos),
                BlockState.CODEC.fieldOf("state").forGetter(SavedBlock::state)
        ).apply(i, SavedBlock::new));
    }
    public static final Codec<DungeonDestruction> CODEC = RecordCodecBuilder.create(i -> i.group(
            DestructionConfig.CODEC.fieldOf("settings").forGetter(d -> d.settings),
            Codec.STRING.fieldOf("mode").forGetter(d -> d.mode),
            BoundingBox.CODEC.listOf().fieldOf("regions").forGetter(d -> d.regions),
            BlockPos.CODEC.listOf().fieldOf("template_positions").forGetter(d -> d.explicit),
            SavedBlock.CODEC.listOf().fieldOf("snapshot").forGetter(d -> d.snapshot),
            Codec.LONG.listOf().fieldOf("protected_positions").forGetter(d -> List.copyOf(d.protectedPositions)),
            Codec.intRange(0, Integer.MAX_VALUE).fieldOf("capture_cursor").forGetter(d -> d.captureCursor),
            Codec.intRange(0, Integer.MAX_VALUE).fieldOf("removal_cursor").forGetter(d -> d.removalCursor),
            Codec.STRING.fieldOf("phase").forGetter(d -> d.phase),
            Codec.intRange(0, 72000).fieldOf("delay_remaining").forGetter(d -> d.delayRemaining),
            Codec.LONG.optionalFieldOf("random_state", 0L).forGetter(d -> d.randomState),
            BlockPos.CODEC.optionalFieldOf("collapse_center").forGetter(d -> Optional.ofNullable(d.collapseCenter)),
            Codec.intRange(0, 6).optionalFieldOf("patch_remaining", 0).forGetter(d -> d.patchRemaining)
    ).apply(i, DungeonDestruction::new));

    private final DestructionConfig settings;
    private final String mode;
    private final List<BoundingBox> regions;
    private final List<BlockPos> explicit;
    private final List<SavedBlock> snapshot;
    private final Set<Long> protectedPositions;
    private final Set<Long> captured = new HashSet<>();
    private int captureCursor;
    private int removalCursor;
    private String phase;
    private int delayRemaining;
    private long randomState;
    private BlockPos collapseCenter;
    private int patchRemaining;
    private final Map<Long, Integer> remainingIndices = new HashMap<>();
    private final int bottomY;
    private final long ruinSeed;

    private DungeonDestruction(DestructionConfig settings, String mode, List<BoundingBox> regions, List<BlockPos> explicit,
                               List<SavedBlock> snapshot, List<Long> protectedPositions, int captureCursor,
                               int removalCursor, String phase, int delayRemaining, long randomState, Optional<BlockPos> collapseCenter, int patchRemaining) {
        this.settings = settings;
        this.mode = mode;
        this.regions = new ArrayList<>(regions);
        this.explicit = new ArrayList<>(explicit);
        this.snapshot = new ArrayList<>(snapshot);
        this.protectedPositions = new HashSet<>(protectedPositions);
        this.captureCursor = captureCursor;
        this.removalCursor = removalCursor;
        this.phase = phase;
        this.delayRemaining = delayRemaining;
        snapshot.forEach(b -> captured.add(b.pos().asLong()));
        this.bottomY = regions.stream().mapToInt(BoundingBox::minY).min().orElse(Integer.MIN_VALUE);
        this.ruinSeed = 0x9E3779B97F4A7C15L ^ (regions.isEmpty() ? 1 : regions.getFirst().getCenter().asLong());
        this.randomState = randomState == 0 ? ruinSeed : randomState;
        if (this.randomState == 0) this.randomState = 1;
        this.collapseCenter = collapseCenter.orElse(null);
        this.patchRemaining = patchRemaining;
        for (int index = removalCursor; index < snapshot.size(); index++) remainingIndices.put(snapshot.get(index).pos().asLong(), index);
    }
    public static DungeonDestruction create(DestructionConfig settings, String mode, List<BoundingBox> regions, List<BlockPos> positions) {
        if (!settings.destroyAfterDefeat() || !(mode.equals("nbt") || mode.equals("structure"))) return null;
        if (mode.equals("nbt") && positions.isEmpty()) return null;
        long total = positions.isEmpty() ? regions.stream().mapToLong(DungeonDestruction::volume).sum() : positions.size();
        if (total > MAX_SCAN || positions.size() > MAX_BLOCKS) {
            BossProgressionMod.LOGGER.warn("Dungeon destruction disabled: snapshot exceeds scan limit ({})", total);
            return null;
        }
        return new DungeonDestruction(settings, mode, regions, positions, List.of(), List.of(), 0, 0, "capturing", settings.delayTicks(), 0, Optional.empty(), 0);
    }
    private static long volume(BoundingBox box) { return (long) box.getXSpan() * box.getYSpan() * box.getZSpan(); }
    public boolean capturing() { return phase.equals("capturing"); }
    public boolean finished() { return phase.equals("done"); }
    public boolean begin() {
        if (!phase.equals("ready")) return false;
        phase = "waiting";
        return true;
    }
    public int delaySeconds() { return (delayRemaining + 19) / 20; }

    /** Protects even same-material replacements after the snapshot was requested. */
    public boolean protect(BlockPos pos) {
        if (finished() || regions.stream().noneMatch(b -> b.isInside(pos))) return false;
        if (protectedPositions.size() >= MAX_BLOCKS) {
            BossProgressionMod.LOGGER.warn("Dungeon destruction disabled: too many player edits");
            finish();
            return true;
        }
        return protectedPositions.add(pos.asLong());
    }
    private BlockPos source(int index) {
        if (!explicit.isEmpty()) return index < explicit.size() ? explicit.get(index) : null;
        long offset = index;
        for (BoundingBox box : regions) {
            long size = volume(box);
            if (offset >= size) { offset -= size; continue; }
            int x = (int) (offset % box.getXSpan());
            int z = (int) (offset / box.getXSpan() % box.getZSpan());
            int y = (int) (offset / ((long) box.getXSpan() * box.getZSpan()));
            return new BlockPos(box.minX() + x, box.maxY() - y, box.minZ() + z);
        }
        return null;
    }

    private int randomIndex(int bound) {
        randomState ^= randomState << 13;
        randomState ^= randomState >>> 7;
        randomState ^= randomState << 17;
        return (int) Math.floorMod(randomState, (long) bound);
    }

    /** Small random local patches give spreading cracks, not a horizontal scanning wipe. */
    private int chooseNext() {
        if (collapseCenter != null && patchRemaining > 0) {
            int start = randomIndex(27);
            for (int step = 0; step < 27; step++) {
                int point = (start + step) % 27;
                BlockPos pos = collapseCenter.offset(point % 3 - 1, point / 9 - 1, point / 3 % 3 - 1);
                Integer index = remainingIndices.get(pos.asLong());
                if (index != null) { patchRemaining--; return index; }
            }
        }
        int index = removalCursor + randomIndex(snapshot.size() - removalCursor);
        collapseCenter = snapshot.get(index).pos();
        patchRemaining = 6;
        return index;
    }

    private boolean foundation(BlockPos pos) {
        return (long) pos.getY() < (long) bottomY + settings.preserveBottomLayers();
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
    private long columnHash(int x, int z) { return mix(ruinSeed ^ ((long) x * 0x632BE59BD9B4E019L) ^ ((long) z * 0x9E3779B97F4A7C15L)); }
    private static double unit(long hash) { return (hash >>> 11) * 0x1.0p-53; }

    /** Organic ground-level clusters with jagged heights, rather than floating random roof blocks. */
    private boolean ruin(ServerLevel level, SavedBlock block) {
        if (settings.ruinChance() == 0) return false;
        BlockPos pos = block.pos();
        long height = (long) pos.getY() - bottomY - settings.preserveBottomLayers() + 1;
        if (height < 1 || height > settings.ruinMaxHeight() || !structural(level, pos, block.state())) return false;
        int cellX = Math.floorDiv(pos.getX(), 6), cellZ = Math.floorDiv(pos.getZ(), 6);
        double nearest = Double.POSITIVE_INFINITY;
        long selected = 0;
        // Jittered Voronoi cells avoid a visible square-grid pattern. No removal RNG is consumed.
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            int x = cellX + dx, z = cellZ + dz;
            long hash = columnHash(x, z);
            double px = x * 6.0 + 1 + unit(hash) * 4;
            double pz = z * 6.0 + 1 + unit(mix(hash)) * 4;
            double distance = (pos.getX() - px) * (pos.getX() - px) + (pos.getZ() - pz) * (pos.getZ() - pz);
            if (distance < nearest) { nearest = distance; selected = hash; }
        }
        if (unit(mix(selected ^ 0xD1B54A32D192ED03L)) >= settings.ruinChance()) return false;
        int jagged = (int) (columnHash(pos.getX(), pos.getZ()) & 1);
        int retainedHeight = Math.max(1, settings.ruinMaxHeight() - (int) (Math.sqrt(nearest) * 0.8) - jagged);
        if (height > retainedHeight) return false;
        // Every retained column must be connected to the preserved foundation. Roofs across air gaps,
        // decorations, containers and unsupported upper floors do not become floating "ruins".
        for (int below = 1; below <= height; below++) {
            BlockPos support = pos.below(below);
            if (!structural(level, support, level.getBlockState(support))) return false;
        }
        return true;
    }
    private static boolean structural(ServerLevel level, BlockPos pos, BlockState state) {
        return !state.hasBlockEntity() && state.isCollisionShapeFullBlock(level, pos);
    }

    /** Returns consumed work units. All scanning/removal is bounded by the manager's per-level budget. */
    public int tick(ServerLevel level, boolean defeated, int budget, long deadline) {
        if (finished()) return 0;
        if (phase.equals("ready")) {
            if (!defeated) return 0;
            begin();
        }
        if (phase.equals("waiting")) {
            if (delayRemaining > 0) { delayRemaining--; return 1; }
            phase = "removing";
        }
        int work = 0;
        int limit = Math.min(budget, capturing() ? 256 : settings.blocksPerTick());
        TagKey<Block> allowed = TagKey.create(Registries.BLOCK, settings.structureBlockTag());
        TagKey<Block> protectedTerrain = TagKey.create(Registries.BLOCK, settings.protectedBlockTag());
        while (work < limit && System.nanoTime() < deadline) {
            if (capturing()) {
                BlockPos pos = source(captureCursor);
                if (pos == null) { phase = "ready"; explicit.clear(); return Math.max(1, work); }
                captureCursor++;
                work++;
                if (foundation(pos) || protectedPositions.contains(pos.asLong()) || captured.contains(pos.asLong())) continue;
                level.getChunk(pos);
                BlockState state = level.getBlockState(pos);
                if (state.isAir() || state.is(Blocks.STRUCTURE_VOID) || state.is(Blocks.STRUCTURE_BLOCK)
                        || state.is(Blocks.BEDROCK) || state.is(protectedTerrain) || mode.equals("structure") && !state.is(allowed)) continue;
                if (snapshot.size() >= MAX_BLOCKS) {
                    BossProgressionMod.LOGGER.warn("Dungeon destruction disabled: snapshot exceeds {} blocks", MAX_BLOCKS);
                    finish(); return work;
                }
                remainingIndices.put(pos.asLong(), snapshot.size());
                snapshot.add(new SavedBlock(pos, state));
                captured.add(pos.asLong());
            } else if (phase.equals("removing")) {
                if (removalCursor >= snapshot.size()) { finish(); return Math.max(1, work); }
                int selected = chooseNext();
                SavedBlock block = snapshot.get(selected);
                SavedBlock displaced = snapshot.get(removalCursor);
                snapshot.set(selected, displaced);
                snapshot.set(removalCursor, block);
                remainingIndices.put(displaced.pos().asLong(), selected);
                remainingIndices.remove(block.pos().asLong());
                removalCursor++;
                work++;
                if (foundation(block.pos()) || block.state().is(protectedTerrain)
                        || protectedPositions.contains(block.pos().asLong())) continue;
                level.getChunk(block.pos());
                if (!level.getBlockState(block.pos()).equals(block.state()) || ruin(level, block)) continue;
                // Suppress container onRemove drops BEFORE changing its block, and never unpack its loot table.
                level.removeBlockEntity(block.pos());
                if (!level.setBlock(block.pos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE)) {
                    removalCursor--; // a rejected world write must be retryable
                    remainingIndices.put(block.pos().asLong(), removalCursor);
                    return work;
                }
                if ((removalCursor & 3) == 0) level.levelEvent(2001, block.pos(), Block.getId(block.state()));
            } else break;
        }
        return work;
    }
    private void finish() {
        phase = "done";
        regions.clear(); explicit.clear(); snapshot.clear(); protectedPositions.clear(); captured.clear(); remainingIndices.clear();
        collapseCenter = null; patchRemaining = 0;
        captureCursor = 0; removalCursor = 0;
    }
}

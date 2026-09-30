package com.alex.bossprogression.dungeon;

import com.alex.bossprogression.BossProgressionMod;
import com.alex.bossprogression.boss.BossDefinition;
import com.alex.bossprogression.registry.ModAttachments;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.FMLPaths;

/** Resolves a new owned site, optionally placing a local structure-block NBT. */
public final class EncounterPlacement {
    public record Site(BlockPos pos, BlockPos spawn, String key, DungeonDestruction destruction) {}
    private EncounterPlacement() {}
    public static Path nbtDirectory() { return FMLPaths.CONFIGDIR.get().resolve("bossprogression/structures"); }

    public static Optional<Site> prepare(ServerLevel level, BlockPos anchor, BossDefinition boss) {
        try {
            return switch (boss.dungeon().mode()) {
                case "structure" -> structure(level, anchor, boss);
                case "biome" -> biome(level, anchor, boss);
                case "nbt" -> nbt(level, anchor, boss);
                default -> procedural(level, anchor, boss);
            };
        } catch (Exception e) {
            BossProgressionMod.LOGGER.warn("Cannot prepare encounter {}: {}", boss.id(), e.toString());
            return Optional.empty();
        }
    }

    private static boolean free(ServerLevel level, String key, BlockPos pos) {
        var store = level.getData(ModAttachments.DUNGEON_STORE.get());
        return store.at(pos) == null && store.instances().values().stream().noneMatch(i -> i.siteKey().equals(key));
    }
    private static boolean separated(ServerLevel level, BlockPos pos, int margin) {
        return level.getData(ModAttachments.DUNGEON_STORE.get()).instances().values().stream()
                .noneMatch(i -> Math.abs(i.pos().getX() - pos.getX()) < margin && Math.abs(i.pos().getZ() - pos.getZ()) < margin);
    }
    private static Optional<Site> procedural(ServerLevel level, BlockPos anchor, BossDefinition boss) {
        for (int attempt = 0; attempt < 8; attempt++) {
            var located = DungeonLocator.locate(level, anchor, boss.dungeon());
            if (located.isEmpty()) return Optional.empty();
            BlockPos pos = located.get();
            if (!separated(level, pos, boss.dungeon().size() + 32)) continue;
            if (!DungeonGenerator.generate(level, pos, boss.dungeon())) return Optional.empty();
            return Optional.of(new Site(pos, pos.above().offset(boss.dungeon().spawnOffset()), "procedural:" + pos.asLong(), null));
        }
        return Optional.empty();
    }

    private static Optional<Site> structure(ServerLevel level, BlockPos anchor, BossDefinition boss) {
        var config = boss.dungeon();
        if (!level.getServer().getWorldData().worldGenOptions().generateStructures()) return Optional.empty();
        var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        var holder = registry.getHolder(net.minecraft.resources.ResourceKey.create(Registries.STRUCTURE, config.structure().orElseThrow()));
        if (holder.isEmpty()) throw new IllegalArgumentException("Unknown structure " + config.structure().get());
        for (int attempt = 0; attempt < 8; attempt++) {
            BlockPos search = attempt == 0 ? anchor : anchor.offset(
                    (attempt % 3 - 1) * config.searchRadius() * 16, 0, (attempt / 3 - 1) * config.searchRadius() * 16);
            var found = level.getChunkSource().getGenerator().findNearestMapStructure(
                    level, HolderSet.direct(holder.get()), search, config.searchRadius(), false);
            if (found == null) continue;
            BlockPos located = found.getFirst();
            level.getChunk(located);
            for (var start : level.structureManager().startsForStructure(new ChunkPos(located), s -> s == holder.get().value())) {
                if (!start.isValid()) continue;
                String key = "structure:" + config.structure().get() + ":" + start.getChunkPos().toLong();
                BoundingBox bounds = start.getBoundingBox();
                if (!free(level, key, bounds.getCenter())) continue;
                var spawn = findSafe(level, bounds.getCenter(), bounds, boss, config.spawnOffset(),
                        p -> level.structureManager().structureHasPieceAt(p, start));
                if (spawn.isPresent() && free(level, key, spawn.get().below()))
                    return Optional.of(new Site(spawn.get().below(), spawn.get(), key,
                            DungeonDestruction.create(config.destruction(), "structure",
                                    start.getPieces().stream().map(p -> p.getBoundingBox()).toList(), java.util.List.of())));
            }
        }
        return Optional.empty();
    }

    private static Optional<Site> biome(ServerLevel level, BlockPos anchor, BossDefinition boss) {
        var config = boss.dungeon();
        var id = config.biome().orElseThrow();
        if (!level.registryAccess().registryOrThrow(Registries.BIOME).containsKey(id)) throw new IllegalArgumentException("Unknown biome " + id);
        for (int attempt = 0; attempt < 8; attempt++) {
            BlockPos search = attempt == 0 ? anchor : anchor.offset(
                    level.random.nextInt(config.maxDistance() * 2 + 1) - config.maxDistance(), 0,
                    level.random.nextInt(config.maxDistance() * 2 + 1) - config.maxDistance());
            var found = level.findClosestBiome3d(h -> h.is(id), search, config.maxDistance(), 32, 16);
            if (found == null) continue;
            BlockPos center = found.getFirst();
            level.getChunk(center);
            if (!level.dimensionType().hasCeiling()) center = new BlockPos(center.getX(), level.getHeight(
                    aquatic(boss) ? Heightmap.Types.OCEAN_FLOOR : Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    center.getX(), center.getZ()), center.getZ());
            var spawn = findSafe(level, center, null, boss, config.spawnOffset(), p -> level.getBiome(p).is(id));
            if (spawn.isEmpty() || !level.getBiome(spawn.get()).is(id)
                    || spawn.get().distSqr(anchor) < (double) config.minDistance() * config.minDistance()
                    || !separated(level, spawn.get(), 64)) continue;
            return Optional.of(new Site(spawn.get().below(), spawn.get(), "biome:" + spawn.get().asLong(), null));
        }
        return Optional.empty();
    }

    private static Optional<Site> nbt(ServerLevel level, BlockPos anchor, BossDefinition boss) throws Exception {
        Files.createDirectories(nbtDirectory());
        Path root = nbtDirectory().toRealPath();
        Path file = root.resolve(boss.dungeon().nbt().orElseThrow()).normalize().toRealPath();
        if (!file.startsWith(root) || !Files.isRegularFile(file) || Files.size(file) > 8 * 1024 * 1024)
            throw new IllegalArgumentException("Unsafe or oversized NBT file");
        var tag = NbtIo.readCompressed(file, NbtAccounter.create(32 * 1024 * 1024));
        StructureTemplate template = new StructureTemplate();
        template.load(level.registryAccess().lookupOrThrow(Registries.BLOCK), tag);
        var size = template.getSize();
        if (size.getX() < 1 || size.getY() < 1 || size.getZ() < 1 || size.getX() > 128 || size.getY() > 128 || size.getZ() > 128
                || (long) size.getX() * size.getY() * size.getZ() > 262144)
            throw new IllegalArgumentException("NBT dimensions exceed limits (128 per axis, 262144 blocks)");
        BlockPos localSpawn = boss.dungeon().spawnOffset();
        if (localSpawn.getX() < 0 || localSpawn.getY() < 0 || localSpawn.getZ() < 0
                || localSpawn.getX() >= size.getX() || localSpawn.getY() >= size.getY() || localSpawn.getZ() >= size.getZ())
            throw new IllegalArgumentException("spawn_offset must be inside the NBT template");
        for (int attempt = 0; attempt < 8; attempt++) {
            var located = DungeonLocator.locate(level, anchor, boss.dungeon());
            if (located.isEmpty()) return Optional.empty();
            BlockPos origin = located.get();
            BlockPos end = origin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1);
            if (end.getY() >= level.getMaxBuildHeight() || !level.getWorldBorder().isWithinBounds(origin)
                    || !level.getWorldBorder().isWithinBounds(end) || !separated(level, origin, Math.max(size.getX(), size.getZ()) + 128)) continue;
            for (int x = origin.getX() >> 4; x <= end.getX() >> 4; x++)
                for (int z = origin.getZ() >> 4; z <= end.getZ() >> 4; z++) level.getChunk(x, z);
            // Ignore saved entities: the encounter boss is spawned once by BossSpawnManager, never copied from NBT.
            if (!template.placeInWorld(level, origin, origin, new StructurePlaceSettings().setIgnoreEntities(true), level.getRandom(), 2))
                throw new IllegalArgumentException("Empty or unplaceable NBT template");
            BlockPos spawn = origin.offset(boss.dungeon().spawnOffset());
            // Persist even a blocked spawn: retrying must not duplicate a successfully placed building.
            if (!safe(level, spawn, boss)) BossProgressionMod.LOGGER.warn(
                    "NBT {} placed at {}, but boss spawn {} is blocked/unsuitable; clear that spot. No duplicate template will be placed.",
                    file.getFileName(), origin, spawn);
            java.util.List<BlockPos> positions = new java.util.ArrayList<>();
            if (boss.dungeon().destruction().destroyAfterDefeat()) {
                var blocks = tag.getList("blocks", 10);
                for (int index = 0; index < blocks.size(); index++) {
                    var point = blocks.getCompound(index).getList("pos", 3);
                    if (point.size() != 3) continue;
                    int x = point.getInt(0), y = point.getInt(1), z = point.getInt(2);
                    if (x >= 0 && y >= 0 && z >= 0 && x < size.getX() && y < size.getY() && z < size.getZ())
                        positions.add(origin.offset(x, y, z));
                }
                positions.sort(java.util.Comparator.<BlockPos>comparingInt(p -> p.getY()).reversed());
            }
            var cleanup = DungeonDestruction.create(boss.dungeon().destruction(), "nbt",
                    java.util.List.of(new BoundingBox(origin.getX(), origin.getY(), origin.getZ(), end.getX(), end.getY(), end.getZ())), positions);
            return Optional.of(new Site(origin, spawn, "nbt:" + origin.asLong(), cleanup));
        }
        return Optional.empty();
    }

    private static Optional<BlockPos> findSafe(ServerLevel level, BlockPos center, BoundingBox bounds, BossDefinition boss,
                                              BlockPos offset, Predicate<BlockPos> permitted) {
        BlockPos preferred = center.offset(offset);
        if ((bounds == null || bounds.isInside(preferred)) && permitted.test(preferred) && safe(level, preferred, boss)) return Optional.of(preferred);
        for (int radius = 0; radius <= 16; radius += 2) {
            for (int dx = -radius; dx <= radius; dx += 2) for (int dz = -radius; dz <= radius; dz += 2) {
                if (radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                for (int dy = -12; dy <= 12; dy++) {
                    BlockPos pos = preferred.offset(dx, dy, dz);
                    if ((bounds == null || bounds.isInside(pos)) && permitted.test(pos) && safe(level, pos, boss)) return Optional.of(pos);
                }
            }
        }
        return Optional.empty();
    }
    private static boolean aquatic(BossDefinition boss) {
        return boss.bossEntity().getNamespace().equals("minecraft")
                && (boss.bossEntity().getPath().equals("guardian") || boss.bossEntity().getPath().equals("elder_guardian"));
    }
    public static boolean safe(ServerLevel level, BlockPos pos, BossDefinition boss) {
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(boss.bossEntity()) || pos.getY() < level.getMinBuildHeight()
                || pos.getY() >= level.getMaxBuildHeight() - 4 || !level.getWorldBorder().isWithinBounds(pos)) return false;
        level.getChunk(pos);
        var type = BuiltInRegistries.ENTITY_TYPE.get(boss.bossEntity());
        var box = type.getDimensions().makeBoundingBox(new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5));
        if (!level.noCollision(box) || level.getFluidState(pos).is(FluidTags.LAVA)) return false;
        if (aquatic(boss)) return level.getFluidState(pos).is(FluidTags.WATER);
        return level.getFluidState(pos).isEmpty() && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }
}

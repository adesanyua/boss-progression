package com.alex.bossprogression.dungeon;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

/**
 * Finds a suitable world position for a dungeon. Server-side only.
 *
 * <p>For MVP3 the search is anchored on the owner's current position: candidate points are sampled
 * in a ring between {@code minDistance} and {@code maxDistance}, must lie inside the world border
 * (with a margin of the dungeon footprint), must resolve to a real surface height, and their chunks
 * are force-loaded so {@link DungeonGenerator} can actually place blocks there. A dedicated worldgen
 * structure is deliberately NOT toggled on at runtime (per Stage 8).
 */
public final class DungeonLocator {
    private static final int MAX_ATTEMPTS = 48;

    private DungeonLocator() {
    }

    public static Optional<BlockPos> locate(ServerLevel level, BlockPos anchor, DungeonConfig config) {
        RandomSource random = level.getRandom();
        int margin = config.halfSize() + 2;

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            double angle = random.nextDouble() * (Math.PI * 2.0);
            int distance = config.minDistance() + random.nextInt(Math.max(1, config.maxDistance() - config.minDistance()));

            int x = anchor.getX() + (int) Math.round(Math.cos(angle) * distance);
            int z = anchor.getZ() + (int) Math.round(Math.sin(angle) * distance);

            if (!level.getWorldBorder().isWithinBounds(x - margin, z - margin)
                    || !level.getWorldBorder().isWithinBounds(x + margin, z + margin)) {
                continue;
            }

            BlockPos surface = surfaceAt(level, x, z);
            if (surface != null) {
                return Optional.of(surface);
            }
        }
        return Optional.empty();
    }

    /**
     * Returns the surface origin for a dungeon at column (x, z), or {@code null} if the area is not a
     * usable height range. Force-loads the chunk so height and later block placement are valid.
     */
    @Nullable
    private static BlockPos surfaceAt(ServerLevel level, int x, int z) {
        level.getChunk(new BlockPos(x, 0, z)); // ensure the chunk exists (server-side load)
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        if (y <= level.getMinBuildHeight() + 1 || y >= level.getMaxBuildHeight() - 8) {
            return null;
        }
        return new BlockPos(x, y, z);
    }
}

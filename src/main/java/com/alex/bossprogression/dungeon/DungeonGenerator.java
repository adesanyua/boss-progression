package com.alex.bossprogression.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Places a dungeon at a located position. Server-side only.
 *
 * <p>MVP3 builds a small procedural test room (floor, walls, ceiling light, central boss marker)
 * instead of loading a {@code StructureTemplate}. The abstraction is intentionally identical to the
 * future template-based generator: given a {@link ServerLevel}, an origin and a {@link DungeonConfig},
 * it fills the footprint and returns whether placement succeeded. Swapping in
 * {@code StructureTemplateManager} (Stage 8) only replaces {@link #buildRoom}.
 */
public final class DungeonGenerator {
    private static final BlockState FLOOR = Blocks.STONE_BRICKS.defaultBlockState();
    private static final BlockState WALL = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState LIGHT = Blocks.SEA_LANTERN.defaultBlockState();
    private static final BlockState MARKER = Blocks.GOLD_BLOCK.defaultBlockState();
    private static final int ROOM_HEIGHT = 4;

    private DungeonGenerator() {
    }

    /**
     * Builds the dungeon centered on {@code origin} (its floor sits at {@code origin.getY()}).
     *
     * @return true if the room was placed; false on any failure (leaving the encounter retryable)
     */
    public static boolean generate(ServerLevel level, BlockPos origin, DungeonConfig config) {
        try {
            forceLoadChunks(level, origin, config.halfSize());
            buildRoom(level, origin, config);
            return true;
        } catch (RuntimeException e) {
            com.alex.bossprogression.BossProgressionMod.LOGGER.error(
                    "Failed to generate dungeon at {}", origin.toShortString(), e);
            return false;
        }
    }

    /** Ensures every chunk the footprint touches is loaded so setBlock() has a real effect. */
    private static void forceLoadChunks(ServerLevel level, BlockPos origin, int half) {
        int minChunkX = (origin.getX() - half) >> 4;
        int maxChunkX = (origin.getX() + half) >> 4;
        int minChunkZ = (origin.getZ() - half) >> 4;
        int maxChunkZ = (origin.getZ() + half) >> 4;
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                level.getChunk(cx, cz);
            }
        }
    }

    private static void buildRoom(ServerLevel level, BlockPos origin, DungeonConfig config) {
        int half = config.halfSize();
        boolean aquatic = config.template().equals("guardian_chamber");
        int roomHeight = config.template().equals("wither_chamber") ? 10 : aquatic ? 7 : ROOM_HEIGHT;
        if (origin.getY() + roomHeight >= level.getMaxBuildHeight()) throw new IllegalArgumentException("Arena exceeds build height");
        BlockState floor = aquatic ? Blocks.PRISMARINE_BRICKS.defaultBlockState() : FLOOR;
        BlockState wall = aquatic ? Blocks.PRISMARINE.defaultBlockState() : WALL;
        int floorY = origin.getY();
        int minX = origin.getX() - half;
        int maxX = origin.getX() + half;
        int minZ = origin.getZ() - half;
        int maxZ = origin.getZ() + half;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                boolean perimeter = x == minX || x == maxX || z == minZ || z == maxZ;
                // Floor.
                level.setBlock(new BlockPos(x, floorY, z), floor, BLOCK_FLAGS);
                // Walls and hollow interior.
                for (int dy = 1; dy <= roomHeight; dy++) {
                    BlockPos pos = new BlockPos(x, floorY + dy, z);
                    BlockState interior = aquatic && dy < roomHeight - 1 ? Blocks.WATER.defaultBlockState() : AIR;
                    level.setBlock(pos, perimeter ? wall : interior, BLOCK_FLAGS);
                }
            }
        }

        // Ceiling light and the central boss spawn marker (used from MVP5 on).
        level.setBlock(new BlockPos(origin.getX(), floorY + roomHeight, origin.getZ()), LIGHT, BLOCK_FLAGS);
        level.setBlock(new BlockPos(origin.getX(), floorY, origin.getZ()), MARKER, BLOCK_FLAGS);
    }

    private static final int BLOCK_FLAGS = Block.UPDATE_ALL;
}

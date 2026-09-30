package com.alex.bossprogression.dungeon;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Executed only by GameTest; uses isolated test structures, never player dungeons. */
@GameTestHolder("bossprogression")
@PrefixGameTestTemplate(false)
public final class DungeonDestructionTests {
    private static DestructionConfig settings() {
        return new DestructionConfig(true, 0, 1, DestructionConfig.DEFAULT.structureBlockTag(), 1, DestructionConfig.DEFAULT.protectedBlockTag(), 0, 5);
    }
    private static DungeonDestruction roundTrip(DungeonDestruction plan) {
        var data = DungeonDestruction.CODEC.encodeStart(NbtOps.INSTANCE, plan).getOrThrow();
        return DungeonDestruction.CODEC.parse(NbtOps.INSTANCE, data).getOrThrow();
    }
    private static void capture(GameTestHelper helper, DungeonDestruction plan) {
        for (int i = 0; i < 20 && plan.capturing(); i++) plan.tick(helper.getLevel(), false, 256, Long.MAX_VALUE);
        helper.assertTrue(!plan.capturing(), "Snapshot should complete");
    }
    @GameTest(template = "empty_test")
    public static void nbtResumeNoDrops(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos stone = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos chest = stone.east();
        BlockPos replaced = chest.east();
        BlockPos outside = replaced.east();
        level.setBlock(stone, Blocks.STONE_BRICKS.defaultBlockState(), 2);
        level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 2);
        level.setBlock(replaced, Blocks.OAK_PLANKS.defaultBlockState(), 2);
        level.setBlock(outside, Blocks.DIRT.defaultBlockState(), 2);
        ((ChestBlockEntity) level.getBlockEntity(chest)).setItem(0, new ItemStack(Items.DIAMOND, 32));
        var plan = DungeonDestruction.create(settings(), "nbt", List.of(new BoundingBox(stone.getX(), stone.getY() - 1, stone.getZ(),
                replaced.getX(), replaced.getY(), replaced.getZ())), List.of(stone, chest, replaced));
        capture(helper, plan);
        plan.protect(replaced); // same-state player replacement must survive
        plan = roundTrip(plan);
        plan.tick(level, true, 256, Long.MAX_VALUE); // one block only
        int removed = (level.getBlockState(stone).isAir() ? 1 : 0) + (level.getBlockState(chest).isAir() ? 1 : 0);
        helper.assertTrue(removed <= 1, "Per-tick limit must apply even with randomized order");
        plan = roundTrip(plan); // restart in the middle of the removal cursor
        for (int i = 0; i < 8; i++) plan.tick(level, true, 256, Long.MAX_VALUE);
        helper.assertTrue(plan.finished(), "Queue must finish after restore");
        helper.assertTrue(level.getBlockState(chest).isAir(), "Chest must disappear");
        helper.assertTrue(level.getBlockState(replaced).is(Blocks.OAK_PLANKS), "Protected replacement must survive restart");
        helper.assertTrue(level.getBlockState(outside).is(Blocks.DIRT), "Outside block must survive");
        helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, new AABB(stone).inflate(6)).isEmpty(), "Cleanup must not drop chest items");
        helper.assertTrue(!plan.begin(), "Finished queue must not restart");
        helper.succeed();
    }
    @GameTest(template = "empty_test")
    public static void structureKeepsTerrainAndEdits(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos wood = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos dirt = wood.east();
        BlockPos changed = dirt.east();
        level.setBlock(wood, Blocks.OAK_PLANKS.defaultBlockState(), 2);
        level.setBlock(dirt, Blocks.DIRT.defaultBlockState(), 2);
        level.setBlock(changed, Blocks.OAK_PLANKS.defaultBlockState(), 2);
        var plan = DungeonDestruction.create(settings(), "structure", List.of(new BoundingBox(wood.getX(), wood.getY() - 1, wood.getZ(),
                changed.getX(), changed.getY(), changed.getZ())), List.of());
        capture(helper, plan);
        level.setBlock(changed, Blocks.DIAMOND_BLOCK.defaultBlockState(), 2);
        for (int i = 0; i < 8; i++) plan.tick(level, true, 256, Long.MAX_VALUE);
        helper.assertTrue(level.getBlockState(wood).isAir(), "Tagged structure material must disappear");
        helper.assertTrue(level.getBlockState(dirt).is(Blocks.DIRT), "Terrain outside material tag must remain");
        helper.assertTrue(level.getBlockState(changed).is(Blocks.DIAMOND_BLOCK), "Changed state must not be removed");
        helper.succeed();
    }
    @GameTest(template = "empty_test")
    public static void chaoticPatchesPreserveFoundation(GameTestHelper helper) {
        var level = helper.getLevel();
        var origin = helper.absolutePos(new BlockPos(1, 2, 1));
        java.util.List<BlockPos> positions = new java.util.ArrayList<>();
        for (int y = 3; y >= 0; y--) for (int x = 0; x < 5; x++) {
            BlockPos pos = origin.offset(x, y, 0);
            level.setBlock(pos, Blocks.STONE_BRICKS.defaultBlockState(), 2);
            positions.add(pos);
        }
        BlockPos soil = origin.offset(0, 2, 1);
        level.setBlock(soil, Blocks.DIRT.defaultBlockState(), 2);
        positions.add(soil);
        var box = new BoundingBox(origin.getX(), origin.getY(), origin.getZ(), origin.getX() + 4, origin.getY() + 3, origin.getZ() + 1);
        var plan = DungeonDestruction.create(settings(), "nbt", List.of(box), positions);
        capture(helper, plan);
        var saved = DungeonDestruction.CODEC.encodeStart(NbtOps.INSTANCE, plan).getOrThrow();
        ((net.minecraft.nbt.CompoundTag) saved).putLong("random_state", 1L);
        plan = DungeonDestruction.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();
        for (int i = 0; i < 6; i++) plan.tick(level, true, 256, Long.MAX_VALUE);
        boolean upperStillPresent = false, lowerRemoved = false;
        for (int x = 0; x < 5; x++) {
            upperStillPresent |= !level.getBlockState(origin.offset(x, 3, 0)).isAir();
            lowerRemoved |= level.getBlockState(origin.offset(x, 2, 0)).isAir();
        }
        helper.assertTrue(upperStillPresent && lowerRemoved, "Collapse must spread in patches rather than finish an entire top layer first");
        plan = roundTrip(plan);
        for (int i = 0; i < 40; i++) plan.tick(level, true, 256, Long.MAX_VALUE);
        helper.assertTrue(plan.finished(), "Randomized removal must drain without duplicates or omissions");
        for (int x = 0; x < 5; x++) {
            helper.assertTrue(level.getBlockState(origin.offset(x, 0, 0)).is(Blocks.STONE_BRICKS), "Bottom layer must survive");
            for (int y = 1; y <= 3; y++) helper.assertTrue(level.getBlockState(origin.offset(x, y, 0)).isAir(), "All unprotected wall blocks must disappear");
        }
        helper.assertTrue(level.getBlockState(soil).is(Blocks.DIRT), "Terrain above foundation must also survive");
        helper.succeed();
    }

    @GameTest(template = "empty_test")
    public static void groundedRuinsSurviveRestart(GameTestHelper helper) {
        var level = helper.getLevel();
        var origin = helper.absolutePos(new BlockPos(1, 2, 1));
        java.util.List<BlockPos> positions = new java.util.ArrayList<>();
        for (int x = 0; x < 6; x++) for (int z = 0; z < 6; z++) {
            for (int y = 0; y <= 6; y++) {
                if (y != 0 && y != 6 && x != 0 && x != 5 && z != 0 && z != 5) continue;
                var pos = origin.offset(x, y, z);
                level.setBlock(pos, Blocks.STONE_BRICKS.defaultBlockState(), 2);
                positions.add(pos);
            }
        }
        var floating = origin.offset(2, 2, 2);
        var chest = origin.offset(3, 1, 3);
        level.setBlock(floating, Blocks.STONE_BRICKS.defaultBlockState(), 2);
        level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 2);
        ((ChestBlockEntity) level.getBlockEntity(chest)).setItem(0, new ItemStack(Items.DIAMOND, 8));
        positions.add(floating); positions.add(chest);
        var config = new DestructionConfig(true, 0, 1, DestructionConfig.DEFAULT.structureBlockTag(), 1,
                DestructionConfig.DEFAULT.protectedBlockTag(), 1, 3);
        var box = new BoundingBox(origin.getX(), origin.getY(), origin.getZ(), origin.getX() + 5, origin.getY() + 6, origin.getZ() + 5);
        var plan = DungeonDestruction.create(config, "nbt", List.of(box), positions);
        capture(helper, plan);
        for (int i = 0; i < 30; i++) plan.tick(level, true, 256, Long.MAX_VALUE);
        plan = roundTrip(plan);
        for (int i = 0; i < 250 && !plan.finished(); i++) plan.tick(level, true, 256, Long.MAX_VALUE);
        helper.assertTrue(plan.finished(), "Ruin cleanup must finish after restart");
        int remnants = 0, removed = 0;
        for (BlockPos pos : positions) {
            if (pos.getY() == origin.getY()) {
                helper.assertTrue(level.getBlockState(pos).is(Blocks.STONE_BRICKS), "Foundation must survive");
                continue;
            }
            if (level.getBlockState(pos).isAir()) { removed++; continue; }
            remnants++;
            helper.assertTrue(pos.getY() <= origin.getY() + 3, "Ruin height must be bounded");
            for (int y = pos.getY() - 1; y >= origin.getY(); y--)
                helper.assertTrue(level.getBlockState(new BlockPos(pos.getX(), y, pos.getZ())).is(Blocks.STONE_BRICKS), "Remnants must stay grounded, without gaps");
        }
        helper.assertTrue(remnants > 0 && removed > remnants, "Leave small wall remnants, not a bare foundation or intact house");
        helper.assertTrue(level.getBlockState(floating).isAir(), "Unsupported roof fragment must not survive");
        helper.assertTrue(level.getBlockState(chest).isAir(), "Containers are not ruin fragments");
        helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, new AABB(origin).inflate(8)).isEmpty(), "Ruins must not drop container items");
        helper.succeed();
    }

    @GameTest(template = "empty_test")
    public static void biomeAndDisabledAreIgnored(GameTestHelper helper) {
        var box = new BoundingBox(helper.absolutePos(new BlockPos(1, 2, 1)));
        helper.assertTrue(DungeonDestruction.create(settings(), "biome", List.of(box), List.of()) == null, "Biome must have no cleanup");
        helper.assertTrue(DungeonDestruction.create(DestructionConfig.DEFAULT, "structure", List.of(box), List.of()) == null, "Default must be disabled");
        var legacy = (net.minecraft.nbt.CompoundTag) DestructionConfig.CODEC.encodeStart(NbtOps.INSTANCE, settings()).getOrThrow();
        legacy.remove("ruin_chance"); legacy.remove("ruin_max_height");
        var upgraded = DestructionConfig.CODEC.parse(NbtOps.INSTANCE, legacy).getOrThrow();
        helper.assertTrue(upgraded.ruinChance() == 0.35 && upgraded.ruinMaxHeight() == 5, "Old settings must inherit ruin defaults");
        legacy.putDouble("ruin_chance", Double.NaN);
        helper.assertTrue(DestructionConfig.CODEC.parse(NbtOps.INSTANCE, legacy).error().isPresent(), "Non-finite ruin chance must be rejected");
        legacy.putDouble("ruin_chance", 2);
        helper.assertTrue(DestructionConfig.CODEC.parse(NbtOps.INSTANCE, legacy).error().isPresent(), "Ruin chance above 1 must be rejected");
        legacy.putDouble("ruin_chance", 0.35); legacy.putInt("ruin_max_height", 13);
        helper.assertTrue(DestructionConfig.CODEC.parse(NbtOps.INSTANCE, legacy).error().isPresent(), "Excessive ruin height must be rejected");
        helper.succeed();
    }
}

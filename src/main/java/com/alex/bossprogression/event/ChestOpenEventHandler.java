package com.alex.bossprogression.event;

import com.alex.bossprogression.boss.BossDefinition;
import com.alex.bossprogression.boss.BossProgressManager;
import com.alex.bossprogression.boss.BossRegistry;
import com.alex.bossprogression.condition.BossCondition;
import com.alex.bossprogression.condition.ChestLootedCondition;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Counts a previously unopened loot-table chest/barrel only after its menu actually opens. */
public final class ChestOpenEventHandler {
    // The loot table is consumed when vanilla constructs the menu, so capture it beforehand.
    // Keys are weak to avoid retaining disconnected players when a click does not open a menu.
    private final Map<ServerPlayer, Candidate> pending = new WeakHashMap<>();

    private record Candidate(RandomizableContainerBlockEntity blockEntity, String location, long gameTime, Set<ResourceLocation> tables) {}

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        pending.remove(player); // a failed/blocked click cannot be redeemed by a later menu
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ChestBlock) && !(state.getBlock() instanceof BarrelBlock)) return;

        RandomizableContainerBlockEntity entity = lootBearing(level, pos);
        Set<ResourceLocation> tables = new HashSet<>();
        if (entity != null) tables.add(entity.getLootTable().location());
        BlockPos keyPos = pos;
        if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            BlockPos other = pos.relative(ChestBlock.getConnectedDirection(state));
            BlockState otherState = level.getBlockState(other);
            if (otherState.is(state.getBlock()) && otherState.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
                var second = lootBearing(level, other);
                if (second != null) tables.add(second.getLootTable().location());
                if (entity == null) entity = second;
                // A double chest is one container, even if both halves have independent loot tables.
                if (other.asLong() < pos.asLong()) keyPos = other;
            }
        }
        if (entity == null) return; // player-placed ordinary chests have no pending loot table
        String location = level.dimension().location() + "|" + keyPos.getX() + "," + keyPos.getY() + "," + keyPos.getZ();
        pending.put(player, new Candidate(entity, location, level.getGameTime(), Set.copyOf(tables)));
    }

    private static RandomizableContainerBlockEntity lootBearing(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity container
                && container.getLootTable() != null) return container;
        return null;
    }

    @SubscribeEvent
    public void onContainerOpen(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Candidate candidate = pending.remove(player);
        if (candidate == null || candidate.gameTime() != player.level().getGameTime()
                || !(event.getContainer() instanceof ChestMenu menu)) return;
        Container opened = menu.getContainer();
        if (opened != candidate.blockEntity()
                && !(opened instanceof CompoundContainer doubleChest && doubleChest.contains(candidate.blockEntity()))) return;

        for (BossDefinition definition : BossRegistry.all()) {
            if (BossProgressManager.isUnlocked(player, definition.id())) continue;
            boolean hasChest = definition.conditions().stream().anyMatch(c -> c instanceof ChestLootedCondition chest
                    && (chest.lootTable().isEmpty() || candidate.tables().contains(chest.lootTable().get())));
            if (!hasChest || !BossProgressManager.markChestLooted(player, definition.id() + "|" + candidate.location())) continue;
            for (BossCondition condition : definition.conditions()) {
                if (condition instanceof ChestLootedCondition chest
                        && (chest.lootTable().isEmpty() || candidate.tables().contains(chest.lootTable().get()))) {
                    BossProgressManager.increment(player, definition.id(), chest.id(), 1);
                }
            }
        }
    }
}

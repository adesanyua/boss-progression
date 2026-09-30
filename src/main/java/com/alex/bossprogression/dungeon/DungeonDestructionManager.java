package com.alex.bossprogression.dungeon;

import com.alex.bossprogression.BossProgressionMod;
import com.alex.bossprogression.registry.ModAttachments;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Resumable, fair, per-level work queue, independent from reward delivery and player resets. */
public final class DungeonDestructionManager {
    private final Map<ServerLevel, Integer> next = new WeakHashMap<>();
    public static void onDefeated(ServerLevel level, DungeonInstance instance) {
        var cleanup = instance.destruction();
        if (cleanup == null || !cleanup.begin()) return;
        BossProgressionMod.LOGGER.info("Encounter {} destruction scheduled in {} seconds", instance.encounterId(), cleanup.delaySeconds());
        for (var player : level.players()) if (player.blockPosition().closerThan(instance.spawnPos(), 128))
            player.sendSystemMessage(Component.translatable("message.bossprogression.dungeon_collapse", cleanup.delaySeconds()));
    }
    @SubscribeEvent
    public void tick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var store = level.getData(ModAttachments.DUNGEON_STORE.get());
        var jobs = new ArrayList<>(store.instances().values().stream()
                .filter(i -> i.destruction() != null && !i.destruction().finished()).toList());
        if (jobs.isEmpty()) return;
        int start = Math.floorMod(next.getOrDefault(level, 0), jobs.size());
        int budget = 1024;
        long deadline = System.nanoTime() + 2_000_000L;
        boolean changed = false;
        for (int offset = 0; offset < jobs.size() && budget > 0 && System.nanoTime() < deadline; offset++) {
            var instance = jobs.get((start + offset) % jobs.size());
            int work = instance.destruction().tick(level, instance.defeated(), budget, deadline);
            budget -= work;
            changed |= work > 0;
        }
        next.put(level, start + 1);
        if (changed) level.setData(ModAttachments.DUNGEON_STORE.get(), store);
    }
    private static void protect(ServerLevel level, BlockPos pos) {
        var store = level.getData(ModAttachments.DUNGEON_STORE.get());
        boolean changed = false;
        for (var instance : store.instances().values())
            if (instance.destruction() != null) changed |= instance.destruction().protect(pos);
        if (changed) level.setData(ModAttachments.DUNGEON_STORE.get(), store);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void broken(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level) protect(level, event.getPos());
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void placed(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof Player) || !(event.getLevel() instanceof ServerLevel level)) return;
        protect(level, event.getPos());
        if (event instanceof BlockEvent.EntityMultiPlaceEvent multiple)
            for (var snapshot : multiple.getReplacedBlockSnapshots()) protect(level, snapshot.getPos());
    }
}

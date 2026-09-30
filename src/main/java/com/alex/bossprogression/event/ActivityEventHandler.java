package com.alex.bossprogression.event;

import com.alex.bossprogression.boss.BossProgressManager;
import com.alex.bossprogression.boss.BossRegistry;
import com.alex.bossprogression.condition.ActivityCondition;
import com.alex.bossprogression.condition.ActivityCondition.Kind;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

public final class ActivityEventHandler {
    private static void increment(ServerPlayer player, Kind kind, ResourceLocation target, int amount) {
        for (var boss : BossRegistry.all()) for (var condition : boss.conditions()) {
            if (condition instanceof ActivityCondition activity && activity.kind() == kind && activity.matches(target))
                BossProgressManager.increment(player, boss.id(), condition.id(), amount);
        }
    }
    @SubscribeEvent
    public void craft(PlayerEvent.ItemCraftedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player)
            increment(player, Kind.CRAFT_ITEM, BuiltInRegistries.ITEM.getKey(event.getCrafting().getItem()), event.getCrafting().getCount());
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void mine(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && !player.isCreative())
            increment(player, Kind.MINE_BLOCK, BuiltInRegistries.BLOCK.getKey(event.getState().getBlock()), 1);
    }
    @SubscribeEvent
    public void trade(TradeWithVillagerEvent event) {
        if (event.getEntity() instanceof ServerPlayer player)
            increment(player, Kind.TRADE, BuiltInRegistries.ITEM.getKey(event.getMerchantOffer().getResult().getItem()), 1);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void fish(ItemFishedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) for (var stack : event.getDrops())
            increment(player, Kind.FISH, BuiltInRegistries.ITEM.getKey(stack.getItem()), stack.getCount());
    }
    @SubscribeEvent
    public void poll(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0) return;
        for (var boss : BossRegistry.all()) {
            if (BossProgressManager.isUnlocked(player, boss.id())) continue;
            for (var condition : boss.conditions()) {
                if (!(condition instanceof ActivityCondition activity) || activity.target().isEmpty()) continue;
                var target = activity.target().get();
                boolean satisfied = switch (activity.kind()) {
                    case OBTAIN_ITEM -> BuiltInRegistries.ITEM.containsKey(target)
                            && player.getInventory().countItem(BuiltInRegistries.ITEM.get(target)) >= activity.requiredCount();
                    case VISIT_BIOME -> player.level().getBiome(player.blockPosition()).is(target);
                    case VISIT_DIMENSION -> player.level().dimension().location().equals(target);
                    case ADVANCEMENT -> {
                        var advancement = player.server.getAdvancements().get(target);
                        yield advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
                    }
                    case DEFEAT_BOSS -> BossProgressManager.state(player, target).map(s -> s.defeated()).orElse(false);
                    default -> false;
                };
                if (satisfied) BossProgressManager.increment(player, boss.id(), condition.id(), activity.requiredCount());
            }
        }
    }
}

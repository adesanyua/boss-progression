package com.alex.bossprogression.boss;

import com.alex.bossprogression.BossProgressionMod;
import com.alex.bossprogression.condition.BossCondition;
import com.alex.bossprogression.condition.ActivityCondition;
import net.minecraft.core.registries.BuiltInRegistries;
import com.alex.bossprogression.dungeon.DungeonManager;
import com.alex.bossprogression.registry.ModAttachments;
import java.util.Optional;
import java.util.UUID;
import java.util.Objects;
import com.alex.bossprogression.dungeon.DungeonInstance;
import com.alex.bossprogression.reward.BossReward;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * The single authoritative entry point for changing player boss progress.
 *
 * <p>Event handlers must not mutate progress themselves: they resolve a {@link ServerPlayer} and a
 * matched condition, then call {@link #increment}. Unlock (and the one-shot dungeon generation that
 * follows it, added in MVP3) is coordinated here so it happens exactly once, guarded against
 * re-entrancy by the persisted {@code unlocked} flag.
 *
 * <p>All methods run server-side only. The backing {@link BossProgressData} is a persistent,
 * copy-on-death attachment, so progress survives relog, server restart and player death.
 */
public final class BossProgressManager {
    private BossProgressManager() {
    }

    /** Current progress value for one condition of one boss. */
    public static int getProgress(ServerPlayer player, ResourceLocation bossId, String conditionId) {
        return data(player).getOrCreate(bossId).progress(conditionId);
    }

    public static Optional<BossState> state(ServerPlayer player, ResourceLocation bossId) {
        return Optional.ofNullable(data(player).get(bossId));
    }

    public static boolean isUnlocked(ServerPlayer player, ResourceLocation bossId) {
        BossState state = data(player).get(bossId);
        return state != null && state.unlocked();
    }

    /**
     * Adds {@code amount} to a condition counter and attempts to unlock the boss.
     * Progress is capped at the condition's required count. No-op if the boss/condition is unknown
     * or the encounter is already unlocked and not repeatable.
     */
    public static void increment(ServerPlayer player, ResourceLocation bossId, String conditionId, int amount) {
        BossDefinition definition = BossRegistry.get(bossId);
        if (definition == null) {
            return;
        }
        var condition = definition.condition(conditionId);
        if (condition == null || amount <= 0) {
            return;
        }
        BossProgressData data = data(player);
        BossState state = data.getOrCreate(bossId);
        if (state.unlocked() || state.defeated()) {
            return;
        }
        int current = state.progress(conditionId);
        int next = (int) Math.min((long) current + amount, condition.requiredCount());
        if (next == current) {
            return; // already capped, nothing changed
        }
        state.setProgress(conditionId, next);
        markDirty(player, data);
        BossProgressionMod.LOGGER.debug("progress {} / {} for boss {} condition '{}' (player {})",
                next, condition.requiredCount(), bossId, conditionId, player.getGameProfile().getName());
        tryUnlock(player, definition);
    }

    /**
     * Marks the boss READY when every condition is satisfied. Idempotent: an already-unlocked,
     * non-repeatable encounter is never unlocked twice, which also protects against two events
     * triggering unlock in the same tick.
     *
     * @return true if this call caused the transition to unlocked
     */
    public static boolean tryUnlock(ServerPlayer player, BossDefinition definition) {
        BossProgressData data = data(player);
        BossState state = data.getOrCreate(definition.id());
        if (state.unlocked()) {
            return false;
        }
        if (!definition.allConditionsMet(state)) {
            return false;
        }
        state.setUnlocked(true);
        state.setEncounterId(UUID.randomUUID());
        markDirty(player, data);
        BossProgressionMod.LOGGER.info("Boss {} unlocked for {}", definition.id(), player.getGameProfile().getName());
        // Dungeon is generated exactly once, here, right after the (already idempotent) unlock transition.
        DungeonManager.onUnlocked(player, definition);
        return true;
    }

    /** Force-set progress for one condition (used by debug commands). */
    public static void setProgress(ServerPlayer player, ResourceLocation bossId, String conditionId, int value) {
        BossDefinition definition = BossRegistry.get(bossId);
        if (definition == null || definition.condition(conditionId) == null) {
            return;
        }
        BossProgressData data = data(player);
        data.getOrCreate(bossId).setProgress(conditionId, Math.max(0, value));
        markDirty(player, data);
        tryUnlock(player, definition);
    }

    /** Only the active cycle at the recorded dungeon can be completed. */
    public static void markDefeated(ServerPlayer player, DungeonInstance instance) {
        BossProgressData data = data(player);
        BossState state = data.get(instance.bossId());
        if (state == null || !state.unlocked() || !state.dungeonGenerated()
                || !Objects.equals(state.encounterId(), instance.encounterId())
                || !Objects.equals(state.dungeonPos(), instance.pos())
                || !Objects.equals(state.dungeonDimension(), instance.dimension())) return;
        if (!state.defeated()) {
            state.setDefeated(true);
            markDirty(player, data);
        }
        grantPending(player, instance.bossId(), state, data);
    }

    private static void grantPending(ServerPlayer player, ResourceLocation id, BossState state, BossProgressData data) {
        if (!state.defeated() || state.rewardGranted()) return;
        BossDefinition definition = BossRegistry.get(id);
        if (definition == null) return;
        // Commit claim before side effects to guard against reentrant callbacks.
        state.setRewardGranted(true);
        markDirty(player, data);
        for (BossReward reward : definition.rewards()) {
            try { reward.grant(player); }
            catch (RuntimeException e) { BossProgressionMod.LOGGER.error("Reward {} failed for {}", reward.type(), player.getUUID(), e); }
        }
    }

    public static void reconcile(ServerPlayer player) {
        for (var level : player.server.getAllLevels()) {
            var store = level.getData(ModAttachments.DUNGEON_STORE.get());
            for (DungeonInstance instance : store.instances().values()) {
                if (instance.defeated() && instance.owner().equals(player.getUUID())) markDefeated(player, instance);
            }
        }
    }

    public static boolean requestReset(ServerPlayer player, ResourceLocation id) {
        BossDefinition definition = BossRegistry.get(id);
        BossState state = data(player).get(id);
        if (definition == null || !definition.repeatable() || state == null || !state.defeated() || !state.rewardGranted()) return false;
        for (var level : player.server.getAllLevels()) {
            for (DungeonInstance instance : level.getData(ModAttachments.DUNGEON_STORE.get()).instances().values()) {
                if (instance.owner().equals(player.getUUID()) && instance.bossId().equals(id)
                        && Objects.equals(instance.encounterId(), state.encounterId()) && !instance.defeated()) return false;
            }
        }
        reset(player, id);
        return true;
    }

    /**
     * Records a chest location as newly looted. Returns true only the first time this exact chest is
     * seen (first-open semantics, so reopening the same container never counts again). The set is
     * bounded ({@link BossProgressData#LOOTED_CHEST_BOUND}) so storage stays finite; once full, new
     * chests stop being counted rather than growing the attachment without limit.
     */
    public static boolean markChestLooted(ServerPlayer player, String chestKey) {
        BossProgressData data = data(player);
        if (data.lootedChests().contains(chestKey) || data.lootedChests().size() >= BossProgressData.LOOTED_CHEST_BOUND) {
            return false;
        }
        data.lootedChests().add(chestKey);
        markDirty(player, data);
        return true;
    }

    /** Whether the dungeon for this boss has already been generated for the player (once-guard). */
    public static boolean isDungeonGenerated(ServerPlayer player, ResourceLocation bossId) {
        BossState state = data(player).get(bossId);
        return state != null && state.dungeonGenerated();
    }

    /**
     * Records a successfully generated dungeon (dimension + position). Called by
     * {@link DungeonManager} only after generation actually succeeded, so a failed locate/generate
     * leaves the flag unset and the encounter stays retryable.
     */
    public static void setDungeon(ServerPlayer player, ResourceLocation bossId,
                                  ResourceKey<Level> dimension, BlockPos pos) {
        BossProgressData data = data(player);
        data.getOrCreate(bossId).setDungeon(dimension, pos);
        markDirty(player, data);
        BossProgressionMod.LOGGER.info("Dungeon for boss {} generated at {} in {} (player {})",
                bossId, pos.toShortString(), dimension.location(), player.getGameProfile().getName());
    }

    /** Whether the journal has already been handed out to this player at least once (ever). */
    public static boolean journalReceived(ServerPlayer player) {
        return data(player).journalReceived();
    }

    /**
     * Debug/dev helper (Stage 12): fills every condition of a boss to its required count and runs the
     * normal unlock path, so the whole pipeline (unlock -> dungeon -> invitation) can be exercised from
     * a command without grinding. Goes through {@link #tryUnlock}, so it stays exactly-once.
     */
    public static boolean forceUnlock(ServerPlayer player, ResourceLocation bossId) {
        BossDefinition definition = BossRegistry.get(bossId);
        if (definition == null) {
            return false;
        }
        BossProgressData data = data(player);
        BossState state = data.getOrCreate(bossId);
        if (state.unlocked() || state.defeated()) return false;
        for (BossCondition condition : definition.conditions()) {
            state.setProgress(condition.id(), condition.requiredCount());
        }
        markDirty(player, data);
        return tryUnlock(player, definition);
    }

    /** Donate only the outstanding count; the client supplies no item/count/owner data. */
    public static void submitItems(ServerPlayer player, ResourceLocation id) {
        BossDefinition boss = BossRegistry.get(id);
        if (boss == null || isUnlocked(player, id)) return;
        for (BossCondition condition : boss.conditions()) {
            if (!(condition instanceof ActivityCondition activity)
                    || activity.kind() != ActivityCondition.Kind.SUBMIT_ITEM || activity.target().isEmpty()) continue;
            var target = activity.target().get();
            if (!BuiltInRegistries.ITEM.containsKey(target)) continue;
            int needed = activity.requiredCount() - getProgress(player, id, activity.id());
            if (needed <= 0) continue;
            var item = BuiltInRegistries.ITEM.get(target);
            int remaining = Math.min(needed, player.getInventory().countItem(item));
            int submitted = remaining;
            for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
                var stack = player.getInventory().getItem(slot);
                if (!stack.is(item)) continue;
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
            }
            if (submitted > 0) {
                player.getInventory().setChanged();
                increment(player, id, activity.id(), submitted);
            }
        }
        player.containerMenu.broadcastChanges();
    }

    /** Debug/dev helper (Stage 12): wipes one boss's progress for this player. */
    public static void reset(ServerPlayer player, ResourceLocation bossId) {
        BossProgressData data = data(player);
        data.remove(bossId);
        data.lootedChests().removeIf(key -> key.startsWith(bossId + "|"));
        markDirty(player, data);
    }

    /** Debug/dev helper (Stage 12): wipes all boss progress for this player (journal flag is kept). */
    public static void resetAll(ServerPlayer player) {
        BossProgressData data = data(player);
        data.bosses().clear();
        data.lootedChests().clear();
        markDirty(player, data);
    }

    /**
     * Flips the one-shot journal flag. The flag lives on the immutable {@link BossProgressData}
     * header, so this replaces the attachment value (the per-boss {@link BossState} objects are kept
     * by reference). Returns true if this call performed the first-time transition.
     */
    public static boolean markJournalReceived(ServerPlayer player) {
        BossProgressData current = data(player);
        if (current.journalReceived()) {
            return false;
        }
        player.setData(ModAttachments.BOSS_PROGRESS.get(), current.withJournalReceived(true));
        return true;
    }

    private static BossProgressData data(ServerPlayer player) {
        return player.getData(ModAttachments.BOSS_PROGRESS.get());
    }

    /**
     * Reassigning the (same) object marks the entity dirty so the attachment is written to disk.
     * Required because we mutate the state in place rather than replacing the whole object.
     */
    private static void markDirty(ServerPlayer player, BossProgressData data) {
        player.setData(ModAttachments.BOSS_PROGRESS.get(), data);
    }
}

package com.alex.bossprogression.dungeon;

import com.alex.bossprogression.BossProgressionMod;
import com.alex.bossprogression.boss.BossDefinition;
import com.alex.bossprogression.boss.BossProgressManager;
import com.alex.bossprogression.item.InvitationData;
import com.alex.bossprogression.item.InvitationItem;
import com.alex.bossprogression.registry.ModAttachments;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Coordinates dungeon generation after a boss unlocks. Runs entirely server-side and is the only
 * place a dungeon is created.
 *
 * <p>Ordering (Stage 7): locate &rarr; generate &rarr; create {@link DungeonInstance} &rarr; persist
 * coordinates on the player. The once-guard is {@link BossProgressManager#isDungeonGenerated}: if the
 * dungeon already exists the call returns immediately. If locating or generating fails the flag is
 * left unset so the encounter stays retryable (a later unlock trigger, e.g. relog, can run it again).
 */
public final class DungeonManager {
    private DungeonManager() {
    }

    public static void onUnlocked(ServerPlayer player, BossDefinition definition) {
        if (!BossProgressManager.isUnlocked(player, definition.id())) return;
        if (definition.dungeon() == null) {
            BossProgressionMod.LOGGER.warn("Boss {} has no dungeon config; skipping generation", definition.id());
            return;
        }
        if (BossProgressManager.isDungeonGenerated(player, definition.id())) {
            return; // already generated exactly once
        }

        DungeonConfig config = definition.dungeon();
        ServerLevel level = resolveLevel(player.server, config.dimension(), player);
        if (level == null) {
            BossProgressionMod.LOGGER.warn("Cannot resolve dimension {} for boss {}; retryable", config.dimension().location(), definition.id());
            return;
        }

        // Anchor the search on the owner when they are already in the target dimension,
        // otherwise on that dimension's spawn point. (MVP3 test boss: owner is in the overworld.)
        BlockPos anchor = level.dimension() == player.level().dimension()
                ? player.blockPosition()
                : level.getSharedSpawnPos();

        Optional<EncounterPlacement.Site> prepared = EncounterPlacement.prepare(level, anchor, definition);
        if (prepared.isEmpty()) {
            BossProgressionMod.LOGGER.warn("No available {} site for boss {} (player {}); retry with /bossprogress generate after fixing configuration",
                    config.mode(), definition.id(), player.getGameProfile().getName());
            return;
        }
        var site = prepared.get();
        registerInstance(level, new DungeonInstance(player.getUUID(), definition.id(), level.dimension(), site.pos(),
                BossProgressManager.state(player, definition.id()).orElseThrow().encounterId(),
                site.spawn(), config.activationRadius(), site.key(), site.destruction()));
        BossProgressManager.setDungeon(player, definition.id(), level.dimension(), site.pos());
        giveInvitation(player, definition.id(), level.dimension(), site.pos());
    }

    /**
     * Stage 7 final step: hand out the invitation encoding the real dungeon coordinates. Runs exactly
     * once because it is only reached from the once-guarded {@link #onUnlocked}. Coordinates live in
     * the stack's data component, not in name/lore. The player is never teleported.
     */
    private static void giveInvitation(ServerPlayer player, ResourceLocation bossId,
                                       ResourceKey<Level> dimension, BlockPos pos) {
        ItemStack stack = InvitationItem.create(new InvitationData(bossId, dimension, pos));
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        BossProgressionMod.LOGGER.info("Invitation for boss {} given to {}",
                bossId, player.getGameProfile().getName());
    }

    private static ServerLevel resolveLevel(MinecraftServer server, ResourceKey<Level> key, ServerPlayer player) {
        return server.getLevel(key);
    }

    private static void registerInstance(ServerLevel level, DungeonInstance instance) {
        DungeonInstanceStore store = level.getData(ModAttachments.DUNGEON_STORE.get());
        store.put(instance);
        level.setData(ModAttachments.DUNGEON_STORE.get(), store);
    }
}

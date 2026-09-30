package com.alex.bossprogression.event;

import com.alex.bossprogression.BossProgressionMod;
import com.alex.bossprogression.boss.BossProgressManager;
import com.alex.bossprogression.registry.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Server-side player lifecycle handling. The Boss Journal is handed out exactly once per player
 * (tracked by a persistent flag), never on every relog. After death the journal is restored to the
 * respawned player only if it was already earned and is missing from the inventory.
 */
public final class PlayerEventHandler {

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        // Fires only on the server; giveJournal itself re-checks the side defensively.
        giveJournal(event.getEntity(), false);
        if (event.getEntity() instanceof ServerPlayer player) BossProgressManager.reconcile(player);
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        // Restore the journal after death if it was lost (flag already set), without granting a first-ever one.
        giveJournal(event.getEntity(), true);
        if (event.getEntity() instanceof ServerPlayer player) BossProgressManager.reconcile(player);
    }

    private void giveJournal(Player playerEntity, boolean restoreOnly) {
        if (!(playerEntity instanceof ServerPlayer player) || player.level().isClientSide()) {
            return;
        }
        boolean alreadyReceived = BossProgressManager.journalReceived(player);

        if (restoreOnly) {
            if (alreadyReceived && !hasJournal(player)) {
                handJournal(player);
            }
            return;
        }

        // First entry into the world: grant once, then never again on subsequent logins.
        if (!alreadyReceived && BossProgressManager.markJournalReceived(player)) {
            handJournal(player);
            BossProgressionMod.LOGGER.info("Granted Boss Journal to {} (first entry)", player.getGameProfile().getName());
        }
    }

    private static boolean hasJournal(ServerPlayer player) {
        return player.getInventory().countItem(ModItems.BOSS_JOURNAL.get()) > 0;
    }

    private static void handJournal(ServerPlayer player) {
        ItemStack stack = new ItemStack(ModItems.BOSS_JOURNAL.get());
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }
}

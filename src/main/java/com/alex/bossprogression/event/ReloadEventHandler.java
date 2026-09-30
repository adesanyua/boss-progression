package com.alex.bossprogression.event;

import com.alex.bossprogression.data.BossDefinitionLoader;
import com.alex.bossprogression.network.SyncJournalDefinitions;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/**
 * Attaches the boss JSON loader to the server's datapack reload pipeline. {@link AddReloadListenerEvent}
 * fires on the game bus during world load and on {@code /reload}, so boss content is (re)read from the
 * active datapacks exactly where the authoritative server logic runs.
 */
public final class ReloadEventHandler {

    @SubscribeEvent
    public void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new BossDefinitionLoader());
    }

    @SubscribeEvent
    public void syncDefinitions(OnDatapackSyncEvent event) {
        event.getRelevantPlayers().forEach(SyncJournalDefinitions::sendTo);
    }
}

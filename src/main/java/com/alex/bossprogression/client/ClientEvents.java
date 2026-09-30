package com.alex.bossprogression.client;

import com.alex.bossprogression.data.BossDefinitionLoader;
import com.alex.bossprogression.boss.BossRegistry;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import java.util.function.Consumer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

/**
 * Client-only wiring. Kept in its own class so client classes are never loaded on a dedicated server;
 * {@link #init} is called only when {@code Dist == CLIENT}.
 *
 * <p>Resources populate only a fallback Journal cache; connected clients use server-synchronized rules.
 * Resource reloads cannot overwrite authoritative rules on an integrated server.
 */
public final class ClientEvents {

    private ClientEvents() {
    }

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener((Consumer<RegisterClientReloadListenersEvent>)
                event -> event.registerReloadListener(new BossDefinitionLoader(false)));
        NeoForge.EVENT_BUS.addListener((Consumer<ClientPlayerNetworkEvent.LoggingOut>) event -> BossRegistry.clearClientSync());
    }
}

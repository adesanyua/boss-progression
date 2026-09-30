package com.alex.bossprogression.event;

import com.alex.bossprogression.command.BossProgressCommand;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Registers the mod's Brigadier commands on the server command dispatcher. {@link RegisterCommandsEvent}
 * fires on the game bus during server startup, which is the server-authoritative place to add commands
 * (a client cannot influence this).
 */
public final class CommandRegistryHandler {

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        BossProgressCommand.register(event.getDispatcher());
    }
}

package com.alex.bossprogression;

import com.alex.bossprogression.client.ClientEvents;
import com.alex.bossprogression.dungeon.BossSpawnManager;
import com.alex.bossprogression.dungeon.DungeonDestructionManager;
import com.alex.bossprogression.event.ChestOpenEventHandler;
import com.alex.bossprogression.event.CommandRegistryHandler;
import com.alex.bossprogression.event.KillEventHandler;
import com.alex.bossprogression.event.PlayerEventHandler;
import com.alex.bossprogression.event.ReloadEventHandler;
import com.alex.bossprogression.registry.ModAttachments;
import com.alex.bossprogression.registry.ModComponents;
import com.alex.bossprogression.registry.ModItems;
import com.alex.bossprogression.network.RequestBossReset;
import com.alex.bossprogression.network.RequestItemSubmission;
import com.alex.bossprogression.network.SyncJournalDefinitions;
import com.alex.bossprogression.config.BossConfigFiles;
import com.alex.bossprogression.event.ActivityEventHandler;
import com.alex.bossprogression.event.BossDamageEventHandler;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * Mod entrypoint. Wires registries and server-side event handlers.
 *
 * <p>MVP1 scope: a single hardcoded test boss (kill 5 zombies) whose progress is stored in a
 * persistent player attachment and mutated only through {@link com.alex.bossprogression.boss.BossProgressManager}.
 * Journal item, dungeon generation, invitations and the JSON loader are added in later MVPs.
 */
@Mod(BossProgressionMod.MODID)
public class BossProgressionMod {
    public static final String MODID = "bossprogression";
    public static final Logger LOGGER = LogUtils.getLogger();

    public BossProgressionMod(IEventBus modEventBus, ModContainer modContainer) {
        // Registry objects (attachments, items, data components) register on the mod bus.
        ModAttachments.register(modEventBus);
        ModItems.register(modEventBus);
        ModComponents.register(modEventBus);
        modEventBus.addListener(RequestBossReset::register);
        modEventBus.addListener(RequestItemSubmission::register);
        modEventBus.addListener(SyncJournalDefinitions::register);

        // Gameplay events fire on the NeoForge game bus and are server-authoritative.
        NeoForge.EVENT_BUS.register(new KillEventHandler());
        NeoForge.EVENT_BUS.register(new ActivityEventHandler());
        NeoForge.EVENT_BUS.register(new BossDamageEventHandler());
        NeoForge.EVENT_BUS.register(new ChestOpenEventHandler());
        NeoForge.EVENT_BUS.register(new PlayerEventHandler());
        NeoForge.EVENT_BUS.register(new BossSpawnManager());
        NeoForge.EVENT_BUS.register(new DungeonDestructionManager());
        // Boss content is data-driven: loaded from data/<ns>/bosses/*.json on datapack reload (MVP6).
        NeoForge.EVENT_BUS.register(new ReloadEventHandler());
        // Developer/testing commands (Stage 12 / MVP8).
        NeoForge.EVENT_BUS.register(new CommandRegistryHandler());
        // The client also needs the definitions for journal display; only touch client classes on client.
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientEvents.init(modEventBus);
        }

        BossConfigFiles.initialize();
        LOGGER.info("Boss Progression {} initialized.", MODID);
    }
}

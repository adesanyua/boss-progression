package com.alex.bossprogression.registry;

import com.alex.bossprogression.BossProgressionMod;
import com.alex.bossprogression.item.BossJournalItem;
import com.alex.bossprogression.item.InvitationItem;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registers the mod's items. Kept intentionally small: the journal and invitation are the only
 * items so far. Item models/textures are optional and can be added as resources later.
 */
public final class ModItems {
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(BossProgressionMod.MODID);

    public static final DeferredItem<BossJournalItem> BOSS_JOURNAL =
            ITEMS.registerItem("boss_journal",
                    props -> new BossJournalItem(props.stacksTo(1).rarity(Rarity.RARE)));

    public static final DeferredItem<InvitationItem> INVITATION =
            ITEMS.registerItem("invitation",
                    props -> new InvitationItem(props.stacksTo(1)));

    private ModItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}

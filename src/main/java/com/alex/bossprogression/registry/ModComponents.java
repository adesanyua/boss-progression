package com.alex.bossprogression.registry;

import com.alex.bossprogression.BossProgressionMod;
import com.alex.bossprogression.item.InvitationData;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registers the mod's {@link DataComponentType}s. Item stack data (boss id, dimension, position)
 * lives here rather than in display name/lore, so it is a reliable authoritative source on both sides.
 */
public final class ModComponents {
    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, BossProgressionMod.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<InvitationData>> INVITATION =
            DATA_COMPONENTS.registerComponentType("invitation",
                    builder -> builder
                            .persistent(InvitationData.CODEC)
                            .networkSynchronized(InvitationData.STREAM_CODEC));

    private ModComponents() {
    }

    public static void register(IEventBus modEventBus) {
        DATA_COMPONENTS.register(modEventBus);
    }
}

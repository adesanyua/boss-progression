package com.alex.bossprogression.reward;

import com.mojang.serialization.MapCodec;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

public final class RewardRegistry {
    private static final Map<ResourceLocation, MapCodec<? extends BossReward>> CODECS = new HashMap<>();
    private RewardRegistry() {}
    public static void register(ResourceLocation id, MapCodec<? extends BossReward> codec) { CODECS.put(id, codec); }
    public static MapCodec<? extends BossReward> byDiscriminator(String raw) {
        MapCodec<? extends BossReward> codec = CODECS.get(ResourceLocation.tryParse(raw));
        if (codec == null) throw new IllegalStateException("Unknown reward type: " + raw);
        return codec;
    }
    public static void registerBuiltIns() {
        register(ItemReward.TYPE, ItemReward.CODEC);
        register(ExperienceReward.TYPE, ExperienceReward.CODEC);
    }
}

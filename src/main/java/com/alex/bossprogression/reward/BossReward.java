package com.alex.bossprogression.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public interface BossReward {
    Codec<BossReward> CODEC = Codec.STRING.<BossReward>dispatch("type",
            r -> r.type().toString(), (Function<String, MapCodec<? extends BossReward>>) RewardRegistry::byDiscriminator);

    ResourceLocation type();
    String label();
    void grant(ServerPlayer player);
}

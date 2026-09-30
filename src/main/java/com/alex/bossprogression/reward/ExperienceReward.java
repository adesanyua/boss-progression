package com.alex.bossprogression.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public record ExperienceReward(int amount) implements BossReward {
    public static final ResourceLocation TYPE = ResourceLocation.fromNamespaceAndPath("bossprogression", "experience");
    public static final MapCodec<ExperienceReward> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.intRange(0, 1000000).fieldOf("amount").forGetter(ExperienceReward::amount)
    ).apply(inst, ExperienceReward::new));
    @Override public ResourceLocation type() { return TYPE; }
    @Override public String label() { return amount + " XP"; }
    @Override public void grant(ServerPlayer player) { player.giveExperiencePoints(amount); }
}

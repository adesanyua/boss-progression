package com.alex.bossprogression.reward;

import com.alex.bossprogression.BossProgressionMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public record ItemReward(ResourceLocation item, int count) implements BossReward {
    public static final ResourceLocation TYPE = ResourceLocation.fromNamespaceAndPath("bossprogression", "item");
    public static final MapCodec<ItemReward> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            ResourceLocation.CODEC.fieldOf("item").forGetter(ItemReward::item),
            Codec.intRange(1, 2304).fieldOf("count").forGetter(ItemReward::count)
    ).apply(inst, ItemReward::new));
    @Override public ResourceLocation type() { return TYPE; }
    @Override public String label() { return count + "x " + item.getPath().replace('_', ' '); }
    @Override public void grant(ServerPlayer player) {
        if (!BuiltInRegistries.ITEM.containsKey(item)) {
            BossProgressionMod.LOGGER.warn("Unknown reward item {}", item);
            return;
        }
        var resolved = BuiltInRegistries.ITEM.get(item);
        int remaining = count;
        while (remaining > 0) {
            int batch = Math.min(remaining, resolved.getDefaultMaxStackSize());
            ItemStack stack = new ItemStack(resolved, batch);
            player.getInventory().add(stack);
            if (!stack.isEmpty()) player.drop(stack, false);
            remaining -= batch;
        }
    }
}

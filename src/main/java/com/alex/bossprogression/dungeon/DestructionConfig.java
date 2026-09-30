package com.alex.bossprogression.dungeon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

/** Opt-in settings, captured in the encounter so reloads cannot change a running cleanup. */
public record DestructionConfig(boolean destroyAfterDefeat, int delayTicks, int blocksPerTick, ResourceLocation structureBlockTag,
                                int preserveBottomLayers, ResourceLocation protectedBlockTag, double ruinChance, int ruinMaxHeight) {
    public static final DestructionConfig DEFAULT = new DestructionConfig(false, 200, 32,
            ResourceLocation.fromNamespaceAndPath("bossprogression", "structure_cleanup"), 1,
            ResourceLocation.fromNamespaceAndPath("bossprogression", "protected_terrain"), 0.35, 5);
    public static final Codec<DestructionConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.optionalFieldOf("destroy_after_defeat", false).forGetter(DestructionConfig::destroyAfterDefeat),
            Codec.intRange(0, 72000).optionalFieldOf("delay_ticks", 200).forGetter(DestructionConfig::delayTicks),
            Codec.intRange(1, 128).optionalFieldOf("blocks_per_tick", 32).forGetter(DestructionConfig::blocksPerTick),
            ResourceLocation.CODEC.optionalFieldOf("structure_block_tag", DEFAULT.structureBlockTag()).forGetter(DestructionConfig::structureBlockTag),
            Codec.intRange(1, 16).optionalFieldOf("preserve_bottom_layers", 1).forGetter(DestructionConfig::preserveBottomLayers),
            ResourceLocation.CODEC.optionalFieldOf("protected_block_tag", DEFAULT.protectedBlockTag()).forGetter(DestructionConfig::protectedBlockTag),
            Codec.DOUBLE.validate(v -> Double.isFinite(v) && v >= 0 && v <= 1 ? DataResult.success(v)
                    : DataResult.error(() -> "ruin_chance must be finite and between 0 and 1"))
                    .optionalFieldOf("ruin_chance", DEFAULT.ruinChance()).forGetter(DestructionConfig::ruinChance),
            Codec.intRange(1, 12).optionalFieldOf("ruin_max_height", DEFAULT.ruinMaxHeight()).forGetter(DestructionConfig::ruinMaxHeight)
    ).apply(i, DestructionConfig::new));
}

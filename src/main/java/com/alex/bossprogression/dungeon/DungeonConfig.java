package com.alex.bossprogression.dungeon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/** Placement configuration. Old definitions default to the legacy procedural arena. */
public record DungeonConfig(ResourceKey<Level> dimension, String template, int minDistance, int maxDistance,
                            int size, String mode, Optional<ResourceLocation> structure,
                            Optional<ResourceLocation> biome, Optional<String> nbt, BlockPos spawnOffset,
                            int activationRadius, int searchRadius, DestructionConfig destruction) {
    public static final Codec<DungeonConfig> CODEC = RecordCodecBuilder.<DungeonConfig>create(i -> i.group(
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(DungeonConfig::dimension),
            Codec.STRING.optionalFieldOf("template", "test_room").forGetter(DungeonConfig::template),
            Codec.intRange(0, 8192).optionalFieldOf("min_distance", 32).forGetter(DungeonConfig::minDistance),
            Codec.intRange(1, 8192).optionalFieldOf("max_distance", 64).forGetter(DungeonConfig::maxDistance),
            Codec.intRange(3, 128).optionalFieldOf("size", 11).forGetter(DungeonConfig::size),
            Codec.STRING.optionalFieldOf("mode", "procedural").forGetter(DungeonConfig::mode),
            ResourceLocation.CODEC.optionalFieldOf("structure").forGetter(DungeonConfig::structure),
            ResourceLocation.CODEC.optionalFieldOf("biome").forGetter(DungeonConfig::biome),
            Codec.STRING.optionalFieldOf("nbt").forGetter(DungeonConfig::nbt),
            BlockPos.CODEC.optionalFieldOf("spawn_offset", BlockPos.ZERO).forGetter(DungeonConfig::spawnOffset),
            Codec.intRange(4, 128).optionalFieldOf("activation_radius", 24).forGetter(DungeonConfig::activationRadius),
            Codec.intRange(1, 128).optionalFieldOf("search_radius_chunks", 64).forGetter(DungeonConfig::searchRadius),
            DestructionConfig.CODEC.optionalFieldOf("destruction", DestructionConfig.DEFAULT).forGetter(DungeonConfig::destruction)
    ).apply(i, DungeonConfig::new)).validate(c -> {
        if (c.minDistance >= c.maxDistance) return DataResult.error(() -> "min_distance must be less than max_distance");
        boolean valid = switch (c.mode) {
            case "procedural" -> true;
            case "structure" -> c.structure.isPresent();
            case "biome" -> c.biome.isPresent();
            case "nbt" -> c.nbt.filter(p -> p.matches("[a-zA-Z0-9_./-]+\\.nbt") && !p.contains("..") && !p.startsWith("/")).isPresent();
            default -> false;
        };
        return valid ? DataResult.success(c) : DataResult.error(() -> "Invalid dungeon mode or missing/unsafe placement target");
    });
    public int halfSize() { return size / 2; }
}

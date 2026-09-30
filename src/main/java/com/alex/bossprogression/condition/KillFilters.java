package com.alex.bossprogression.condition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** All specified filters must match at the victim's death position. */
public record KillFilters(Optional<ResourceLocation> dimension, Optional<ResourceLocation> biome,
                          Optional<String> time, Optional<ResourceLocation> weaponTag) {
    public static final KillFilters NONE = new KillFilters(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    private static final Codec<String> TIME = Codec.STRING.validate(value ->
            value.equals("day") || value.equals("night") ? com.mojang.serialization.DataResult.success(value)
                    : com.mojang.serialization.DataResult.error(() -> "time must be day or night"));
    public static final Codec<KillFilters> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.optionalFieldOf("dimension").forGetter(KillFilters::dimension),
            ResourceLocation.CODEC.optionalFieldOf("biome").forGetter(KillFilters::biome),
            TIME.optionalFieldOf("time").forGetter(KillFilters::time),
            ResourceLocation.CODEC.optionalFieldOf("weapon_tag").forGetter(KillFilters::weaponTag)
    ).apply(i, KillFilters::new));

    public boolean matches(LivingEntity victim, ServerPlayer killer, DamageSource source) {
        if (dimension.isPresent() && !dimension.get().equals(victim.level().dimension().location())) return false;
        if (biome.isPresent() && !victim.level().getBiome(victim.blockPosition()).is(biome.get())) return false;
        long clock = Math.floorMod(victim.level().getDayTime(), 24000L);
        if (time.isPresent() && !(time.get().equals("night") ? clock >= 13000 && clock < 23000 : clock < 13000 || clock >= 23000)) return false;
        // Weapon filters apply to direct melee only, never to whatever the shooter now holds.
        return weaponTag.isEmpty() || source.getDirectEntity() == killer
                && killer.getMainHandItem().is(TagKey.create(Registries.ITEM, weaponTag.get()));
    }

    public String description() {
        return dimension.map(v -> "Dimension: " + v).orElse("")
                + biome.map(v -> " Biome: " + v).orElse("")
                + time.map(v -> " Time: " + v).orElse("")
                + weaponTag.map(v -> " Melee weapon: #" + v).orElse("");
    }
}

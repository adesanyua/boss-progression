package com.alex.bossprogression.condition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

/**
 * Condition satisfied by killing any entity whose type belongs to an entity-type {@link TagKey}
 * (MVP7). Used for "kill N illagers": the {@code minecraft:raiders}/{@code minecraft:illager} family
 * is expressed as a tag rather than a hard-coded entity list, so it also picks up modded entities that
 * join the tag. Matching itself happens in the kill event handler; this class only carries the tag.
 */
public final class KillTagCondition extends BossCondition {
    public static final ResourceLocation TYPE =
            ResourceLocation.fromNamespaceAndPath("bossprogression", "kill_entity_tag");

    /** JSON shape: {@code {"id":..., "tag":..., "required_count":...}} (the {@code type} key is consumed by dispatch). */
    public static final MapCodec<KillTagCondition> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.STRING.fieldOf("id").forGetter(BossCondition::id),
            ResourceLocation.CODEC.fieldOf("tag").forGetter(KillTagCondition::tagId),
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf("required_count").forGetter(BossCondition::requiredCount),
            KillFilters.CODEC.optionalFieldOf("filters", KillFilters.NONE).forGetter(KillTagCondition::filters)
    ).apply(inst, KillTagCondition::new));

    private final ResourceLocation tagId;
    private final KillFilters filters;
    public KillFilters filters() { return filters; }

    public KillTagCondition(String id, ResourceLocation tagId, int requiredCount, KillFilters filters) {
        super(id, requiredCount);
        this.tagId = tagId;
        this.filters = filters;
    }

    public ResourceLocation tagId() {
        return tagId;
    }

    public TagKey<EntityType<?>> tag() {
        return TagKey.create(Registries.ENTITY_TYPE, tagId);
    }

    @Override
    public ResourceLocation type() {
        return TYPE;
    }
}

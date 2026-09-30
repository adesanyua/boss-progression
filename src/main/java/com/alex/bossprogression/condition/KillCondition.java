package com.alex.bossprogression.condition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

/**
 * Condition satisfied by killing a specific entity type.
 * <p>Later stages add {@code kill_entity_tag} (matches by entity type tag), loot-chest,
 * advancement and obtain-item conditions. Matching logic itself lives in the event handlers;
 * this class only carries the target and the required count.
 */
public final class KillCondition extends BossCondition {
    public static final ResourceLocation TYPE =
            ResourceLocation.fromNamespaceAndPath("bossprogression", "kill_entity");

    /** JSON shape: {@code {"id":..., "entity":..., "required_count":...}} (the {@code type} key is consumed by dispatch). */
    public static final MapCodec<KillCondition> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.STRING.fieldOf("id").forGetter(BossCondition::id),
            ResourceLocation.CODEC.fieldOf("entity").forGetter(KillCondition::targetType),
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf("required_count").forGetter(BossCondition::requiredCount),
            KillFilters.CODEC.optionalFieldOf("filters", KillFilters.NONE).forGetter(KillCondition::filters)
    ).apply(inst, KillCondition::new));

    private final ResourceLocation targetType;
    private final KillFilters filters;
    public KillFilters filters() { return filters; }

    public KillCondition(String id, ResourceLocation targetType, int requiredCount, KillFilters filters) {
        super(id, requiredCount);
        this.targetType = targetType;
        this.filters = filters;
    }

    public ResourceLocation targetType() {
        return targetType;
    }

    /** Whether the given killed entity type id counts towards this condition. */
    public boolean matches(ResourceLocation killedEntityId) {
        return targetType.equals(killedEntityId);
    }

    @Override
    public ResourceLocation type() {
        return TYPE;
    }
}

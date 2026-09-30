package com.alex.bossprogression.condition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import java.util.Optional;

/**
 * Condition satisfied by opening a number of <em>distinct</em> storage containers (MVP7). The required
 * count is reached by first-time loots only: reopening the same chest never counts again (the set of
 * already-counted locations is tracked on the player's {@link com.alex.bossprogression.boss.BossProgressData}),
 * which stops a single chest being farmed. Matching/counting lives in the chest event handler; this
 * class only carries the required count.
 */
public final class ChestLootedCondition extends BossCondition {
    public static final ResourceLocation TYPE =
            ResourceLocation.fromNamespaceAndPath("bossprogression", "loot_chest");

    /** JSON shape: {@code {"id":..., "required_count":...}} (the {@code type} key is consumed by dispatch). */
    public static final MapCodec<ChestLootedCondition> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.STRING.fieldOf("id").forGetter(BossCondition::id),
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf("required_count").forGetter(BossCondition::requiredCount),
            ResourceLocation.CODEC.optionalFieldOf("loot_table").forGetter(ChestLootedCondition::lootTable)
    ).apply(inst, ChestLootedCondition::new));

    private final Optional<ResourceLocation> lootTable;
    public Optional<ResourceLocation> lootTable() { return lootTable; }
    public ChestLootedCondition(String id, int requiredCount, Optional<ResourceLocation> lootTable) {
        super(id, requiredCount);
        this.lootTable = lootTable;
    }

    @Override
    public ResourceLocation type() {
        return TYPE;
    }
}

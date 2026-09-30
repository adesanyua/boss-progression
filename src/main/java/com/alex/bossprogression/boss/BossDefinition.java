package com.alex.bossprogression.boss;

import com.alex.bossprogression.condition.BossCondition;
import com.alex.bossprogression.dungeon.DungeonConfig;
import com.alex.bossprogression.reward.BossReward;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Static description of a boss encounter: what entity is the boss, which conditions unlock it,
 * where/how its dungeon is generated and whether it can be repeated.
 *
 * <p>In MVP1 a single definition is hardcoded in {@link BossRegistry}. Stage 3/MVP6 replace this
 * with a data-driven JSON codec loader; the shape of this record is deliberately the same as the
 * target JSON schema so the loader can populate it without changing consumers.
 *
 * @param id          boss id, e.g. {@code bossprogression:evoker}
 * @param displayName name shown in the journal / invitation
 * @param bossEntity  entity type id of the boss; may belong to another mod. If it is missing at
 *                    runtime the definition is marked unavailable instead of crashing.
 * @param conditions  unlock conditions; the encounter unlocks only when all are satisfied
 * @param dungeon     where/how to generate the boss dungeon once unlocked
 * @param repeatable  whether the encounter can be regenerated for the same player
 */
public record BossDefinition(
        ResourceLocation id,
        Component displayName,
        ResourceLocation bossEntity,
        List<BossCondition> conditions,
        DungeonConfig dungeon,
        boolean repeatable,
        boolean bossGlowing,
        List<BossReward> rewards,
        BossMobConfig mob) {

    /**
     * JSON schema codec, aligned with the on-disk {@code data/<ns>/bosses/*.json} format.
     * {@code display_name} is a plain string (wrapped in {@link Component#literal}); {@code dungeon}
     * is optional so a boss may be defined without a generated arena.
     */
    public static final Codec<BossDefinition> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(BossDefinition::id),
            Codec.STRING.fieldOf("display_name").forGetter(d -> d.displayName().getString()),
            ResourceLocation.CODEC.fieldOf("boss_entity").forGetter(BossDefinition::bossEntity),
            BossCondition.CODEC.listOf().fieldOf("conditions").forGetter(BossDefinition::conditions),
            DungeonConfig.CODEC.optionalFieldOf("dungeon").forGetter(d -> Optional.ofNullable(d.dungeon())),
            Codec.BOOL.optionalFieldOf("repeatable", false).forGetter(BossDefinition::repeatable),
            Codec.BOOL.optionalFieldOf("boss_glowing", false).forGetter(BossDefinition::bossGlowing),
            BossReward.CODEC.listOf().optionalFieldOf("rewards", List.of()).forGetter(BossDefinition::rewards),
            BossMobConfig.CODEC.optionalFieldOf("mob", BossMobConfig.DEFAULT).forGetter(BossDefinition::mob)
    ).apply(inst, (id, name, bossEntity, conditions, dungeon, repeatable, glowing, rewards, mob) ->
            new BossDefinition(id, Component.literal(name), bossEntity, conditions, dungeon.orElse(null), repeatable, glowing, rewards, mob)));

    public BossCondition condition(String conditionId) {
        for (BossCondition c : conditions) {
            if (c.id().equals(conditionId)) {
                return c;
            }
        }
        return null;
    }

    /** True when every condition has reached its required count in the given state. */
    public boolean allConditionsMet(BossState state) {
        for (BossCondition c : conditions) {
            if (state.progress(c.id()) < c.requiredCount()) {
                return false;
            }
        }
        return true;
    }
}

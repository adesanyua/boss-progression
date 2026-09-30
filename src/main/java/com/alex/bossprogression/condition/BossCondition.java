package com.alex.bossprogression.condition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;

/**
 * Base type for a single unlock requirement of a {@link com.alex.bossprogression.boss.BossDefinition}.
 * <p>Every condition has a stable string id (unique within its boss) that is used as the progress key,
 * and a required count that must be reached before the boss can be unlocked.
 * <p>Conditions are pure data; all mutation of player progress goes through
 * {@link com.alex.bossprogression.boss.BossProgressManager}.
 */
public abstract class BossCondition {
    /**
     * Polymorphic codec keyed by the {@code "type"} discriminator. Concrete types are supplied by
     * {@link ConditionRegistry}; an unknown discriminator becomes a decode error (handled gracefully
     * by the boss loader) rather than a crash.
     */
    public static final Codec<BossCondition> CODEC = buildCodec();

    private static Codec<BossCondition> buildCodec() {
        // The type-keyed dispatch resolves a MapCodec per discriminator; explicit witness + exact
        // function types pin the type variable, which inference cannot derive from lambdas alone.
        Function<BossCondition, String> typeGetter = condition -> condition.type().toString();
        Function<String, MapCodec<? extends BossCondition>> codecGetter = ConditionRegistry::byDiscriminator;
        return Codec.STRING.<BossCondition>dispatch("type", typeGetter, codecGetter);
    }

    private final String id;
    private final int requiredCount;

    protected BossCondition(String id, int requiredCount) {
        this.id = id;
        this.requiredCount = requiredCount;
    }

    /** Stable, per-boss unique key used to store progress. */
    public String id() {
        return id;
    }

    /** How much progress is needed to satisfy this condition. */
    public int requiredCount() {
        return requiredCount;
    }

    /** The discriminator used by the (future) JSON codec / {@link ConditionRegistry}. */
    public abstract ResourceLocation type();
}
